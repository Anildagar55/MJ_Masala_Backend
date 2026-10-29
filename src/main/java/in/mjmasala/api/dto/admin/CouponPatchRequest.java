package in.mjmasala.api.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;

/** Every field optional — only what is sent is changed. */
public record CouponPatchRequest(
        BigDecimal value,
        BigDecimal minSubtotal,
        BigDecimal maxDiscount,
        Boolean active,
        Instant expiresAt,
        Integer usageLimit,
        String description) {
}
