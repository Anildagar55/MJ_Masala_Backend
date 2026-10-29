package in.mjmasala.api.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** One jar inside the box. */
public record AdminComboItemRequest(
        @NotBlank(message = "Pick a product for this line") String productSlug,
        @NotBlank(message = "Give the jar a weight, e.g. 100 g") String packLabel,
        @Positive @Min(value = 1, message = "At least one jar") int quantity,
        Integer sortOrder) {
}
