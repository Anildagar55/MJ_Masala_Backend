package in.mjmasala.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A discount code.
 *
 * The rules are deliberately narrow: a coupon is either a percentage or a flat rupee
 * amount, may require a minimum subtotal, may cap the discount, and may expire or run
 * out of redemptions. That is the whole surface the storefront needs, and every rule is
 * re-checked server-side at order time — the frontend preview is never trusted.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "coupon", uniqueConstraints = @UniqueConstraint(name = "uk_coupon_code", columnNames = "code"))
public class Coupon extends BaseEntity {

    /** Stored upper-case. Lookups upper-case the input before matching. */
    @Column(nullable = false, length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponType type = CouponType.PERCENT;

    /**
     * Percent (0–100) for PERCENT, or a rupee amount for FLAT.
     *
     * Mapped to {@code coupon_value} rather than {@code value} because VALUE is a
     * reserved word in some engines and quoting it everywhere is a needless trap.
     */
    @Column(name = "coupon_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    /** Minimum cart subtotal required for the code to apply. */
    @Column(name = "min_subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal minSubtotal = BigDecimal.ZERO;

    /** Upper bound on the discount for PERCENT codes. Null means uncapped. */
    @Column(name = "max_discount", precision = 10, scale = 2)
    private BigDecimal maxDiscount;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /** Null means unlimited. */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private int usedCount = 0;

    /** Shown in the cart next to the code, e.g. "15% off everything". */
    @Column(length = 160)
    private String description;
}
