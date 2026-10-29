package in.mjmasala.api.dto.catalogue;

import java.math.BigDecimal;
import java.util.List;

public record ComboDetailDto(
        Long id,
        String slug,
        String name,
        String hindi,
        String imageKey,
        String imageUrl,
        BigDecimal price,
        BigDecimal mrp,
        int offPct,
        BigDecimal rating,
        int reviewCount,
        List<String> badges,
        boolean bestseller,
        boolean active,
        String save,
        String blurb,
        String description,
        List<String> contents,
        /** structured lines, so the admin form can round-trip them */
        List<ComboLineDto> items,
        List<SpecDto> specs) {
}
