package in.mjmasala.api.dto.admin;

import java.math.BigDecimal;

/**
 * A row in the payments panel. Derived from the order rather than a separate ledger —
 * there is no gateway yet, so the order *is* the transaction.
 */
public record PaymentRowDto(
        String reference,
        String placedAt,
        String customer,
        String method,
        String status,
        BigDecimal amount,
        BigDecimal codFee,
        String couponCode) {
}
