package in.mjmasala.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A customer review of a product.
 *
 * {@code user} is null for reviews imported from the old store (the seeded pool), which
 * is why {@code authorName} and {@code city} are stored on the row rather than read
 * through the account.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "review")
public class Review extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Null for imported reviews; set for reviews written on the site. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "author_name", nullable = false, length = 120)
    private String authorName;

    @Column(length = 80)
    private String city;

    /** 1–5. Enforced in the service layer and by a check on the request DTO. */
    @Column(nullable = false)
    private int rating;

    @Column(length = 160)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    /** True when the reviewer has a delivered order containing this product. */
    @Column(nullable = false)
    private boolean verified = false;

    @Column(name = "helpful_count", nullable = false)
    private int helpfulCount = 0;

    /** Optional customer photo, stored as a data URL or an object-store key. */
    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(nullable = false)
    private boolean approved = true;
}
