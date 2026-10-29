package in.mjmasala.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A storefront shelf: Masala, Whole Spices, Combos. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "category", uniqueConstraints = @UniqueConstraint(name = "uk_category_slug", columnNames = "slug"))
public class Category extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String slug;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean active = true;
}
