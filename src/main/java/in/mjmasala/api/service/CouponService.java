package in.mjmasala.api.service;

import in.mjmasala.api.domain.Coupon;
import in.mjmasala.api.domain.CouponType;
import in.mjmasala.api.dto.coupon.CouponResultDto;
import in.mjmasala.api.exception.BadRequestException;
import in.mjmasala.api.repository.CouponRepository;
import in.mjmasala.api.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Coupon rules.
 *
 * Every rule is checked server-side at order time. The cart's preview uses the same
 * method, so the two can never disagree — but the preview is never trusted as input.
 */
@Service
public class CouponService {

    private final CouponRepository coupons;

    public CouponService(CouponRepository coupons) {
        this.coupons = coupons;
    }

    /** Preview for the cart. Never throws: an unknown code is just an invalid result. */
    @Transactional(readOnly = true)
    public CouponResultDto validate(String code, BigDecimal subtotal) {
        if (code == null || code.isBlank()) {
            return new CouponResultDto(false, null, "Enter a coupon code", Money.ZERO, null);
        }
        String normalised = code.trim().toUpperCase(Locale.ROOT);
        Optional<Coupon> found = coupons.findByCodeIgnoreCase(normalised);
        if (found.isEmpty()) {
            return new CouponResultDto(false, normalised, "That code is not valid", Money.ZERO, null);
        }
        Coupon coupon = found.get();
        String reason = rejectionReason(coupon, subtotal);
        if (reason != null) {
            return new CouponResultDto(false, normalised, reason, Money.ZERO, coupon.getDescription());
        }
        return new CouponResultDto(true, normalised,
                "Code applied", discountFor(coupon, subtotal), coupon.getDescription());
    }

    /**
     * Used by the order flow, where an unusable code must fail loudly rather than quietly
     * charging full price.
     */
    @Transactional(readOnly = true)
    public Coupon requireUsable(String code, BigDecimal subtotal) {
        String normalised = code.trim().toUpperCase(Locale.ROOT);
        Coupon coupon = coupons.findByCodeIgnoreCase(normalised)
                .orElseThrow(() -> new BadRequestException("Coupon " + normalised + " is not valid"));
        String reason = rejectionReason(coupon, subtotal);
        if (reason != null) {
            throw new BadRequestException(reason);
        }
        return coupon;
    }

    public BigDecimal discountFor(Coupon coupon, BigDecimal subtotal) {
        BigDecimal sub = subtotal == null ? BigDecimal.ZERO : subtotal;
        BigDecimal discount = switch (coupon.getType()) {
            case PERCENT -> sub.multiply(coupon.getValue()).divide(BigDecimal.valueOf(100), 2,
                    java.math.RoundingMode.HALF_UP);
            case FLAT -> coupon.getValue();
        };
        if (coupon.getMaxDiscount() != null && discount.compareTo(coupon.getMaxDiscount()) > 0) {
            discount = coupon.getMaxDiscount();
        }
        if (discount.compareTo(sub) > 0) {
            discount = sub;
        }
        return Money.round(discount);
    }

    /** @return null when the coupon may be used, otherwise the reason it may not */
    private String rejectionReason(Coupon coupon, BigDecimal subtotal) {
        if (!coupon.isActive()) {
            return "That code is no longer active";
        }
        if (coupon.getExpiresAt() != null && coupon.getExpiresAt().isBefore(Instant.now())) {
            return "That code has expired";
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            return "That code has been fully redeemed";
        }
        BigDecimal sub = subtotal == null ? BigDecimal.ZERO : subtotal;
        if (sub.compareTo(coupon.getMinSubtotal()) < 0) {
            return "Add " + in.mjmasala.api.util.Rupees.format(
                    coupon.getMinSubtotal().subtract(sub)) + " more to use this code";
        }
        return null;
    }

    @Transactional
    public void recordRedemption(Coupon coupon) {
        coupon.setUsedCount(coupon.getUsedCount() + 1);
        coupons.save(coupon);
    }
}
