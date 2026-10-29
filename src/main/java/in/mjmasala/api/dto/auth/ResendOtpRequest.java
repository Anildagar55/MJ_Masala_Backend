package in.mjmasala.api.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record ResendOtpRequest(@NotBlank(message = "Missing verification token") String pendingToken) {
}
