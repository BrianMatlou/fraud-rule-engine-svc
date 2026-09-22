package za.co.capitecbank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;
import za.co.capitecbank.domain.model.EvalMode;


public record CreateRuleRequestDto(
    @NotBlank String name,
    @NotBlank String description,
    @NotBlank String ruleType,
    @NotNull EvalMode evalMode,
    @NotNull Integer riskScoreImpact,
    @NotNull JsonNode conditions
) {}