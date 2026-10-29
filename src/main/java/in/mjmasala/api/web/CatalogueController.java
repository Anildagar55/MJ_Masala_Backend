package in.mjmasala.api.web;

import in.mjmasala.api.domain.ProductKind;
import in.mjmasala.api.dto.catalogue.CategoryDto;
import in.mjmasala.api.dto.catalogue.ProductCardDto;
import in.mjmasala.api.dto.catalogue.ProductDetailDto;
import in.mjmasala.api.dto.common.PageResponse;
import in.mjmasala.api.service.CatalogueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * The shop. Public — no token required.
 *
 * The query parameters mirror the storefront's filter panel one for one, so switching the
 * React grid over to the API is a change of data source, not of behaviour.
 */
@RestController
@Tag(name = "Catalogue", description = "Products, combos and categories")
public class CatalogueController {

    private final CatalogueService catalogue;

    public CatalogueController(CatalogueService catalogue) {
        this.catalogue = catalogue;
    }

    @GetMapping("/products")
    @Operation(summary = "Filtered, sorted, paged product list")
    public PageResponse<ProductCardDto> products(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProductKind kind,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) Boolean bestseller,
            @RequestParam(required = false, defaultValue = "popular") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return catalogue.listProducts(category, q, kind, maxPrice, minRating, bestseller, sort, page, size);
    }

    @GetMapping("/products/{slug}")
    @Operation(summary = "One product with its pack ladder and spec table")
    public ProductDetailDto product(@PathVariable String slug) {
        return catalogue.getProduct(slug);
    }

    @GetMapping("/categories")
    @Operation(summary = "The shelves shown in the nav")
    public List<CategoryDto> categories() {
        return catalogue.listCategories();
    }
}
