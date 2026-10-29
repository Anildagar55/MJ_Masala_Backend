package in.mjmasala.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Missing reset token") String pendingToken,
        @NotBlank(message = "Enter the code we sent you")
        @Pattern(regexp = "^[0-9]{4,8}$", message = "The code is a number")
        String code,
        @NotBlank(message = "Choose a new password")
        @Size(min = 8, max = 72, message = "Use at least 8 characters")
        String newPassword) {
}
