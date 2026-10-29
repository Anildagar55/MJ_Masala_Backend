package in.mjmasala.api.dto.catalogue;

import java.math.BigDecimal;

/** One rung of the pack ladder, as the product page shows it. */
public record PackDto(
        Long id,
        String label,
        int weightGrams,
        BigDecimal price,
        BigDecimal mrp,
        String sku,
        int offPct) {
}
