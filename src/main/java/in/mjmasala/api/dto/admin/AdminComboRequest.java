package in.mjmasala.api.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record AdminComboRequest(
        String slug,

        @NotBlank(message = "A combo needs a name")
        @Size(max = 120)
        String name,

        @Size(max = 120) String hindi,
        @Size(max = 400) String blurb,
        String description,
        @Size(max = 60) String imageKey,

        @NotNull(message = "Selling price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
        BigDecimal price,

        @NotNull(message = "MRP is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "MRP must be greater than zero")
        BigDecimal mrp,

        Boolean bestseller,
        Boolean active,
        List<String> badges,

        @NotEmpty(message = "Add at least one jar to the box")
        @Valid
        List<AdminComboItemRequest> items,

        @Valid
        List<AdminSpecRequest> specs) {
}
