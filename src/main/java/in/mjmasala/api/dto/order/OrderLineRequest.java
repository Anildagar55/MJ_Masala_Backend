package in.mjmasala.api.dto.order;

import in.mjmasala.api.domain.ItemType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record OrderLineRequest(
        ItemType itemType,
        Long productId,
        Long packId,
        Long comboId,

        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 50, message = "That is more than we can ship in one order")
        int quantity) {
}
