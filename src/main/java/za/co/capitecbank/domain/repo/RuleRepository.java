package za.co.capitecbank.domain.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.capitecbank.domain.entity.RuleEntity;

import java.util.List;
import java.util.UUID;

public interface RuleRepository extends JpaRepository<RuleEntity, UUID> {

    List<RuleEntity> findByActiveTrue();
}
