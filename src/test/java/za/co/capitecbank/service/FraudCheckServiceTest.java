package za.co.capitecbank.service;

import org.apache.kafka.common.errors.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.capitecbank.domain.entity.FraudCheckRequestEntity;
import za.co.capitecbank.domain.entity.RuleEntity;
import za.co.capitecbank.domain.model.EvalMode;
import za.co.capitecbank.domain.model.FraudOutcome;
import za.co.capitecbank.domain.model.TransactionType;
import za.co.capitecbank.domain.repo.CheckRepository;
import za.co.capitecbank.domain.repo.RuleRepository;
import za.co.capitecbank.dto.FraudCheckResponse;
import za.co.capitecbank.dto.TransactionEventDto;
import za.co.capitecbank.service.evaluator.RuleEvaluator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudCheckServiceTest {

    @Mock private RuleRepository ruleRepository;
    @Mock private CheckRepository checkRepository;
    @Mock private RuleEvaluator evaluator;

    private FraudCheckService service;

    private static final int HIGH_THRESHOLD = 70;
    private static final int MEDIUM_THRESHOLD = 40;

    @BeforeEach
    void setUp() {
        service = new FraudCheckService(List.of(evaluator), ruleRepository, checkRepository);
        ReflectionTestUtils.setField(service, "highScoreThreshold", HIGH_THRESHOLD);
        ReflectionTestUtils.setField(service, "mediumScoreThreshold", MEDIUM_THRESHOLD);
    }

    @Test
    void performCheck_returnsHighOutcome_whenScoreAtHighThreshold() {
        RuleEntity rule = ruleEntity(EvalMode.SPEL, "AMOUNT_CHECK");
        TransactionEventDto request = transactionEvent();
        FraudCheckRequestEntity saved = savedEntity("tx-001", FraudOutcome.HIGH, 70);

        when(ruleRepository.findByActiveTrue()).thenReturn(List.of(rule));
        when(evaluator.supports(EvalMode.SPEL, "AMOUNT_CHECK")).thenReturn(true);
        when(evaluator.evaluate(rule, request)).thenReturn(70);
        when(checkRepository.save(any())).thenReturn(saved);

        FraudCheckResponse response = service.performCheck(request);

        assertThat(response.outcome()).isEqualTo(FraudOutcome.HIGH);
        assertThat(response.riskScore()).isEqualTo(70);
    }

    @Test
    void performCheck_returnsMediumOutcome_whenScoreAboveMediumBelowHighThreshold() {
        RuleEntity rule = ruleEntity(EvalMode.SPEL, "VELOCITY_RULE");
        TransactionEventDto request = transactionEvent();
        FraudCheckRequestEntity saved = savedEntity("tx-001", FraudOutcome.MEDIUM, 50);

        when(ruleRepository.findByActiveTrue()).thenReturn(List.of(rule));
        when(evaluator.supports(EvalMode.SPEL, "VELOCITY_RULE")).thenReturn(true);
        when(evaluator.evaluate(rule, request)).thenReturn(50);
        when(checkRepository.save(any())).thenReturn(saved);

        FraudCheckResponse response = service.performCheck(request);

        assertThat(response.outcome()).isEqualTo(FraudOutcome.MEDIUM);
    }

    @Test
    void performCheck_returnsLowOutcome_whenScoreAtOrBelowMediumThreshold() {
        RuleEntity rule = ruleEntity(EvalMode.SPEL, "AMOUNT_CHECK");
        TransactionEventDto request = transactionEvent();
        FraudCheckRequestEntity saved = savedEntity("tx-001", FraudOutcome.LOW, 10);

        when(ruleRepository.findByActiveTrue()).thenReturn(List.of(rule));
        when(evaluator.supports(EvalMode.SPEL, "AMOUNT_CHECK")).thenReturn(true);
        when(evaluator.evaluate(rule, request)).thenReturn(10);
        when(checkRepository.save(any())).thenReturn(saved);

        FraudCheckResponse response = service.performCheck(request);

        assertThat(response.outcome()).isEqualTo(FraudOutcome.LOW);
    }

    @Test
    void performCheck_capsRiskScoreAt100_whenMultipleRulesExceedCap() {
        RuleEntity rule1 = ruleEntity(EvalMode.SPEL, "RULE_A");
        RuleEntity rule2 = ruleEntity(EvalMode.SPEL, "RULE_A");
        TransactionEventDto request = transactionEvent();
        FraudCheckRequestEntity saved = savedEntity("tx-001", FraudOutcome.HIGH, 100);

        when(ruleRepository.findByActiveTrue()).thenReturn(List.of(rule1, rule2));
        when(evaluator.supports(EvalMode.SPEL, "RULE_A")).thenReturn(true);
        when(evaluator.evaluate(any(RuleEntity.class), eq(request))).thenReturn(80);
        when(checkRepository.save(any())).thenReturn(saved);

        service.performCheck(request);

        verify(checkRepository).save(argThat(e -> e.getRiskScore() == 100));
    }

    @Test
    void performCheck_sumsScoresAcrossMultipleRules() {
        RuleEntity rule1 = ruleEntity(EvalMode.SPEL, "RULE_A");
        RuleEntity rule2 = ruleEntity(EvalMode.SPEL, "RULE_B");
        TransactionEventDto request = transactionEvent();
        FraudCheckRequestEntity saved = savedEntity("tx-001", FraudOutcome.HIGH, 75);

        when(ruleRepository.findByActiveTrue()).thenReturn(List.of(rule1, rule2));
        when(evaluator.supports(EvalMode.SPEL, "RULE_A")).thenReturn(true);
        when(evaluator.supports(EvalMode.SPEL, "RULE_B")).thenReturn(true);
        when(evaluator.evaluate(rule1, request)).thenReturn(45);
        when(evaluator.evaluate(rule2, request)).thenReturn(30);
        when(checkRepository.save(any())).thenReturn(saved);

        service.performCheck(request);

        verify(checkRepository).save(argThat(e -> e.getRiskScore() == 75));
    }

    @Test
    void performCheck_skipsRule_whenNoMatchingEvaluatorFound() {
        RuleEntity rule = ruleEntity(EvalMode.SPEL, "UNSUPPORTED_TYPE");
        TransactionEventDto request = transactionEvent();
        FraudCheckRequestEntity saved = savedEntity("tx-001", FraudOutcome.LOW, 0);

        when(ruleRepository.findByActiveTrue()).thenReturn(List.of(rule));
        when(evaluator.supports(EvalMode.SPEL, "UNSUPPORTED_TYPE")).thenReturn(false);
        when(checkRepository.save(any())).thenReturn(saved);

        service.performCheck(request);

        verify(evaluator, never()).evaluate(any(), any());
        verify(checkRepository).save(argThat(e -> e.getRiskScore() == 0));
    }

    @Test
    void performCheck_returnsLow_whenNoActiveRules() {
        TransactionEventDto request = transactionEvent();
        FraudCheckRequestEntity saved = savedEntity("tx-001", FraudOutcome.LOW, 0);

        when(ruleRepository.findByActiveTrue()).thenReturn(List.of());
        when(checkRepository.save(any())).thenReturn(saved);

        FraudCheckResponse response = service.performCheck(request);

        assertThat(response.outcome()).isEqualTo(FraudOutcome.LOW);
        verify(checkRepository).save(argThat(e -> e.getRiskScore() == 0));
    }

    @Test
    void findCheckResults_returnsResponse_whenTransactionFound() {
        String transactionId = "tx-123";
        FraudCheckRequestEntity entity = savedEntity(transactionId, FraudOutcome.HIGH, 80);

        when(checkRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(entity));

        FraudCheckResponse response = service.findCheckResults(transactionId);

        assertThat(response.transactionId()).isEqualTo(transactionId);
        assertThat(response.outcome()).isEqualTo(FraudOutcome.HIGH);
        assertThat(response.riskScore()).isEqualTo(80);
    }

    @Test
    void findCheckResults_throwsResourceNotFoundException_whenTransactionNotFound() {
        when(checkRepository.findByTransactionId("tx-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findCheckResults("tx-missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("tx-missing");
    }

    // ---- helpers ----

    private static RuleEntity ruleEntity(EvalMode mode, String type) {
        return RuleEntity.builder()
                .id(UUID.randomUUID())
                .name("Test Rule")
                .ruleType(type)
                .evalMode(mode)
                .riskScoreImpact(30)
                .active(true)
                .conditions("{}")
                .build();
    }

    private static TransactionEventDto transactionEvent() {
        return new TransactionEventDto(
                "tx-001", new BigDecimal("1500.00"), "ZAR",
                "ACC-001", "MER-001", TransactionType.PURCHASE,
                OffsetDateTime.now(), "ZA", "Johannesburg",
                -26.2041, 28.0473, "192.168.1.1"
        );
    }

    private static FraudCheckRequestEntity savedEntity(String txId, FraudOutcome outcome, int score) {
        return FraudCheckRequestEntity.builder()
                .id(UUID.randomUUID())
                .transactionId(txId)
                .outcome(outcome)
                .riskScore(score)
                .createDateTime(OffsetDateTime.now())
                .build();
    }
}
