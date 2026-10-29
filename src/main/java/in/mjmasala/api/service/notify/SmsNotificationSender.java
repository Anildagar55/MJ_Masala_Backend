package in.mjmasala.api.service.notify;

import in.mjmasala.api.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends the code by SMS through a provider's HTTP API.
 *
 * There is no single standard, so the request shape is chosen by {@code app.notify.sms.provider}:
 *
 * <ul>
 *   <li>{@code msg91} — MSG91's v5 flow API: an {@code authkey} header and a JSON body
 *       naming the flow, the sender and the recipient.</li>
 *   <li>{@code fast2sms} — Fast2SMS's bulk API: an {@code authorization} header with the
 *       message and the number.</li>
 *   <li>{@code generic} — a plain JSON body with {@code sender}, {@code mobile} and
 *       {@code message}. The one to reach for with anybody else.</li>
 * </ul>
 *
 * To add a provider, add a case to {@link #requestFor} — nothing outside this class knows
 * how the message travels.
 *
 * DLT: Indian carriers require transactional SMS to go out against a registered template,
 * which is what {@code template-id} is. Until one is registered, expect the provider to
 * reject the send.
 */
@Component
public class SmsNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationSender.class);

    private final AppProperties.Notify.Sms config;
    private final RestClient http;

    public SmsNotificationSender(AppProperties properties, RestClient.Builder builder) {
        this.config = properties.notifications().sms();
        this.http = builder.build();
    }

    public boolean isAvailable() {
        return config.enabled()
                && config.url() != null && !config.url().isBlank()
                && config.authKey() != null && !config.authKey().isBlank();
    }

    public void send(String mobile, String code, String purpose) {
        if (!isAvailable()) {
            throw new IllegalStateException("No SMS provider is configured");
        }

        String message = "REGISTER".equals(purpose)
                ? code + " is your MJ MASALA verification code. Valid for 10 minutes. Do not share it."
                : code + " is your MJ MASALA password reset code. Valid for 10 minutes. Do not share it.";

        Request request = requestFor(mobile, message, code);

        http.post()
                .uri(config.url())
                .header(request.headerName, request.headerValue)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(request.body)
                .retrieve()
                .toBodilessEntity();

        log.info("Sent a {} code to {}", purpose, mask(mobile));
    }

    private record Request(String headerName, String headerValue, Map<String, Object> body) {
    }

    private Request requestFor(String mobile, String message, String code) {
        String provider = config.provider() == null ? "generic" : config.provider().toLowerCase();

        Map<String, Object> body = new LinkedHashMap<>();
        switch (provider) {
            case "msg91" -> {
                body.put("flow_id", config.templateId());
                body.put("sender", config.senderId());
                body.put("mobiles", normalise(mobile));
                // MSG91's flow templates take the code as VAR1
                body.put("VAR1", code);
                return new Request("authkey", config.authKey(), body);
            }
            case "fast2sms" -> {
                body.put("route", config.route() == null || config.route().isBlank() ? "q" : config.route());
                body.put("sender_id", config.senderId());
                body.put("message", message);
                body.put("numbers", stripCountryCode(mobile));
                return new Request("authorization", config.authKey(), body);
            }
            default -> {
                body.put("sender", config.senderId());
                body.put("templateId", config.templateId());
                body.put("mobile", normalise(mobile));
                body.put("message", message);
                body.put("code", code);
                return new Request("Authorization", "Bearer " + config.authKey(), body);
            }
        }
    }

    /** Providers expect a country code; the app stores a bare 10-digit number. */
    private static String normalise(String mobile) {
        return mobile.startsWith("+") ? mobile : "91" + mobile;
    }

    private static String stripCountryCode(String mobile) {
        String digits = mobile.replaceAll("\\D", "");
        return digits.length() > 10 ? digits.substring(digits.length() - 10) : digits;
    }

    private static String mask(String value) {
        return value.length() <= 4 ? "****" : "******" + value.substring(value.length() - 4);
    }
}
