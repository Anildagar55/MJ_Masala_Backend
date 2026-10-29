package in.mjmasala.api.service;

import in.mjmasala.api.domain.Category;
import in.mjmasala.api.domain.Combo;
import in.mjmasala.api.domain.ComboItem;
import in.mjmasala.api.domain.Product;
import in.mjmasala.api.domain.ProductPack;
import in.mjmasala.api.domain.ProductSpec;
import in.mjmasala.api.dto.catalogue.ComboCardDto;
import in.mjmasala.api.dto.catalogue.ComboDetailDto;
import in.mjmasala.api.dto.catalogue.PackDto;
import in.mjmasala.api.dto.catalogue.ProductCardDto;
import in.mjmasala.api.dto.catalogue.ProductDetailDto;
import in.mjmasala.api.dto.catalogue.SpecDto;
import in.mjmasala.api.util.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Entity → DTO. Kept apart from the services so the mapping rules are easy to find. */
@Component
public class CatalogueMapper {

    private final in.mjmasala.api.service.media.MediaService media;

    public CatalogueMapper(in.mjmasala.api.service.media.MediaService media) {
        this.media = media;
    }

    public PackDto toPack(ProductPack pack) {
        return new PackDto(
                pack.getId(),
                pack.getLabel(),
                pack.getWeightGrams(),
                pack.getPrice(),
                pack.getMrp(),
                pack.getSku(),
                Money.offPct(pack.getMrp(), pack.getPrice()));
    }

    public SpecDto toSpec(ProductSpec spec) {
        return new SpecDto(spec.getLabel(), spec.getValue());
    }

    public SpecDto toSpec(in.mjmasala.api.domain.ComboSpec spec) {
        return new SpecDto(spec.getLabel(), spec.getValue());
    }

    /** Cheapest active pack drives the "from ₹x" on the card. */
    public ProductPack cheapestPack(Collection<ProductPack> packs) {
        return packs.stream()
                .filter(ProductPack::isActive)
                .min(Comparator.comparing(ProductPack::getPrice))
                .orElse(null);
    }

    public ProductCardDto toCard(Product product, Collection<ProductPack> packs,
                                 Set<String> badges, Category category) {
        ProductPack cheapest = cheapestPack(packs);
        BigDecimal price = cheapest != null ? cheapest.getPrice() : Money.ZERO;
        BigDecimal mrp = cheapest != null ? cheapest.getMrp() : Money.ZERO;
        return new ProductCardDto(
                product.getId(),
                product.getSlug(),
                product.getName(),
                product.getHindi(),
                product.getKind().name(),
                category != null ? category.getSlug() : null,
                product.getImageKey(),
                media.publicUrl(product.getImageFile()),
                price,
                mrp,
                Money.offPct(mrp, price),
                product.getRatingAvg(),
                product.getReviewCount(),
                List.copyOf(badges),
                product.isBestseller(),
                packs.stream().filter(ProductPack::isActive).map(this::toPack).toList());
    }

    public ProductDetailDto toDetail(Product product) {
        ProductPack cheapest = cheapestPack(product.getPacks());
        BigDecimal price = cheapest != null ? cheapest.getPrice() : Money.ZERO;
        BigDecimal mrp = cheapest != null ? cheapest.getMrp() : Money.ZERO;
        return new ProductDetailDto(
                product.getId(),
                product.getSlug(),
                product.getName(),
                product.getHindi(),
                product.getKind().name(),
                product.getCategory() != null ? product.getCategory().getSlug() : null,
                product.getImageKey(),
                media.publicUrl(product.getImageFile()),
                product.getSpiceRgb(),
                product.getLid(),
                product.getOrigin(),
                product.getBlurb(),
                product.getDescription(),
                product.getIngredients(),
                product.getUsage(),
                product.getShelf(),
                price,
                mrp,
                Money.offPct(mrp, price),
                product.getRatingAvg(),
                product.getReviewCount(),
                List.copyOf(product.getBadges()),
                product.isBestseller(),
                product.isActive(),
                product.getPacks().stream().filter(ProductPack::isActive).map(this::toPack).toList(),
                product.getSpecs().stream().map(this::toSpec).toList());
    }

    public ComboCardDto toComboCard(Combo combo) {
        return new ComboCardDto(
                combo.getId(),
                combo.getSlug(),
                combo.getName(),
                combo.getHindi(),
                combo.getImageKey(),
                media.publicUrl(combo.getImageFile()),
                combo.getPrice(),
                combo.getMrp(),
                Money.offPct(combo.getMrp(), combo.getPrice()),
                combo.getRatingAvg(),
                combo.getReviewCount(),
                List.copyOf(combo.getBadges()),
                combo.isBestseller(),
                "Save " + in.mjmasala.api.util.Rupees.format(combo.getMrp().subtract(combo.getPrice())),
                contents(combo));
    }

    public ComboDetailDto toComboDetail(Combo combo) {
        return new ComboDetailDto(
                combo.getId(),
                combo.getSlug(),
                combo.getName(),
                combo.getHindi(),
                combo.getImageKey(),
                media.publicUrl(combo.getImageFile()),
                combo.getPrice(),
                combo.getMrp(),
                Money.offPct(combo.getMrp(), combo.getPrice()),
                combo.getRatingAvg(),
                combo.getReviewCount(),
                List.copyOf(combo.getBadges()),
                combo.isBestseller(),
                combo.isActive(),
                "Save " + in.mjmasala.api.util.Rupees.format(combo.getMrp().subtract(combo.getPrice())),
                combo.getBlurb(),
                combo.getDescription(),
                contents(combo),
                combo.getItems().stream()
                        .sorted(Comparator.comparingInt(ComboItem::getSortOrder))
                        .map(i -> new in.mjmasala.api.dto.catalogue.ComboLineDto(
                                i.getProduct().getSlug(),
                                i.getProduct().getName(),
                                i.getPackLabel(),
                                i.getQuantity()))
                        .toList(),
                combo.getSpecs().stream().map(this::toSpec).toList());
    }

    /** "Turmeric Powder 100 g" — how the box contents are listed. */
    private List<String> contents(Combo combo) {
        return combo.getItems().stream()
                .sorted(Comparator.comparingInt(ComboItem::getSortOrder))
                .map(i -> i.getProduct().getName() + " " + i.getPackLabel())
                .toList();
    }
}
