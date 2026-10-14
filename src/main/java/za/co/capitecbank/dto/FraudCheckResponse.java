package za.co.capitecbank.dto;

import lombok.Builder;
import za.co.capitecbank.domain.model.FraudOutcome;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record FraudCheckResponse(
    UUID checkId,
    String transactionId,
    FraudOutcome outcome,
    Integer riskScore,
    List<String> reasons,
    OffsetDateTime checkedAt
) {}
