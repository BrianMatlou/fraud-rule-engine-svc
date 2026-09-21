package za.co.capitecbank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;
import za.co.capitecbank.domain.model.RuleType;


public record CreateRuleRequestDto(
    @NotBlank String name,
    @NotBlank String description,
    @NotNull RuleType ruleType,
    @NotNull Integer riskScoreImpact,
    @NotNull JsonNode conditions
) {}