package in.mjmasala.api.dto.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartDto(
        List<CartLineDto> lines,
        int itemCount,
        BigDecimal subtotal) {
}
