package in.mjmasala.api.repository;

import in.mjmasala.api.domain.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    @EntityGraph(attributePaths = {"user"})
    List<Review> findByProductIdAndApprovedTrueOrderByCreatedAtDesc(Long productId);

    Page<Review> findByProductIdAndApprovedTrue(Long productId, Pageable pageable);

    long countByProductIdAndApprovedTrue(Long productId);

    @Query("""
           select coalesce(avg(r.rating), 0)
           from Review r
           where r.product.id = :productId and r.approved = true
           """)
    double averageForProduct(@Param("productId") Long productId);

    /** Rows of [rating, count], used to draw the 5→1 star distribution bars. */
    @Query("""
           select r.rating, count(r)
           from Review r
           where r.product.id = :productId and r.approved = true
           group by r.rating
           """)
    List<Object[]> distributionForProduct(@Param("productId") Long productId);

    Optional<Review> findByIdAndUserId(Long id, Long userId);

    boolean existsByProductIdAndUserId(Long productId, Long userId);

    @EntityGraph(attributePaths = {"product"})
    List<Review> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);
}
