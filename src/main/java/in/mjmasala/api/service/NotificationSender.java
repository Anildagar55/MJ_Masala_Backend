package in.mjmasala.api.service;

import in.mjmasala.api.domain.OtpChannel;

/**
 * The seam where a real email or SMS provider plugs in.
 *
 * This build ships a logging implementation, so nothing is actually delivered. That is the
 * one honest gap in the sign-up flow: the code is generated, hashed, stored and verified
 * exactly as it would be in production — it just has no way out of the process yet.
 *
 * To wire a provider, implement this interface and mark the bean {@code @Primary}. Nothing
 * else changes: the OTP lifecycle does not know how the message travels.
 */
public interface NotificationSender {

    /**
     * Delivers a one-time code.
     *
     * Implementations must not throw for a bad address — a failed send should be logged and
     * swallowed, because telling the caller "that email does not exist" is exactly the
     * account-enumeration leak the rest of this flow avoids.
     */
    void sendOtp(OtpChannel channel, String destination, String code, String purpose);
}
