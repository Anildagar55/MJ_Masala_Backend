package in.mjmasala.api.dto.admin;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record PackPatchRequest(
        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
        BigDecimal price,
        @DecimalMin(value = "0.0", inclusive = false, message = "MRP must be greater than zero")
        BigDecimal mrp,
        Boolean active) {
}
