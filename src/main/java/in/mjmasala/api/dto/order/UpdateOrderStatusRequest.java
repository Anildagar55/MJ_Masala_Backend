package in.mjmasala.api.dto.order;

import in.mjmasala.api.domain.OrderStatus;
import in.mjmasala.api.domain.PaymentStatus;
import jakarta.validation.constraints.NotNull;

/** Admin only. */
public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status,
        PaymentStatus paymentStatus) {
}
