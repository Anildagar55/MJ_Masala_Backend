package in.mjmasala.api.dto.admin;

import in.mjmasala.api.domain.PaymentStatus;
import jakarta.validation.constraints.NotNull;

/** The payments panel: settle, fail or refund an order's payment. */
public record PaymentPatchRequest(
        @NotNull(message = "Pick a payment status") PaymentStatus paymentStatus,
        String note) {
}
