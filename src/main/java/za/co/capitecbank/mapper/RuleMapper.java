package za.co.capitecbank.mapper;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.capitecbank.domain.entity.RuleEntity;
import za.co.capitecbank.dto.CreateRuleRequestDto;
import za.co.capitecbank.dto.RuleResponseDto;

@Component
@RequiredArgsConstructor
public class RuleMapper {

    private final ObjectMapper objectMapper;

    public RuleEntity toEntity(CreateRuleRequestDto request) {
        return RuleEntity.builder()
                .name(request.name())
                .description(request.description())
                .ruleType(request.ruleType())
                .riskScoreImpact(request.riskScoreImpact())
                .active(true)
                .conditions(toConditionsString(request.conditions()))
                .build();
    }

    public RuleResponseDto toResponse(RuleEntity entity) {
        return new RuleResponseDto(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getRuleType(),
                entity.getRiskScoreImpact(),
                entity.getActive(),
                toConditionsNode(entity.getConditions()),
                entity.getCreatedDateTime(),
                entity.getUpdatedDateTime()
        );
    }

    public String toConditionsString(JsonNode node) {
        if (node == null) return null;
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid conditions JSON", e);
        }
    }

    private JsonNode toConditionsNode(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            return objectMapper.getNodeFactory().textNode(json);
        }
    }
}