package in.mjmasala.api.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank(message = "Missing verification token") String pendingToken,
        @NotBlank(message = "Enter the code we sent you")
        @Pattern(regexp = "^[0-9]{4,8}$", message = "The code is a number")
        String code) {
}
