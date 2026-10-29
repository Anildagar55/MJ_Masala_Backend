package in.mjmasala.api.service;

import in.mjmasala.api.domain.Category;
import in.mjmasala.api.domain.Combo;
import in.mjmasala.api.domain.ComboItem;
import in.mjmasala.api.domain.ComboSpec;
import in.mjmasala.api.domain.Product;
import in.mjmasala.api.domain.ProductKind;
import in.mjmasala.api.domain.ProductPack;
import in.mjmasala.api.domain.ProductSpec;
import in.mjmasala.api.dto.admin.AdminComboItemRequest;
import in.mjmasala.api.dto.admin.AdminComboRequest;
import in.mjmasala.api.dto.admin.AdminPackRequest;
import in.mjmasala.api.dto.admin.AdminProductRequest;
import in.mjmasala.api.dto.admin.AdminSpecRequest;
import in.mjmasala.api.dto.catalogue.ComboDetailDto;
import in.mjmasala.api.dto.catalogue.ProductDetailDto;
import in.mjmasala.api.exception.BadRequestException;
import in.mjmasala.api.exception.ConflictException;
import in.mjmasala.api.exception.NotFoundException;
import in.mjmasala.api.repository.CategoryRepository;
import in.mjmasala.api.repository.ComboRepository;
import in.mjmasala.api.repository.ProductRepository;
import in.mjmasala.api.util.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Creating and editing the catalogue from the back office.
 *
 * Two rules are enforced here rather than trusted to the form:
 *
 * 1. **A pack's MRP can never be below its selling price.** A "discount" that goes the
 *    wrong way would make the storefront print a negative percentage.
 * 2. **The derived columns are always refreshed.** {@code minPrice}, {@code offPct} and
 *    {@code ratingAvg} are recomputed on every write, so a price change in the admin panel
 *    immediately shows up in the shop's filters and sorts.
 */
@Service
public class AdminCatalogueService {

    private final ProductRepository products;
    private final ComboRepository combos;
    private final CategoryRepository categories;
    private final CatalogueMapper mapper;
    private final in.mjmasala.api.service.media.MediaService media;

    public AdminCatalogueService(ProductRepository products, ComboRepository combos,
                                 CategoryRepository categories, CatalogueMapper mapper,
                                 in.mjmasala.api.service.media.MediaService media) {
        this.products = products;
        this.combos = combos;
        this.categories = categories;
        this.mapper = mapper;
        this.media = media;
    }

    /* ------------------------------ products ------------------------------ */

