package in.mjmasala.api.dto.admin;

import jakarta.validation.constraints.Size;

public record ProductPatchRequest(
        Boolean active,
        Boolean bestseller,
        @Size(max = 400) String blurb,
        String description) {
}
