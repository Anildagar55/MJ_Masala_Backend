package in.mjmasala.api.dto.catalogue;

import java.math.BigDecimal;
import java.util.List;

/**
 * The shape the shop grid and product cards need. Deliberately smaller than the detail
 * DTO so a 24-card page does not ship every description and spec row.
 */
public record ProductCardDto(
        Long id,
        String slug,
        String name,
        String hindi,
        String kind,
        String category,
        String imageKey,
        /** uploaded photo, when the client has one; null falls back to imageKey */
        String imageUrl,
        /** Cheapest active pack — what the card prints as "from". */
        BigDecimal fromPrice,
        BigDecimal fromMrp,
        int offPct,
        BigDecimal rating,
        int reviewCount,
        List<String> badges,
        boolean bestseller,
        /**
         * The pack ladder. Included so a card can render its "from" price and add the
         * right pack to the cart without a follow-up request. If the catalogue grows
         * past a few hundred SKUs, split this into a ?fields=card variant instead.
         */
        List<PackDto> packs) {
}
