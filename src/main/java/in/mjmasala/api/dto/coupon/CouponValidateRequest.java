package in.mjmasala.api.dto.coupon;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code subtotal} is optional: the cart sends it so the response can show the exact
 * discount, but the server recomputes it from the real cart at order time regardless.
 */
public record CouponValidateRequest(
        @NotBlank(message = "Enter a coupon code") String code,
        java.math.BigDecimal subtotal) {
}
