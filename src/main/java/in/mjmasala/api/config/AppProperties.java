package in.mjmasala.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything under {@code app:} in application.yml, typed.
 *
 * Shipping rules and the JWT secret are configuration, not code, so the client can change
 * a fee or rotate a secret without a rebuild.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Shipping shipping, Seed seed, Otp otp,
                            Notify notifications, Media media) {

    public record Jwt(String secret, String issuer, java.time.Duration accessTokenTtl,
                      java.time.Duration refreshTokenTtl) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    /** Free delivery above {@code freeAbove}; otherwise {@code standardFee}. */
    public record Shipping(BigDecimal freeAbove, BigDecimal standardFee, BigDecimal codFee) {
    }

    public record Seed(boolean enabled) {
    }

    /**
     * One-time-code policy.
     *
     * {@code exposeCode} returns the code in the API response and exists only so the flow
     * can be exercised end to end without a mail or SMS provider. It must be false
     * anywhere real — the startup check refuses to boot with it on outside a dev profile.
     */
    public record Otp(java.time.Duration ttl, int length, int maxAttempts,
                      java.time.Duration resendCooldown, int maxPerHour, boolean exposeCode) {
    }

    /**
     * How one-time codes actually reach people.
     *
     * Both channels are off by default, so a fresh clone boots and logs the code instead
     * of trying to reach a mail host that is not there. Turning one on is configuration.
     */
    /**
     * Where uploaded product photography is kept.
     *
     * {@code dir} is a filesystem path for the shipped implementation. The storage is
     * behind an interface, so moving to S3 means adding one class, not touching callers.
     */
    public record Media(String dir, String publicPath, long maxUploadBytes, int maxDimension) {
    }

    public record Notify(Email email, Sms sms) {

        /** {@code host} empty means "log it instead". */
        public record Email(boolean enabled, String from, String fromName) {
        }

        /**
         * SMS goes out over an HTTP API, so this is the provider's details.
         *
         * {@code provider} picks the request shape — MSG91 and Fast2SMS are built in
         * because they are the common ones in India; {@code generic} posts a plain JSON
         * body and is the one to reach for with anyone else.
         */
        public record Sms(boolean enabled, String provider, String url, String authKey,
                          String senderId, String templateId, String route) {
        }
    }
}
