package in.mjmasala.api.dto.order;

import java.math.BigDecimal;
import java.util.List;

public record OrderDto(
        Long id,
        String reference,
        String status,
        String paymentMethod,
        String paymentStatus,
        String placedAt,
        String contactName,
        String contactPhone,
        String contactEmail,
        String shipLine,
        String shipCity,
        String shipState,
        String shipPin,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal shippingFee,
        BigDecimal codFee,
        BigDecimal total,
        String couponCode,
        List<OrderLineDto> items) {
}
