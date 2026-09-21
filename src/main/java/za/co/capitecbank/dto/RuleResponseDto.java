package za.co.capitecbank.dto;

import tools.jackson.databind.JsonNode;
import za.co.capitecbank.domain.model.RuleType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RuleResponseDto(
    UUID id,
    String name,
    String description,
    RuleType ruleType,
    Integer riskScoreImpact,
    Boolean active,
    JsonNode conditions,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
