package in.mjmasala.api.repository;

import in.mjmasala.api.domain.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {

    /** The newest live code for a user and purpose — older ones are ignored. */
    Optional<OtpToken> findFirstByUserIdAndPurposeAndConsumedFalseOrderByIdDesc(Long userId, String purpose);

    List<OtpToken> findByUserIdAndPurposeAndConsumedFalse(Long userId, String purpose);

    long countByUserIdAndSentAtAfter(Long userId, Instant since);

    /** Used by the reset flow to invalidate everything outstanding for a user. */
    @Modifying
    @Query("update OtpToken t set t.consumed = true where t.user.id = :userId and t.consumed = false")
    void consumeAllForUser(@Param("userId") Long userId);

    /** Housekeeping: drop codes that expired a while ago. */
    @Modifying
    @Query("delete from OtpToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
