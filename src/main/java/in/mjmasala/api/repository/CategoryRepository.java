package in.mjmasala.api.repository;

import in.mjmasala.api.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByActiveTrueOrderBySortOrderAsc();

    Optional<Category> findBySlug(String slug);
}
