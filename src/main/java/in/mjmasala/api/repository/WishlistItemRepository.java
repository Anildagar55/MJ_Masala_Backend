package in.mjmasala.api.repository;

import in.mjmasala.api.domain.WishlistItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    @EntityGraph(attributePaths = {"product", "product.packs", "combo"})
    List<WishlistItem> findByUserIdOrderByIdDesc(Long userId);

    Optional<WishlistItem> findByIdAndUserId(Long id, Long userId);

    Optional<WishlistItem> findByUserIdAndProductId(Long userId, Long productId);

    Optional<WishlistItem> findByUserIdAndComboId(Long userId, Long comboId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndComboId(Long userId, Long comboId);
}
