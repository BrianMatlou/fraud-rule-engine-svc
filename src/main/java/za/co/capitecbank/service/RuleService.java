package za.co.capitecbank.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import za.co.capitecbank.domain.entity.RuleEntity;
import za.co.capitecbank.domain.model.LogKey;
import za.co.capitecbank.domain.repo.RuleRepository;
import za.co.capitecbank.dto.CreateRuleRequestDto;
import za.co.capitecbank.dto.RuleResponseDto;
import za.co.capitecbank.dto.UpdateRuleRequestDto;
import za.co.capitecbank.mapper.RuleMapper;
import za.co.capitecbank.service.evaluator.SpelRuleEvaluator;
import za.co.capitecbank.service.validator.RuleConditionValidator;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RuleService {

    private final RuleConditionValidator validator;
    private final RuleRepository ruleRepository;
    private final RuleMapper ruleMapper;
    private final SpelRuleEvaluator spelRuleEvaluator;

    /** Validates and persists a new fraud detection rule. */
    public RuleResponseDto createRule(CreateRuleRequestDto request) {
        log.atInfo().log("Creating rule: {}", request.name());
        validator.validate(request);
        RuleResponseDto response = ruleMapper.toResponse(ruleRepository.save(ruleMapper.toEntity(request)));
        log.atInfo().addKeyValue(LogKey.RULE_ID.getKey(), response.id()).log("Rule created: {}", response.name());
        return response;
    }

    /** Retrieves all currently active fraud detection rules. */
    public List<RuleResponseDto> getAllRules() {
        log.atDebug().log("Fetching all active rules");
        return ruleRepository.findByActiveTrue().stream()
                .map(ruleMapper::toResponse)
                .toList();
    }

    /** Fetches a single rule by UUID, or throws 404 if not found. */
    public RuleResponseDto getRuleById(UUID id) {
        log.atDebug().addKeyValue(LogKey.RULE_ID.getKey(), id).log("Fetching rule by id");
        return ruleRepository.findById(id)
                .map(ruleMapper::toResponse)
                .orElseThrow(() -> {
                    log.warn("Rule not found: {}", id);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found: " + id);
                });
    }

    /** Applies a partial update to an existing rule. */
    public RuleResponseDto updateRule(UUID id, UpdateRuleRequestDto request) {
        log.atInfo().addKeyValue(LogKey.RULE_ID.getKey(), id).log("Updating rule");
        RuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Rule not found for update: {}", id);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found: " + id);
                });

        if (request.conditions() != null) {
            validator.validate(request.conditions(), entity.getEvalMode(), entity.getRuleType());
        }

        if (request.name() != null) entity.setName(request.name());
        if (request.description() != null) entity.setDescription(request.description());
        if (request.riskScoreImpact() != null) entity.setRiskScoreImpact(request.riskScoreImpact());
        if (request.active() != null) entity.setActive(request.active());
        if (request.conditions() != null) {
            entity.setConditions(ruleMapper.toConditionsString(request.conditions()));
            spelRuleEvaluator.evictCache(id);
        }

        RuleResponseDto response = ruleMapper.toResponse(ruleRepository.save(entity));
        log.atInfo().addKeyValue(LogKey.RULE_ID.getKey(), id).log("Rule updated: {}", response.name());
        return response;
    }

    /** Permanently removes the rule identified by the given UUID. */
    public void deleteRule(UUID id) {
        log.atInfo().addKeyValue(LogKey.RULE_ID.getKey(), id).log("Deleting rule");
        ruleRepository.deleteById(id);
        log.atInfo().addKeyValue(LogKey.RULE_ID.getKey(), id).log("Rule deleted");
    }
}
