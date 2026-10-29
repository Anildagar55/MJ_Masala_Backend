package in.mjmasala.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email(message = "That does not look like an email") String email,
        @NotBlank(message = "Enter your password") String password) {
}