    @Transactional(readOnly = true)
    public List<ProductDetailDto> listProducts() {
        return products.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(mapper::toDetail)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductDetailDto getProduct(String slug) {
        return products.findAll().stream()
                .filter(p -> p.getSlug().equals(slug))
                .findFirst()
                .map(mapper::toDetail)
                .orElseThrow(() -> new NotFoundException("No product with slug " + slug));
    }

    @Transactional
    public ProductDetailDto createProduct(AdminProductRequest request) {
        String slug = slugFor(request.slug(), request.name());
        if (products.existsBySlug(slug)) {
            throw new ConflictException("A product already uses the slug " + slug);
        }

        Product product = new Product();
        product.setSlug(slug);
        apply(product, request);
        product.setSortOrder((int) products.count());
        product.recalculatePackAggregates();

        return mapper.toDetail(products.save(product));
    }

    @Transactional
    public ProductDetailDto updateProduct(String slug, AdminProductRequest request) {
        Product product = products.findAll().stream()
                .filter(p -> p.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No product with slug " + slug));

        // the slug is the public URL — renaming it would break every existing link
        apply(product, request);

        // The ladder is replaced wholesale: the form owns the whole list, so diffing
        // would only add places for the two to disagree.
        //
        // The flush matters. Pack SKUs are unique, and Hibernate orders its statements
        // inserts-before-deletes — without forcing the deletes out first, re-adding the
        // same SKU collides with the row it is replacing.
        product.getPacks().clear();
        product.getSpecs().clear();
        products.flush();

        for (AdminPackRequest packRequest : request.packs()) {
            product.getPacks().add(toPack(product, packRequest, product.getPacks().size()));
        }
        for (AdminSpecRequest spec : safe(request.specs())) {
            product.getSpecs().add(toProductSpec(product, spec, product.getSpecs().size()));
        }

        product.recalculatePackAggregates();
        return mapper.toDetail(products.save(product));
    }

    /**
     * Replaces a product's photograph.
     *
     * The old file is deleted only after the new one is safely stored, so a failed upload
     * never leaves the product with no image at all.
     */
    @Transactional
    public ProductDetailDto setProductImage(String slug, org.springframework.web.multipart.MultipartFile file) {
        Product product = products.findAll().stream()
                .filter(p -> p.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No product with slug " + slug));

        product.setImageFile(media.store(slug, file, product.getImageFile()));
        return mapper.toDetail(products.save(product));
    }

    @Transactional
    public ProductDetailDto clearProductImage(String slug) {
        Product product = products.findAll().stream()
                .filter(p -> p.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No product with slug " + slug));

        media.delete(product.getImageFile());
        product.setImageFile(null);
        return mapper.toDetail(products.save(product));
    }

    @Transactional
    public ComboDetailDto setComboImage(String slug, org.springframework.web.multipart.MultipartFile file) {
        Combo combo = combos.findAll().stream()
                .filter(c -> c.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No combo with slug " + slug));

        combo.setImageFile(media.store(slug, file, combo.getImageFile()));
        return mapper.toComboDetail(combos.save(combo));
    }

    @Transactional
    public ComboDetailDto clearComboImage(String slug) {
        Combo combo = combos.findAll().stream()
                .filter(c -> c.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No combo with slug " + slug));

        media.delete(combo.getImageFile());
        combo.setImageFile(null);
        return mapper.toComboDetail(combos.save(combo));
    }

    @Transactional
    public void deactivateProduct(String slug) {
        Product product = products.findAll().stream()
                .filter(p -> p.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No product with slug " + slug));
        product.setActive(false);
        products.save(product);
    }

    private void apply(Product product, AdminProductRequest request) {
        product.setName(request.name().trim());
        product.setHindi(trimToNull(request.hindi()));
        product.setKind(request.kind() == null ? ProductKind.POWDER : request.kind());
        product.setCategory(categoryFor(request.categorySlug(), product.getKind()));
        product.setOrigin(trimToNull(request.origin()));
        product.setBlurb(trimToNull(request.blurb()));
        product.setDescription(trimToNull(request.description()));
        product.setIngredients(trimToNull(request.ingredients()));
        product.setUsage(trimToNull(request.usage()));
        product.setShelf(trimToNull(request.shelf()));
        product.setSpiceRgb(trimToNull(request.spiceRgb()));
        product.setLid(trimToNull(request.lid()));
        product.setImageKey(trimToNull(request.imageKey()) != null
                ? request.imageKey().trim() : product.getSlug());
        product.setBestseller(Boolean.TRUE.equals(request.bestseller()));
        product.setActive(request.active() == null || request.active());

        product.getBadges().clear();
        for (String badge : safe(request.badges())) {
            if (badge != null && !badge.isBlank()) product.getBadges().add(badge.trim());
        }

        // On create the ladder and the spec table are empty, so this is the whole list.
        // On update the caller has already got the existing rows in place and replaces
        // them itself, so these are skipped.
        if (product.getPacks().isEmpty()) {
            for (AdminPackRequest packRequest : request.packs()) {
                product.getPacks().add(toPack(product, packRequest, product.getPacks().size()));
            }
        }
        if (product.getSpecs().isEmpty()) {
            for (AdminSpecRequest spec : safe(request.specs())) {
                product.getSpecs().add(toProductSpec(product, spec, product.getSpecs().size()));
            }
        }
    }

    private ProductPack toPack(Product product, AdminPackRequest request, int order) {
        BigDecimal price = Money.round(request.price());
        BigDecimal mrp = Money.round(request.mrp());
        if (mrp.compareTo(price) < 0) {
            throw new BadRequestException(
                    "The " + request.label() + " pack has an MRP below its selling price");
        }

        ProductPack pack = new ProductPack();
        pack.setProduct(product);
        pack.setLabel(request.label().trim());
        pack.setWeightGrams(request.weightGrams());
        pack.setPrice(price);
        pack.setMrp(mrp);
        pack.setSku(request.sku() == null || request.sku().isBlank()
                ? product.getSlug() + "-" + request.weightGrams() + "g"
                : request.sku().trim());
        pack.setActive(request.active() == null || request.active());
        pack.setSortOrder(request.sortOrder() != null ? request.sortOrder() : order);
        return pack;
    }

    private ProductSpec toProductSpec(Product product, AdminSpecRequest request, int order) {
        ProductSpec spec = new ProductSpec();
        spec.setProduct(product);
        spec.setLabel(request.label().trim());
        spec.setValue(request.value().trim());
        spec.setSortOrder(request.sortOrder() != null ? request.sortOrder() : order);
        return spec;
    }

    /* ------------------------------- combos ------------------------------- */

    @Transactional(readOnly = true)
    public List<ComboDetailDto> listCombos() {
        return combos.findAll().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(mapper::toComboDetail)
                .toList();
    }

    @Transactional(readOnly = true)
    public ComboDetailDto getCombo(String slug) {
        return combos.findAll().stream()
                .filter(c -> c.getSlug().equals(slug))
                .findFirst()
                .map(mapper::toComboDetail)
                .orElseThrow(() -> new NotFoundException("No combo with slug " + slug));
    }

    @Transactional
    public ComboDetailDto createCombo(AdminComboRequest request) {
        String slug = slugFor(request.slug(), request.name());
        boolean taken = combos.findAll().stream().anyMatch(c -> c.getSlug().equals(slug));
        if (taken) {
            throw new ConflictException("A combo already uses the slug " + slug);
        }

        Combo combo = new Combo();
        combo.setSlug(slug);
        combo.setSortOrder((int) combos.count());
        apply(combo, request);

        return mapper.toComboDetail(combos.save(combo));
    }

    @Transactional
    public ComboDetailDto updateCombo(String slug, AdminComboRequest request) {
        Combo combo = combos.findAll().stream()
                .filter(c -> c.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No combo with slug " + slug));

        apply(combo, request);
        return mapper.toComboDetail(combos.save(combo));
    }

    private void apply(Combo combo, AdminComboRequest request) {
        BigDecimal price = Money.round(request.price());
        BigDecimal mrp = Money.round(request.mrp());
        if (mrp.compareTo(price) < 0) {
            throw new BadRequestException("The combo's MRP is below its selling price");
        }

        combo.setName(request.name().trim());
        combo.setHindi(trimToNull(request.hindi()));
        combo.setBlurb(trimToNull(request.blurb()));
        combo.setDescription(trimToNull(request.description()));
        combo.setImageKey(trimToNull(request.imageKey()) != null
                ? request.imageKey().trim() : combo.getSlug());
        combo.setPrice(price);
        combo.setMrp(mrp);
        combo.setBestseller(Boolean.TRUE.equals(request.bestseller()));
        combo.setActive(request.active() == null || request.active());

        combo.getBadges().clear();
        for (String badge : safe(request.badges())) {
            if (badge != null && !badge.isBlank()) combo.getBadges().add(badge.trim());
        }

        combo.getItems().clear();
        combo.getSpecs().clear();
        combos.flush();

        for (AdminComboItemRequest itemRequest : request.items()) {
            Product product = products.findAll().stream()
                    .filter(p -> p.getSlug().equals(itemRequest.productSlug()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException(
                            "No product with slug " + itemRequest.productSlug()));

            ComboItem item = new ComboItem();
            item.setCombo(combo);
            item.setProduct(product);
            item.setPackLabel(itemRequest.packLabel().trim());
            item.setQuantity(itemRequest.quantity());
            item.setSortOrder(itemRequest.sortOrder() != null
                    ? itemRequest.sortOrder() : combo.getItems().size());
            combo.getItems().add(item);
        }

        for (AdminSpecRequest spec : safe(request.specs())) {
            ComboSpec row = new ComboSpec();
            row.setCombo(combo);
            row.setLabel(spec.label().trim());
            row.setValue(spec.value().trim());
            row.setSortOrder(spec.sortOrder() != null ? spec.sortOrder() : combo.getSpecs().size());
            combo.getSpecs().add(row);
        }
    }

    /* ------------------------------ helpers ------------------------------ */

    private Category categoryFor(String slug, ProductKind kind) {
        if (slug != null && !slug.isBlank()) {
            return categories.findBySlug(slug)
                    .orElseThrow(() -> new BadRequestException("No category called " + slug));
        }
        String fallback = kind == ProductKind.WHOLE ? "whole" : "masala";
        return categories.findBySlug(fallback)
                .orElseGet(() -> categories.findAll().stream().findFirst()
                        .orElseThrow(() -> new BadRequestException("No categories exist yet")));
    }

    /** "Kashmiri Red Chilli" → "kashmiri-red-chilli", with a numeric suffix if taken. */
    private String slugFor(String provided, String name) {
        String base = provided != null && !provided.isBlank() ? provided : name;
        String slug = base.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isEmpty()) {
            throw new BadRequestException("Could not build a slug from that name");
        }
        return slug;
    }

    private <T> List<T> safe(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
