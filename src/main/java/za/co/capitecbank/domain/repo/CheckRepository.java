package za.co.capitecbank.domain.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.capitecbank.domain.entity.FraudCheckRequestEntity;

import java.util.UUID;

public interface CheckRepository extends JpaRepository<FraudCheckRequestEntity, UUID> {
}
