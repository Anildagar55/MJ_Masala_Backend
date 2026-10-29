package in.mjmasala.api.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Records that a user found a review helpful. The unique constraint is what stops one
 * account inflating a review's count by tapping repeatedly.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "review_helpful",
       uniqueConstraints = @UniqueConstraint(name = "uk_helpful_review_user", columnNames = {"review_id", "user_id"}))
public class ReviewHelpful extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
