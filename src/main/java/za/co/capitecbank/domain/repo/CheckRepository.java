package za.co.capitecbank.domain.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.capitecbank.domain.entity.FraudCheckRequestEntity;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckRepository extends JpaRepository<FraudCheckRequestEntity, UUID> {

    Optional<FraudCheckRequestEntity> findByTransactionId(String transactionId);

    @Query("""
            SELECT COUNT(f) FROM FraudCheckRequestEntity f
            WHERE f.accountId = :accountId
              AND f.transactionTimestamp >= :windowStart
            """)
    long countByAccountIdInWindow(
            @Param("accountId") String accountId,
            @Param("windowStart") OffsetDateTime windowStart
    );

    @Query("""
            SELECT f FROM FraudCheckRequestEntity f
            WHERE f.accountId = :accountId
              AND f.transactionTimestamp < :before
            ORDER BY f.transactionTimestamp DESC
            LIMIT 1
            """)
    Optional<FraudCheckRequestEntity> findMostRecentByAccountIdBefore(
            @Param("accountId") String accountId,
            @Param("before") OffsetDateTime before
    );
}
