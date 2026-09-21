package za.co.capitecbank.domain.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.capitecbank.domain.entity.FraudCheckRequestEntity;
import za.co.capitecbank.dto.FraudCheckResponse;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckRepository extends JpaRepository<FraudCheckRequestEntity, UUID> {
    Optional<FraudCheckRequestEntity> findByTransactionId(String transactionId);
}
