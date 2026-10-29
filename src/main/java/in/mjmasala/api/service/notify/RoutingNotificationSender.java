package in.mjmasala.api.service.notify;

import in.mjmasala.api.domain.OtpChannel;
import in.mjmasala.api.service.LoggingNotificationSender;
import in.mjmasala.api.service.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * The sender the rest of the application gets.
 *
 * It picks a channel and, when that channel is not configured, falls back to writing the
 * code to the log. Two consequences worth stating plainly:
 *
 * - **A fresh clone still works.** No mail host, no SMS key, and the sign-up flow is
 *   exercisable end to end because the code is in the log.
 * - **A send failure never breaks sign-up.** Providers go down and mail hosts reject
 *   addresses. A failure here is logged and swallowed; the user sees "we sent you a code"
 *   and can ask for another. Throwing would turn a flaky provider into a broken sign-up.
 *
 * It is {@code @Primary} so the router — not the logger or either provider — is what gets
 * injected wherever a {@link NotificationSender} is asked for.
 */
@Primary
@Service
public class RoutingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(RoutingNotificationSender.class);

    private final EmailNotificationSender email;
    private final SmsNotificationSender sms;
    private final LoggingNotificationSender fallback;

    public RoutingNotificationSender(EmailNotificationSender email, SmsNotificationSender sms,
                                     LoggingNotificationSender fallback) {
        this.email = email;
        this.sms = sms;
        this.fallback = fallback;
    }

    @Override
    public void sendOtp(OtpChannel channel, String destination, String code, String purpose) {
        if (destination == null || destination.isBlank()) {
            return;
        }

        try {
            if (channel == OtpChannel.EMAIL && email.isAvailable()) {
                email.send(destination, code, purpose);
                return;
            }
            if (channel == OtpChannel.SMS && sms.isAvailable()) {
                sms.send(destination, code, purpose);
                return;
            }
        } catch (Exception ex) {
            // never let a provider outage fail a sign-up — the user can ask for another code
            log.error("Could not deliver a {} code over {}. The code is still valid.",
                    purpose, channel, ex);
        }

        fallback.sendOtp(channel, destination, code, purpose);
    }

    /** Surfaced at startup so it is obvious whether codes are actually going anywhere. */
    public String describeDelivery() {
        return "email=" + (email.isAvailable() ? "on" : "off (logging)")
                + " sms=" + (sms.isAvailable() ? "on" : "off (logging)");
    }
}
