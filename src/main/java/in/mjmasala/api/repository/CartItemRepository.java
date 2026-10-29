package in.mjmasala.api.repository;

import in.mjmasala.api.domain.CartItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @EntityGraph(attributePaths = {"product", "product.category", "pack", "combo",
            "combo.items", "combo.items.product"})
    List<CartItem> findByUserIdOrderByIdAsc(Long userId);

    Optional<CartItem> findByIdAndUserId(Long id, Long userId);

    Optional<CartItem> findByUserIdAndPackId(Long userId, Long packId);

    Optional<CartItem> findByUserIdAndComboId(Long userId, Long comboId);

    void deleteByUserId(Long userId);

    long countByUserId(Long userId);
}
