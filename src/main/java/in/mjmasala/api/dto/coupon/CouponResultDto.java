package in.mjmasala.api.dto.coupon;

import java.math.BigDecimal;

public record CouponResultDto(
        boolean valid,
        String code,
        String message,
        BigDecimal discount,
        String description) {
}
