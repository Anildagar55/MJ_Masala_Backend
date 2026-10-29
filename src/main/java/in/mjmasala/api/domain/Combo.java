package in.mjmasala.api.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
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

/** A boxed set of jars sold at a bundle price. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "combo", uniqueConstraints = @UniqueConstraint(name = "uk_combo_slug", columnNames = "slug"))
public class Combo extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String slug;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 120)
    private String hindi;

    @Column(length = 400)
    private String blurb;

    @Column(columnDefinition = "text")
    private String description;

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

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal mrp;

    @Column(nullable = false)
    private boolean bestseller = false;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    /**
     * Review aggregates for the box as a whole. Derived from the combo's own reviews by
     * the review service — same contract as on {@link Product}.
     */
    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 1)
    private java.math.BigDecimal ratingAvg = java.math.BigDecimal.ZERO;

    @Column(name = "review_count", nullable = false)
    private int reviewCount = 0;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "combo_badge", joinColumns = @JoinColumn(name = "combo_id"))
    @Column(name = "badge", length = 60)
    private Set<String> badges = new LinkedHashSet<>();

    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<ComboItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "combo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<ComboSpec> specs = new ArrayList<>();
}
