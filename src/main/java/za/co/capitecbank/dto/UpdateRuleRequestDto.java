package za.co.capitecbank.dto;

import tools.jackson.databind.JsonNode;

public record UpdateRuleRequestDto(
    String name,
    String description,
    Integer riskScoreImpact,
    Boolean active,
    JsonNode conditions
) {}
