package in.mjmasala.api.dto.cart;

import java.math.BigDecimal;

public record CartLineDto(
        Long id,
        String itemType,
        Long productId,
        Long comboId,
        String slug,
        String name,
        String packLabel,
        String imageKey,
        BigDecimal unitPrice,
        BigDecimal mrp,
        int quantity,
        BigDecimal lineTotal) {
}
