package in.mjmasala.api.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money and percentage helpers, so rounding rules live in exactly one place. */
public final class Money {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private Money() {
    }

    /** Always two decimal places, half-up — the convention for rupee amounts. */
    public static BigDecimal round(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(long rupees) {
        return BigDecimal.valueOf(rupees).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Discount percentage of {@code mrp} represented by {@code price}, 0–100.
     * Returns 0 when there is no genuine markdown or the inputs are unusable.
     */
    public static int offPct(BigDecimal mrp, BigDecimal price) {
        if (mrp == null || price == null) return 0;
        if (mrp.compareTo(BigDecimal.ZERO) <= 0) return 0;
        if (price.compareTo(mrp) >= 0) return 0;
        return mrp.subtract(price)
                .multiply(BigDecimal.valueOf(100))
                .divide(mrp, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.min(b);
    }
}
