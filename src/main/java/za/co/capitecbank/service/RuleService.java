package za.co.capitecbank.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import za.co.capitecbank.domain.entity.RuleEntity;
import za.co.capitecbank.domain.repo.RuleRepository;
import za.co.capitecbank.dto.CreateRuleRequestDto;
import za.co.capitecbank.dto.RuleResponseDto;
import za.co.capitecbank.dto.UpdateRuleRequestDto;
import za.co.capitecbank.mapper.RuleMapper;
import za.co.capitecbank.service.validator.SpelExpressionValidator;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RuleService {

    private final SpelExpressionValidator validator;
    private final RuleRepository ruleRepository;
    private final RuleMapper ruleMapper;

    public RuleResponseDto createRule(CreateRuleRequestDto request) {
        validator.validate(request);
        return ruleMapper.toResponse(ruleRepository.save(ruleMapper.toEntity(request)));
    }

    public List<RuleResponseDto> getAllRules() {
        return ruleRepository.findByActiveTrue().stream()
                .map(ruleMapper::toResponse)
                .toList();
    }

    public RuleResponseDto getRuleById(UUID id) {
        return ruleRepository.findById(id)
                .map(ruleMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found: " + id));
    }

    public RuleResponseDto updateRule(UUID id, UpdateRuleRequestDto request) {
        RuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found: " + id));

        if (request.name() != null) entity.setName(request.name());
        if (request.description() != null) entity.setDescription(request.description());
        if (request.riskScoreImpact() != null) entity.setRiskScoreImpact(request.riskScoreImpact());
        if (request.active() != null) entity.setActive(request.active());
        if (request.conditions() != null) entity.setConditions(ruleMapper.toConditionsString(request.conditions()));

        return ruleMapper.toResponse(ruleRepository.save(entity));
    }

    public void deleteRule(UUID id) {
        ruleRepository.deleteById(id);
    }
}
