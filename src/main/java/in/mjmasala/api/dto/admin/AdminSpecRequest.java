package in.mjmasala.api.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record AdminSpecRequest(
        @NotBlank(message = "Spec needs a label") String label,
        @NotBlank(message = "Spec needs a value") String value,
        Integer sortOrder) {
}
