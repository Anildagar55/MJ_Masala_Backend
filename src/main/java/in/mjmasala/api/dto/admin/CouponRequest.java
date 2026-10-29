package in.mjmasala.api.dto.admin;

import in.mjmasala.api.domain.CouponType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponRequest(
        @NotBlank(message = "Give the code a name") String code,
        @NotNull CouponType type,
        @NotNull @DecimalMin(value = "0.0", inclusive = false, message = "Value must be greater than zero")
        BigDecimal value,
        BigDecimal minSubtotal,
        BigDecimal maxDiscount,
        Boolean active,
        Instant expiresAt,
        Integer usageLimit,
        String description) {
}
