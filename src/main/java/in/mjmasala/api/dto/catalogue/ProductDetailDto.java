package in.mjmasala.api.dto.catalogue;

import java.math.BigDecimal;
import java.util.List;

public record ProductDetailDto(
        Long id,
        String slug,
        String name,
        String hindi,
        String kind,
        String category,
        String imageKey,
        /** uploaded photo, when the client has one; null falls back to imageKey */
        String imageUrl,
        String spiceRgb,
        String lid,
        String origin,
        String blurb,
        String description,
        String ingredients,
        String usage,
        String shelf,
        BigDecimal fromPrice,
        BigDecimal fromMrp,
        int offPct,
        BigDecimal rating,
        int reviewCount,
        List<String> badges,
        boolean bestseller,
        /** shown in the admin list so live and hidden are distinguishable */
        boolean active,
        List<PackDto> packs,
        List<SpecDto> specs) {
}
