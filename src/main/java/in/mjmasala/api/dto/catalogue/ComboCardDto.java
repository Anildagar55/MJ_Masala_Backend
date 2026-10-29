package in.mjmasala.api.dto.catalogue;

import java.math.BigDecimal;
import java.util.List;

public record ComboCardDto(
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
        String save,
        List<String> contents) {
}
