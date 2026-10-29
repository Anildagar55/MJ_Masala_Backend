package in.mjmasala.api.service.notify;

import in.mjmasala.api.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends the code by email over SMTP.
 *
 * {@link JavaMailSender} is only auto-configured when {@code spring.mail.host} is set, so
 * it is injected as a provider rather than required — the application still boots on a
 * fresh clone with no mail host, and the router falls back to logging.
 *
 * Set the standard Spring mail properties (or the {@code MAIL_*} variables) plus
 * {@code app.notify.email.enabled=true} and this starts delivering.
 */
@Component
public class EmailNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);

    private final ObjectProvider<JavaMailSender> mailProvider;
    private final AppProperties.Notify.Email config;

    public EmailNotificationSender(ObjectProvider<JavaMailSender> mailProvider,
                                   AppProperties properties) {
        this.mailProvider = mailProvider;
        this.config = properties.notifications().email();
    }

    /** True when a mail host is configured and the channel is switched on. */
    public boolean isAvailable() {
        return config.enabled() && mailProvider.getIfAvailable() != null
                && config.from() != null && !config.from().isBlank();
    }

    public void send(String to, String code, String purpose) {
        JavaMailSender mail = mailProvider.getIfAvailable();
        if (mail == null) {
            throw new IllegalStateException("No mail host is configured (spring.mail.host)");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(config.fromName() == null || config.fromName().isBlank()
                ? config.from()
                : "%s <%s>".formatted(config.fromName(), config.from()));
        message.setTo(to);
        message.setSubject("REGISTER".equals(purpose)
                ? "Your MJ MASALA verification code"
                : "Reset your MJ MASALA password");
        message.setText(body(code, purpose));

        mail.send(message);
        log.info("Sent a {} code to {}", purpose, mask(to));
    }

    private String body(String code, String purpose) {
        String opening = "REGISTER".equals(purpose)
                ? "Welcome to MJ MASALA. Use this code to verify your account:"
                : "Use this code to set a new password for your MJ MASALA account:";

        return """
                %s

                    %s

                The code is good for 10 minutes and can be used once. If you did not ask for
                it, you can ignore this message — nothing has changed on your account.

                Never share this code with anyone. MJ MASALA will never ask you for it.

                — MJ MASALA, authentic Indian spices
                """.formatted(opening, code);
    }

    private static String mask(String value) {
        int at = value.indexOf('@');
        return at <= 2 ? "***" + value.substring(Math.max(at, 0)) : value.substring(0, 2) + "***" + value.substring(at);
    }
}
