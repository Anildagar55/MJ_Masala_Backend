package in.mjmasala.api.dto.order;

import java.math.BigDecimal;

public record OrderLineDto(
        String itemType,
        String name,
        String packLabel,
        String slug,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal) {
}
