package in.mjmasala.api.dto.catalogue;

/**
 * One jar inside a combo, in the shape an edit form needs.
 *
 * {@code contents} on the combo DTOs is a list of display strings ("Turmeric Powder 100 g"),
 * which is right for the storefront but cannot round-trip through an editor. This is the
 * structured version the admin form binds to.
 */
public record ComboLineDto(
        String productSlug,
        String productName,
        String packLabel,
        int quantity) {
}
