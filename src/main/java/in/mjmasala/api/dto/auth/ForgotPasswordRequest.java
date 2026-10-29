package in.mjmasala.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** Accepts either an email address or a mobile number. */
public record ForgotPasswordRequest(
        @NotBlank(message = "Enter your email or mobile number") String emailOrPhone) {
}
