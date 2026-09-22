package za.co.capitecbank.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import za.co.capitecbank.domain.entity.RuleEntity;
import za.co.capitecbank.domain.model.EvalMode;
import za.co.capitecbank.domain.repo.RuleRepository;
import za.co.capitecbank.dto.CreateRuleRequestDto;
import za.co.capitecbank.dto.RuleResponseDto;
import za.co.capitecbank.dto.UpdateRuleRequestDto;
import za.co.capitecbank.mapper.RuleMapper;
import za.co.capitecbank.service.validator.RuleConditionValidator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@ExtendWith(MockitoExtension.class)
class RuleServiceTest {

    @Mock private RuleConditionValidator validator;
    @Mock private RuleRepository ruleRepository;
    @Mock private RuleMapper ruleMapper;

    @InjectMocks
    private RuleService service;

    @Test
    void createRule_validatesConditionsAndPersists() {
        CreateRuleRequestDto request = mock(CreateRuleRequestDto.class);
        RuleEntity entity = ruleEntity();
        RuleResponseDto dto = ruleResponseDto(entity.getId());

        doNothing().when(validator).validate(request);
        when(ruleMapper.toEntity(request)).thenReturn(entity);
        when(ruleRepository.save(entity)).thenReturn(entity);
        when(ruleMapper.toResponse(entity)).thenReturn(dto);

        RuleResponseDto result = service.createRule(request);

        assertThat(result.id()).isEqualTo(entity.getId());
        verify(validator).validate(request);
        verify(ruleRepository).save(entity);
    }

    @Test
    void getAllRules_returnsOnlyActiveRulesMapped() {
        RuleEntity entity1 = ruleEntity();
        RuleEntity entity2 = ruleEntity();
        RuleResponseDto dto1 = ruleResponseDto(entity1.getId());
        RuleResponseDto dto2 = ruleResponseDto(entity2.getId());

        when(ruleRepository.findByActiveTrue()).thenReturn(List.of(entity1, entity2));
        when(ruleMapper.toResponse(entity1)).thenReturn(dto1);
        when(ruleMapper.toResponse(entity2)).thenReturn(dto2);

        List<RuleResponseDto> result = service.getAllRules();

        assertThat(result).hasSize(2).containsExactly(dto1, dto2);
    }

    @Test
    void getRuleById_returnsDto_whenRuleExists() {
        UUID id = UUID.randomUUID();
        RuleEntity entity = ruleEntity();
        RuleResponseDto dto = ruleResponseDto(id);

        when(ruleRepository.findById(id)).thenReturn(Optional.of(entity));
        when(ruleMapper.toResponse(entity)).thenReturn(dto);

        RuleResponseDto result = service.getRuleById(id);

        assertThat(result.id()).isEqualTo(id);
    }

    @Test
    void getRuleById_throwsNotFound_whenRuleMissing() {
        UUID id = UUID.randomUUID();
        when(ruleRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRuleById(id))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(NOT_FOUND));
    }

    @Test
    void updateRule_updatesProvidedFieldsAndSaves() {
        UUID id = UUID.randomUUID();
        RuleEntity entity = ruleEntity();
        UpdateRuleRequestDto request = new UpdateRuleRequestDto("Updated Name", "New desc", 60, false, null);
        RuleResponseDto dto = ruleResponseDto(id);

        when(ruleRepository.findById(id)).thenReturn(Optional.of(entity));
        when(ruleRepository.save(entity)).thenReturn(entity);
        when(ruleMapper.toResponse(entity)).thenReturn(dto);

        service.updateRule(id, request);

        ArgumentCaptor<RuleEntity> captor = ArgumentCaptor.forClass(RuleEntity.class);
        verify(ruleRepository).save(captor.capture());

        RuleEntity saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Updated Name");
        assertThat(saved.getDescription()).isEqualTo("New desc");
        assertThat(saved.getRiskScoreImpact()).isEqualTo(60);
        assertThat(saved.getActive()).isFalse();
    }

    @Test
    void updateRule_doesNotOverwriteFields_whenRequestHasNullValues() {
        UUID id = UUID.randomUUID();
        RuleEntity entity = ruleEntityWithValues("Original Name", "Original desc", 30);
        UpdateRuleRequestDto request = new UpdateRuleRequestDto(null, null, null, null, null);
        RuleResponseDto dto = ruleResponseDto(id);

        when(ruleRepository.findById(id)).thenReturn(Optional.of(entity));
        when(ruleRepository.save(entity)).thenReturn(entity);
        when(ruleMapper.toResponse(entity)).thenReturn(dto);

        service.updateRule(id, request);

        ArgumentCaptor<RuleEntity> captor = ArgumentCaptor.forClass(RuleEntity.class);
        verify(ruleRepository).save(captor.capture());

        RuleEntity saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Original Name");
        assertThat(saved.getDescription()).isEqualTo("Original desc");
        assertThat(saved.getRiskScoreImpact()).isEqualTo(30);
    }

    @Test
    void updateRule_throwsNotFound_whenRuleMissing() {
        UUID id = UUID.randomUUID();
        when(ruleRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRule(id, new UpdateRuleRequestDto(null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(NOT_FOUND));
    }

    @Test
    void deleteRule_delegatesToRepository() {
        UUID id = UUID.randomUUID();

        service.deleteRule(id);

        verify(ruleRepository).deleteById(id);
    }

    // ---- helpers ----

    private static RuleEntity ruleEntity() {
        return RuleEntity.builder()
                .id(UUID.randomUUID())
                .name("Test Rule")
                .description("A test rule")
                .ruleType("AMOUNT_CHECK")
                .evalMode(EvalMode.SPEL)
                .riskScoreImpact(30)
                .active(true)
                .conditions("{\"expression\":\"amount > 100\"}")
                .build();
    }

    private static RuleEntity ruleEntityWithValues(String name, String description, int impact) {
        return RuleEntity.builder()
                .id(UUID.randomUUID())
                .name(name)
                .description(description)
                .ruleType("AMOUNT_CHECK")
                .evalMode(EvalMode.SPEL)
                .riskScoreImpact(impact)
                .active(true)
                .conditions("{\"expression\":\"amount > 100\"}")
                .build();
    }

    private static RuleResponseDto ruleResponseDto(UUID id) {
        return new RuleResponseDto(id, "Test Rule", "A test rule", "AMOUNT_CHECK",
                EvalMode.SPEL, 30, true, null, null, null);
    }
}
