package in.mjmasala.api.util;

import java.math.BigDecimal;

/**
 * Indian money formatting for the few places the API builds a display string (the combo
 * "Save ₹300" chip). Amounts themselves travel as numbers and the frontend formats them.
 */
public final class Rupees {

    private Rupees() {
    }

    public static String format(BigDecimal amount) {
        if (amount == null) return "₹0";
        return "₹" + amount.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
