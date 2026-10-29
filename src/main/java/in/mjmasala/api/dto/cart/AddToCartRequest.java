package in.mjmasala.api.dto.cart;

import in.mjmasala.api.domain.ItemType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Exactly one of packId / comboId is required, matching itemType. The service rejects a
 * mismatched pair rather than silently guessing.
 */
public record AddToCartRequest(
        ItemType itemType,
        Long productId,
        Long packId,
        Long comboId,

        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 50, message = "That is more than we can ship in one order")
        Integer quantity) {
}
