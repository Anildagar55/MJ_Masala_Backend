package in.mjmasala.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sign-up. The mobile number is required, because the code that verifies the account is
 * sent to it as well as to the email.
 */
public record RegisterRequest(

        @NotBlank(message = "Tell us your name")
        @Size(max = 120)
        String name,

        @NotBlank(message = "An email is required")
        @Email(message = "That does not look like an email")
        @Size(max = 190)
        String email,

        @NotBlank(message = "A mobile number is required")
        @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a 10-digit mobile number")
        String phone,

        @NotBlank(message = "Choose a password")
        @Size(min = 8, max = 72, message = "Use at least 8 characters")
        String password) {
}
