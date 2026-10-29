package in.mjmasala.api.repository;

import in.mjmasala.api.domain.Combo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ComboRepository extends JpaRepository<Combo, Long> {

    @EntityGraph(attributePaths = {"items", "items.product", "badges"})
    List<Combo> findByActiveTrueOrderBySortOrderAsc();

    /**
     * Detail view. {@code specs} is left lazy on purpose: with {@code items} also fetched
     * this would be two collection joins in one query, and Hibernate refuses that with
     * MultipleBagFetchException. One extra select on the detail page is the cheaper trade.
     */
    @EntityGraph(attributePaths = {"items", "items.product", "badges"})
    Optional<Combo> findBySlugAndActiveTrue(String slug);
}
