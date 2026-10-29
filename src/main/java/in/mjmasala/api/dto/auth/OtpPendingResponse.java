package in.mjmasala.api.dto.auth;

/**
 * What the client needs to show the "we sent you a code" screen.
 *
 * {@code pendingToken} is a short-lived token naming the account being verified — the
 * client sends it back with the code. Using a token rather than an email address means the
 * verify endpoint never has to look an account up by identifier, so it cannot be used to
 * probe which addresses are registered.
 *
 * {@code devCode} is populated only when {@code app.otp.expose-code} is on. It is null in
 * every real environment.
 */
public record OtpPendingResponse(
        String pendingToken,
        long expiresInSeconds,
        int codeLength,
        String email,
        String phone,
        String devCode) {
}
