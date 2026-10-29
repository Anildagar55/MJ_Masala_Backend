package in.mjmasala.api.dto.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateQuantityRequest(
        @Min(value = 0, message = "Quantity cannot be negative")
        @Max(value = 50, message = "That is more than we can ship in one order")
        int quantity) {
}
