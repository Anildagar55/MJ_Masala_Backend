package in.mjmasala.api.repository;

import in.mjmasala.api.domain.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * Detail view. The entity graph pulls the packs, badges and category in the same
     * query; {@code specs} is left lazy on purpose — two collection fetch joins in one
     * query would throw {@code MultipleBagFetchException}.
     */
    @EntityGraph(attributePaths = {"packs", "badges", "category"})
    Optional<Product> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    /** Rows of [productId, badge], fetched once for a whole page of cards. */
    @Query("select p.id, b from Product p join p.badges b where p.id in :ids")
    List<Object[]> badgesForIds(@Param("ids") Collection<Long> ids);
}
