package in.mjmasala.api.service;

import in.mjmasala.api.domain.Category;
import in.mjmasala.api.domain.Product;
import in.mjmasala.api.domain.ProductKind;
import in.mjmasala.api.domain.ProductPack;
import in.mjmasala.api.dto.catalogue.CategoryDto;
import in.mjmasala.api.dto.catalogue.ComboCardDto;
import in.mjmasala.api.dto.catalogue.ComboDetailDto;
import in.mjmasala.api.dto.catalogue.ProductCardDto;
import in.mjmasala.api.dto.catalogue.ProductDetailDto;
import in.mjmasala.api.dto.common.PageResponse;
import in.mjmasala.api.exception.NotFoundException;
import in.mjmasala.api.repository.CategoryRepository;
import in.mjmasala.api.repository.ComboRepository;
import in.mjmasala.api.repository.ProductPackRepository;
import in.mjmasala.api.repository.ProductRepository;
import in.mjmasala.api.repository.ProductSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads for the storefront: the shop grid, a product page, the combos and the shelves. */
@Service
@Transactional(readOnly = true)
public class CatalogueService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository products;
    private final ProductPackRepository packs;
    private final ComboRepository combos;
    private final CategoryRepository categories;
    private final CatalogueMapper mapper;

    public CatalogueService(ProductRepository products, ProductPackRepository packs,
                            ComboRepository combos, CategoryRepository categories,
                            CatalogueMapper mapper) {
        this.products = products;
        this.packs = packs;
        this.combos = combos;
        this.categories = categories;
        this.mapper = mapper;
    }

    /**
     * The shop query.
     *
     * Paging and sorting happen in SQL against the denormalised {@code minPrice},
     * {@code ratingAvg} and {@code offPct} columns. The collections the cards need
     * (packs, badges) are then batch-loaded for the page in two queries rather than
     * fetched per row — the difference between 3 queries and 50 on a full page.
     */
    public PageResponse<ProductCardDto> listProducts(String categorySlug, String q, ProductKind kind,
                                                     BigDecimal maxPrice, BigDecimal minRating,
                                                     Boolean bestseller, String sort,
                                                     int page, int size) {

        Specification<Product> spec = ProductSpecifications.active();
        spec = and(spec, ProductSpecifications.hasStock());
        spec = and(spec, ProductSpecifications.categorySlug(categorySlug));
        spec = and(spec, ProductSpecifications.kind(kind));
        spec = and(spec, ProductSpecifications.maxPrice(maxPrice));
        spec = and(spec, ProductSpecifications.minRating(minRating));
        spec = and(spec, ProductSpecifications.matches(q));
        if (Boolean.TRUE.equals(bestseller)) {
            spec = and(spec, ProductSpecifications.bestsellerOnly());
        }

        PageRequest request = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), MAX_PAGE_SIZE),
                sortFor(sort));

        Page<Product> found = products.findAll(spec, request);
        if (found.isEmpty()) {
            return PageResponse.of(found, p -> null);
        }

        List<Long> ids = found.getContent().stream().map(Product::getId).toList();

        Map<Long, List<ProductPack>> packsByProduct = new HashMap<>();
        for (ProductPack pack : packs.findByProductIdInAndActiveTrueOrderBySortOrderAsc(ids)) {
            packsByProduct.computeIfAbsent(pack.getProduct().getId(), k -> new ArrayList<>()).add(pack);
        }

        Map<Long, Set<String>> badgesByProduct = new HashMap<>();
        for (Object[] row : products.badgesForIds(ids)) {
            badgesByProduct.computeIfAbsent((Long) row[0], k -> new LinkedHashSet<>()).add((String) row[1]);
        }

        Map<Long, Category> categoriesById = new HashMap<>();
        for (Category c : categories.findAll()) {
            categoriesById.put(c.getId(), c);
        }

        return PageResponse.of(found, product -> mapper.toCard(
                product,
                packsByProduct.getOrDefault(product.getId(), List.of()),
                badgesByProduct.getOrDefault(product.getId(), Set.of()),
                product.getCategory() != null
                        ? categoriesById.get(product.getCategory().getId())
                        : null));
    }

    public ProductDetailDto getProduct(String slug) {
        Product product = products.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new NotFoundException("We could not find a spice called " + slug));
        return mapper.toDetail(product);
    }

    public List<ComboCardDto> listCombos() {
        return combos.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(mapper::toComboCard)
                .toList();
    }

    public ComboDetailDto getCombo(String slug) {
        return combos.findBySlugAndActiveTrue(slug)
                .map(mapper::toComboDetail)
                .orElseThrow(() -> new NotFoundException("We could not find a combo called " + slug));
    }

    public List<CategoryDto> listCategories() {
        return categories.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(c -> new CategoryDto(c.getSlug(), c.getName(), c.getSortOrder()))
                .toList();
    }

    /** Translates the frontend's sort keys into an ORDER BY. */
    private Sort sortFor(String sort) {
        if (sort == null) sort = "popular";
        return switch (sort) {
            case "price-asc" -> Sort.by(Sort.Order.asc("minPrice"), Sort.Order.asc("name"));
            case "price-desc" -> Sort.by(Sort.Order.desc("minPrice"), Sort.Order.asc("name"));
            case "rating" -> Sort.by(Sort.Order.desc("ratingAvg"), Sort.Order.desc("reviewCount"));
            case "discount" -> Sort.by(Sort.Order.desc("offPct"), Sort.Order.asc("minPrice"));
            case "newest" -> Sort.by(Sort.Order.desc("id"));
            case "name" -> Sort.by(Sort.Order.asc("name"));
            // "popular" — what most kitchens reorder first
            default -> Sort.by(Sort.Order.desc("bestseller"),
                    Sort.Order.desc("ratingAvg"),
                    Sort.Order.desc("reviewCount"),
                    Sort.Order.asc("name"));
        };
    }

    private Specification<Product> and(Specification<Product> base, Specification<Product> extra) {
        return extra == null ? base : base.and(extra);
    }

    /** Exposed so the review service can reject a review for an unknown product. */
    public Product requireProduct(Long id) {
        return products.findById(id)
                .orElseThrow(() -> NotFoundException.of("Product", id));
    }

    public Collection<Category> allCategories() {
        return categories.findAll();
    }
}
