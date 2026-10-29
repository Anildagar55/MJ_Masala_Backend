package in.mjmasala.api.web;

import in.mjmasala.api.dto.auth.AuthResponse;
import in.mjmasala.api.dto.auth.ForgotPasswordRequest;
import in.mjmasala.api.dto.auth.OtpPendingResponse;
import in.mjmasala.api.dto.auth.RegisterRequest;
import in.mjmasala.api.dto.auth.ResendOtpRequest;
import in.mjmasala.api.dto.auth.ResetPasswordRequest;
import in.mjmasala.api.dto.auth.VerifyOtpRequest;
import in.mjmasala.api.dto.auth.LoginRequest;
import in.mjmasala.api.dto.auth.RefreshRequest;
import in.mjmasala.api.dto.auth.UserDto;
import in.mjmasala.api.security.SecurityUser;
import in.mjmasala.api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Register, sign in, refresh and read the current account")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @Operation(summary = "Create an account and send a verification code",
            description = "The account is created switched off. It only becomes usable once "
                    + "the code comes back to /auth/verify — no tokens are issued here.")
    public ResponseEntity<OtpPendingResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(request));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify the code sent at sign-up and sign in")
    public AuthResponse verify(@Valid @RequestBody VerifyOtpRequest request) {
        return auth.verify(request);
    }

    @PostMapping("/resend")
    @Operation(summary = "Send a fresh sign-up code")
    public OtpPendingResponse resend(@Valid @RequestBody ResendOtpRequest request) {
        return auth.resend(request.pendingToken());
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Start a password reset",
            description = "Always reports success. The response carries a pendingToken only "
                    + "when the account exists, so this cannot be used to discover which "
                    + "addresses are registered.")
    public OtpPendingResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return auth.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Finish a password reset and sign in",
            description = "Also invalidates refresh tokens issued before the change.")
    public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return auth.resetPassword(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange email + password for a token pair")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Trade a refresh token for a new pair")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in account")
    public UserDto me(@AuthenticationPrincipal SecurityUser principal) {
        return auth.me(principal.getId());
    }
}
