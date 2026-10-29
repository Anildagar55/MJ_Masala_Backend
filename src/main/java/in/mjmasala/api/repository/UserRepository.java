package in.mjmasala.api.repository;

import in.mjmasala.api.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    /** Sign-in is by email, but a forgotten password may be started from either. */
    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByEmailIgnoreCase(String email);
}
