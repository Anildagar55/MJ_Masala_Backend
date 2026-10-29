package in.mjmasala.api.service;

import in.mjmasala.api.config.AppProperties;
import in.mjmasala.api.domain.OtpChannel;
import in.mjmasala.api.domain.OtpPurpose;
import in.mjmasala.api.domain.OtpToken;
import in.mjmasala.api.domain.User;
import in.mjmasala.api.dto.auth.OtpPendingResponse;
import in.mjmasala.api.exception.BadRequestException;
import in.mjmasala.api.exception.TooManyRequestsException;
import in.mjmasala.api.repository.OtpTokenRepository;
import in.mjmasala.api.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Issues and checks one-time codes.
 *
 * The security properties, because they are the whole point of an OTP:
 *
 * - the code is generated from a {@link SecureRandom}, not {@code Math.random}
 * - only a BCrypt hash is stored, so the database never holds a usable code
 * - a code expires, is single-use, and stops being accepted after a handful of wrong tries
 * - a resend is rate-limited, so the endpoint cannot be used to spam somebody's phone
 * - a code issued for one purpose is refused for another
 *
 * What it cannot do here is deliver the message: there is no mail or SMS provider in this
 * build. The code is logged instead, and returned in the response only when
 * {@code app.otp.expose-code} is on.
 */
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final NotificationSender sender;
    private final AppProperties.Otp config;

    public OtpService(OtpTokenRepository tokens, PasswordEncoder encoder, JwtService jwt,
                      NotificationSender sender, AppProperties properties) {
        this.tokens = tokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.sender = sender;
        this.config = properties.otp();
    }

    /** How long a pending token stays valid — the same as the code it accompanies. */
    public Duration pendingTokenTtl() {
        return config.ttl();
    }

    /**
     * Mints a fresh code, invalidating any earlier one, and sends it to both the email and
     * the mobile number.
     */
    @Transactional
    public OtpPendingResponse issue(User user, OtpPurpose purpose) {
        Instant now = Instant.now();

        // rate limit: a cooldown between sends and a ceiling per hour
        OtpToken previous = tokens
                .findFirstByUserIdAndPurposeAndConsumedFalseOrderByIdDesc(user.getId(), purpose.name())
                .orElse(null);

        if (previous != null) {
            Instant nextAllowed = previous.getSentAt().plus(config.resendCooldown());
            if (nextAllowed.isAfter(now)) {
                long wait = Duration.between(now, nextAllowed).toSeconds() + 1;
                throw new TooManyRequestsException(
                        "Please wait " + wait + "s before asking for another code");
            }
        }
        long recent = tokens.countByUserIdAndSentAtAfter(user.getId(), now.minus(Duration.ofHours(1)));
        if (recent >= config.maxPerHour()) {
            throw new TooManyRequestsException(
                    "Too many codes requested. Try again in an hour.");
        }

        // one live code per purpose — anything older is dead
        for (OtpToken old : tokens.findByUserIdAndPurposeAndConsumedFalse(user.getId(), purpose.name())) {
            old.setConsumed(true);
        }

        String code = generateCode();
        OtpToken token = new OtpToken();
        token.setUser(user);
        token.setPurpose(purpose.name());
        token.setCodeHash(encoder.encode(code));
        token.setExpiresAt(now.plus(config.ttl()));
        token.setSentAt(now);
        tokens.save(token);

        // one code, two channels: the account is not verified until it comes back
        if (user.getEmail() != null) {
            sender.sendOtp(OtpChannel.EMAIL, user.getEmail(), code, purpose.name());
        }
        if (user.getPhone() != null) {
            sender.sendOtp(OtpChannel.SMS, user.getPhone(), code, purpose.name());
        }

        return new OtpPendingResponse(
                jwt.issuePendingToken(user, purpose.name(), config.ttl()),
                config.ttl().toSeconds(),
                config.length(),
                LoggingNotificationSender.mask(user.getEmail()),
                LoggingNotificationSender.mask(user.getPhone()),
                config.exposeCode() ? code : null);
    }

    /**
     * Checks a code and consumes it on success.
     *
     * @throws BadRequestException with a message safe to show the user
     */
    @Transactional
    public void verify(User user, OtpPurpose purpose, String code) {
        Instant now = Instant.now();

        OtpToken token = tokens
                .findFirstByUserIdAndPurposeAndConsumedFalseOrderByIdDesc(user.getId(), purpose.name())
                .orElseThrow(() -> new BadRequestException(
                        "We have no code outstanding for this account. Ask for a new one."));

        if (!token.isUsable(now)) {
            throw new BadRequestException("That code has expired. Ask for a new one.");
        }
        if (token.getAttempts() >= config.maxAttempts()) {
            token.setConsumed(true);
            throw new BadRequestException("Too many wrong attempts. Ask for a new code.");
        }

        token.setAttempts(token.getAttempts() + 1);

        if (!encoder.matches(code, token.getCodeHash())) {
            // a wrong guess burns one of the attempts; the message does not say how many are left
            throw new BadRequestException("That code is not correct.");
        }

        token.setConsumed(true);
    }

    /** Reads the account a pending token names, refusing one minted for another purpose. */
    public Long userIdOf(String pendingToken, OtpPurpose expected) {
        Claims claims;
        try {
            claims = jwt.parse(pendingToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BadRequestException("This verification link has expired. Start again.");
        }
        if (!jwt.isPendingToken(claims)) {
            throw new BadRequestException("That token cannot be used here.");
        }
        if (!expected.name().equals(jwt.purpose(claims))) {
            throw new BadRequestException("That code was issued for a different step.");
        }
        return jwt.userId(claims);
    }

    /** A numeric code, uniformly distributed over the configured length. */
    private String generateCode() {
        StringBuilder sb = new StringBuilder(config.length());
        for (int i = 0; i < config.length(); i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
