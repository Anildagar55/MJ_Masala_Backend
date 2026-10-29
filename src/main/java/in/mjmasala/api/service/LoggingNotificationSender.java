package in.mjmasala.api.service;

import in.mjmasala.api.domain.OtpChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Writes the code to the log instead of delivering it.
 *
 * This is the fallback {@link in.mjmasala.api.service.notify.RoutingNotificationSender}
 * uses when a channel has no provider configured, which is why a fresh clone can still
 * exercise the whole sign-up flow.
 */
@Service
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void sendOtp(OtpChannel channel, String destination, String code, String purpose) {
        log.warn("""
                [DEV ONLY] No {} provider is configured, so this OTP was not delivered.
                           destination={} purpose={} code={}
                           Wire a NotificationSender to actually send it.""",
                channel, mask(destination), purpose, code);
    }

    /** Never log a full address — the log is not a safe place for personal data. */
    public static String mask(String value) {
        if (value == null || value.isBlank()) return "?";
        if (value.contains("@")) {
            int at = value.indexOf('@');
            String local = value.substring(0, at);
            return (local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "***")
                    + value.substring(at);
        }
        return value.length() <= 4 ? "****" : "******" + value.substring(value.length() - 4);
    }
}
