package in.mjmasala.api.dto.admin;

import in.mjmasala.api.domain.ProductKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create or replace a product, pack ladder and all.
 *
 * The slug is optional on create (derived from the name) and immutable on update, because
 * it is the product's public URL and anything already linking to it must keep working.
 */
public record AdminProductRequest(
        String slug,

        @NotBlank(message = "A product needs a name")
        @Size(max = 120)
        String name,

        @Size(max = 120) String hindi,
        ProductKind kind,
        String categorySlug,
        @Size(max = 120) String origin,
        @Size(max = 400) String blurb,
        String description,
        String ingredients,
        String usage,
        @Size(max = 120) String shelf,
        @Size(max = 12) String spiceRgb,
        @Size(max = 12) String lid,
        @Size(max = 60) String imageKey,
        Boolean bestseller,
        Boolean active,
        List<String> badges,

        @NotEmpty(message = "Add at least one pack")
        @Valid
        List<AdminPackRequest> packs,

        @Valid
        List<AdminSpecRequest> specs) {
}
