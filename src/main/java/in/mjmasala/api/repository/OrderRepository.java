package in.mjmasala.api.repository;

import in.mjmasala.api.domain.Order;
import in.mjmasala.api.domain.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByReference(String reference);

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByReferenceAndUserId(String reference, Long userId);

    @EntityGraph(attributePaths = {"items"})
    List<Order> findByUserIdOrderByPlacedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"items"})
    List<Order> findAllByOrderByPlacedAtDesc();

    long countByStatus(OrderStatus status);

    /**
     * True when the customer has a delivered order containing this product — the basis
     * for the "verified purchase" badge on a review.
     */
    @Query("""
           select count(o) > 0
           from Order o join o.items i
           where o.user.id = :userId
             and i.product.id = :productId
             and o.status = :status
           """)
    boolean hasDeliveredProduct(@Param("userId") Long userId,
                                @Param("productId") Long productId,
                                @Param("status") OrderStatus status);
}
