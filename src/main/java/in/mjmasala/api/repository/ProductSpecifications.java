package in.mjmasala.api.repository;

import in.mjmasala.api.domain.Product;
import in.mjmasala.api.domain.ProductKind;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * The shop's filter set as reusable, composable predicates, so the query stays in SQL
 * and the service does not hand-roll string concatenation.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> active() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Product> hasStock() {
        return (root, query, cb) -> cb.greaterThan(root.get("minPrice"), BigDecimal.ZERO);
    }

    public static Specification<Product> categorySlug(String slug) {
        if (slug == null || slug.isBlank()) return null;
        return (root, query, cb) -> cb.equal(
                root.join("category", JoinType.INNER).get("slug"), slug);
    }

    public static Specification<Product> kind(ProductKind kind) {
        if (kind == null) return null;
        return (root, query, cb) -> cb.equal(root.get("kind"), kind);
    }

    public static Specification<Product> bestsellerOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("bestseller"));
    }

    /**
     * Matches on the product's cheapest pack — the price the card prints as "from".
     * Reads the denormalised {@code minPrice} rather than joining the pack table, so it
     * composes with paging and sorting without a cartesian product.
     */
    public static Specification<Product> maxPrice(BigDecimal max) {
        if (max == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("minPrice"), max);
    }

    public static Specification<Product> minRating(BigDecimal min) {
        if (min == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("ratingAvg"), min);
    }

    public static Specification<Product> matches(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(root.get("hindi")), like),
                cb.like(cb.lower(root.get("blurb")), like),
                cb.like(cb.lower(root.get("origin")), like));
    }
}
