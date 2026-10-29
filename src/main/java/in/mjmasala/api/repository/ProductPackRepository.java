package in.mjmasala.api.repository;

import in.mjmasala.api.domain.ProductPack;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductPackRepository extends JpaRepository<ProductPack, Long> {

    Optional<ProductPack> findBySku(String sku);

    /**
     * Batch fetch for a page of products. Loading packs per product would be one query per
     * card; this is one query for the whole page.
     */
    List<ProductPack> findByProductIdInAndActiveTrueOrderBySortOrderAsc(Collection<Long> productIds);
}
