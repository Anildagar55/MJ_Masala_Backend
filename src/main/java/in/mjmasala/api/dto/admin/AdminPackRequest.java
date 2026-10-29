package in.mjmasala.api.dto.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** One rung of the pack ladder, as the admin form edits it. */
public record AdminPackRequest(
        @NotBlank(message = "Give the pack a label, e.g. 500 g") String label,
        @Positive(message = "Weight must be greater than zero") int weightGrams,
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
        BigDecimal price,
        @NotNull(message = "MRP is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "MRP must be greater than zero")
        BigDecimal mrp,
        String sku,
        Boolean active,
        Integer sortOrder) {
}
