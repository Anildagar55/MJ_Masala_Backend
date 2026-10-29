package in.mjmasala.api.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A single SKU — one spice, sold in a ladder of pack sizes. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product", uniqueConstraints = @UniqueConstraint(name = "uk_product_slug", columnNames = "slug"))
public class Product extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(nullable = false, length = 120)
    private String name;

    /** Hindi name as printed on the label. */
    @Column(length = 120)
    private String hindi;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductKind kind = ProductKind.POWDER;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(length = 120)
    private String origin;

    @Column(length = 400)
    private String blurb;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String ingredients;

    @Column(name = "usage_notes", columnDefinition = "text")
    private String usage;

    @Column(name = "shelf_life", length = 120)
    private String shelf;

    /** The fill colour of the jar mock-up, as a hex string. */
    @Column(name = "spice_rgb", length = 12)
    private String spiceRgb;

    @Column(length = 12)
    private String lid;

    /** Key the frontend maps to a bundled image, e.g. {@code turmeric}. */
    @Column(name = "image_key", length = 60)
    private String imageKey;

    /**
     * Filename of an uploaded photograph, when the client has one.
     *
     * The filename only — the public URL is built from {@code app.media.public-path} at
     * mapping time, so changing the path or moving the files to a CDN does not rewrite
     * every row. Null means "no upload yet", and the storefront falls back to the bundled
     * placeholder for that SKU.
     */
    @Column(name = "image_file", length = 200)
    private String imageFile;

    /**
     * Denormalised review aggregates, recalculated by the review service whenever a
     * review is written or removed. They live on the row so the shop can filter and
     * sort by rating in SQL instead of loading every review for every product.
     * They are derived data — never set them by hand.
     */
    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 1)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    /**
     * Cheapest active pack price, and the discount shown against it.
     *
     * Both are derived from {@link #packs} by {@link #recalculatePackAggregates()}, and
     * both are what the product card prints as "from ₹x, y% off". Keeping the sortable
     * column identical to the displayed number is what stops the shop sorting on one
     * figure and printing another.
     */
    @Column(name = "min_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal minPrice = BigDecimal.ZERO;

    @Column(name = "off_pct", nullable = false)
    private int offPct = 0;

    @Column(name = "review_count", nullable = false)
    private int reviewCount = 0;

    @Column(nullable = false)
    private boolean bestseller = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "product_badge", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "badge", length = 60)
    private Set<String> badges = new LinkedHashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<ProductPack> packs = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<ProductSpec> specs = new ArrayList<>();

    /**
     * Recomputes the denormalised pack aggregates. Call it after touching {@link #packs} —
     * the seeder does, and so would an admin edit. Keeping it here means the rule lives
     * next to the data it summarises rather than in a service.
     */
    public void recalculatePackAggregates() {
        ProductPack cheapest = null;
        for (ProductPack pack : packs) {
            if (!pack.isActive()) continue;
            if (cheapest == null || pack.getPrice().compareTo(cheapest.getPrice()) < 0) {
                cheapest = pack;
            }
        }
        this.minPrice = cheapest == null ? BigDecimal.ZERO : cheapest.getPrice();
        this.offPct = cheapest == null ? 0 : in.mjmasala.api.util.Money.offPct(cheapest.getMrp(), cheapest.getPrice());
    }
}
