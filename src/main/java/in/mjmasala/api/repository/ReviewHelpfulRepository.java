package in.mjmasala.api.repository;

import in.mjmasala.api.domain.ReviewHelpful;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewHelpfulRepository extends JpaRepository<ReviewHelpful, Long> {

    boolean existsByReviewIdAndUserId(Long reviewId, Long userId);

    long countByReviewId(Long reviewId);

    void deleteByReviewId(Long reviewId);
}
