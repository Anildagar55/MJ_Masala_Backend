package in.mjmasala.api.service;

import in.mjmasala.api.config.AppProperties;
import in.mjmasala.api.domain.OtpPurpose;
import in.mjmasala.api.domain.Role;
import in.mjmasala.api.domain.User;
import in.mjmasala.api.dto.auth.AuthResponse;
import in.mjmasala.api.dto.auth.ForgotPasswordRequest;
import in.mjmasala.api.dto.auth.LoginRequest;
import in.mjmasala.api.dto.auth.OtpPendingResponse;
import in.mjmasala.api.dto.auth.RegisterRequest;
import in.mjmasala.api.dto.auth.ResetPasswordRequest;
import in.mjmasala.api.dto.auth.UserDto;
import in.mjmasala.api.dto.auth.VerifyOtpRequest;
import in.mjmasala.api.exception.BadRequestException;
import in.mjmasala.api.exception.ConflictException;
import in.mjmasala.api.exception.NotFoundException;
import in.mjmasala.api.repository.UserRepository;
import in.mjmasala.api.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

/**
 * Accounts: signing up, proving the contact details, signing in, and getting back in after
 * a forgotten password.
 *
 * The shape of the flow is:
 *
 * <pre>
 *   register  → account created disabled + OTP sent → verify  → tokens
 *   login     → tokens, or 403 if the account was never verified
 *   forgot    → OTP sent (silently, if the account exists) → reset → tokens
 * </pre>
 *
 * Two things are deliberate and worth keeping:
 *
 * - **An account is unusable until the code comes back.** Registering does not hand out
 *   tokens; only {@link #verify} does. Otherwise the OTP would be decoration.
 * - **Neither register nor forgot reveals whether an address is registered** beyond what
 *   the user already knows — register has to say "already registered" because the person
 *   is standing at a sign-up form, but forgot never confirms or denies.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuthenticationManager authenticationManager;
    private final OtpService otp;
    private final AppProperties.Otp otpConfig;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                       AuthenticationManager authenticationManager, OtpService otp,
                       AppProperties properties) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.authenticationManager = authenticationManager;
        this.otp = otp;
        this.otpConfig = properties.otp();
    }

    /* ------------------------------ sign up ------------------------------ */

    /**
     * Creates the account switched off and sends a code. No tokens until it comes back.
     */
    @Transactional
    public OtpPendingResponse register(RegisterRequest request) {
        String email = normaliseEmail(request.email());
        String phone = request.phone().trim();

        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("That email is already registered — try signing in");
        }
        if (users.existsByPhone(phone)) {
            throw new ConflictException("That mobile number is already registered — try signing in");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(encoder.encode(request.password()));
        user.setRole(Role.USER);
        // off until the OTP is verified — login refuses an unverified account
        user.setEnabled(false);
        user.setPasswordChangedAt(nowToTheSecond());

        return otp.issue(users.save(user), OtpPurpose.REGISTER);
    }

    /** Checks the code and, on success, switches the account on and signs the user in. */
    @Transactional
    public AuthResponse verify(VerifyOtpRequest request) {
        Long userId = otp.userIdOf(request.pendingToken(), OtpPurpose.REGISTER);
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        if (user.isEnabled()) {
            // already verified — most likely a double submit; signing in is the kind answer
            return tokens(user);
        }

        otp.verify(user, OtpPurpose.REGISTER, request.code());
        user.setEnabled(true);
        users.save(user);

        return tokens(user);
    }

    /** Sends a fresh code for a sign-up that has not been verified yet. */
    @Transactional
    public OtpPendingResponse resend(String pendingToken) {
        Long userId = otp.userIdOf(pendingToken, OtpPurpose.REGISTER);
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        if (user.isEnabled()) {
            throw new BadRequestException("That account is already verified — please sign in");
        }
        return otp.issue(user, OtpPurpose.REGISTER);
    }

    /* ------------------------------- sign in ------------------------------ */

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normaliseEmail(request.email());

        // Delegates to the DaoAuthenticationProvider: constant-time password check, and the
        // same failure for "no such user" and "wrong password".
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));

        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        if (!user.isEnabled()) {
            // the password was right, so saying so is not a leak — and the client needs to
            // know to send the user to the code screen rather than the sign-up form
            throw new BadRequestException(
                    "This account is not verified yet. Enter the code we sent you, or ask for a new one.");
        }

        return tokens(user);
    }

    /**
     * A refresh token is only good for getting a new access token — it cannot call an API,
     * because the filter checks the token type.
     */
    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwt.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BadRequestException("That session has expired — please sign in again");
        }
        if (!JwtService.TYPE_REFRESH.equals(claims.get(JwtService.CLAIM_TYPE, String.class))) {
            throw new BadRequestException("That is not a refresh token");
        }

        User user = users.findById(jwt.userId(claims))
                .orElseThrow(() -> new NotFoundException("Account not found"));

        // A password reset ends the sessions that existed before it. Without this, a
        // refresh token stolen before the reset would keep working after it — which is
        // precisely when the user is trying to lock somebody out.
        if (jwt.tokenVersion(claims) != user.getTokenVersion()) {
            throw new BadRequestException("Your password was changed — please sign in again");
        }

        if (!user.isEnabled()) {
            throw new BadRequestException("This account is not verified yet");
        }

        return tokens(user);
    }

    /* --------------------------- forgot password --------------------------- */

    /**
     * Starts a reset. Always reports success.
     *
     * Whether the address is registered is not disclosed: a response that differs for a
     * known and an unknown address turns this endpoint into a way to find out who shops
     * here. When the account is not found, the response carries no pending token and the
     * client shows the same "if that account exists, we have sent a code" message either
     * way.
     */
    @Transactional
    public OtpPendingResponse forgotPassword(ForgotPasswordRequest request) {
        String identifier = request.emailOrPhone().trim();
        Optional<User> found = identifier.contains("@")
                ? users.findByEmailIgnoreCase(normaliseEmail(identifier))
                : users.findByPhone(identifier);

        if (found.isEmpty()) {
            return new OtpPendingResponse(null, otp.pendingTokenTtl().toSeconds(),
                    otpConfig.length(), null, null, null);
        }

        User user = found.get();
        if (!user.isEnabled()) {
            // still give them the code path — it is how they finish signing up
            return otp.issue(user, OtpPurpose.REGISTER);
        }
        return otp.issue(user, OtpPurpose.RESET);
    }

    /** Completes a reset: proves the code, sets the password, and signs the user in. */
    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        Long userId = otp.userIdOf(request.pendingToken(), OtpPurpose.RESET);
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("Account not found"));

        otp.verify(user, OtpPurpose.RESET, request.code());

        user.setPasswordHash(encoder.encode(request.newPassword()));
        user.setPasswordChangedAt(nowToTheSecond());
        // every token minted before this line stops working
        user.setTokenVersion(user.getTokenVersion() + 1);
        users.save(user);

        return tokens(user);
    }

    /* -------------------------------- misc -------------------------------- */

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return users.findById(userId)
                .map(AuthService::toDto)
                .orElseThrow(() -> NotFoundException.of("User", userId));
    }

    private AuthResponse tokens(User user) {
        return new AuthResponse(
                jwt.issueAccessToken(user),
                jwt.issueRefreshToken(user),
                "Bearer",
                jwt.accessTokenSeconds(),
                toDto(user));
    }

    public static UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getName(), user.getEmail(),
                user.getPhone(), user.getRole().name());
    }

    /** The stored instant is for humans; session invalidation uses {@code tokenVersion}. */
    private Instant nowToTheSecond() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
