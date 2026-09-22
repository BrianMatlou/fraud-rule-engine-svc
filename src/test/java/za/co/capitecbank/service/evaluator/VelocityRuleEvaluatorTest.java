package za.co.capitecbank.service.evaluator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import za.co.capitecbank.domain.entity.RuleEntity;
import za.co.capitecbank.domain.model.CustomRuleType;
import za.co.capitecbank.domain.model.EvalMode;
import za.co.capitecbank.domain.model.TransactionType;
import za.co.capitecbank.domain.repo.CheckRepository;
import za.co.capitecbank.dto.TransactionEventDto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VelocityRuleEvaluatorTest {

    @Mock private CheckRepository checkRepository;

    private VelocityRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new VelocityRuleEvaluator(checkRepository, new ObjectMapper());
    }

    @Test
    void supports_returnsTrue_forVelocityEvaluatorMode() {
        assertThat(evaluator.supports(EvalMode.EVALUATOR, CustomRuleType.VELOCITY.name())).isTrue();
    }

    @Test
    void supports_returnsFalse_forSpelMode() {
        assertThat(evaluator.supports(EvalMode.SPEL, CustomRuleType.VELOCITY.name())).isFalse();
    }

    @Test
    void supports_returnsFalse_forDifferentCustomRuleType() {
        assertThat(evaluator.supports(EvalMode.EVALUATOR, CustomRuleType.IMPOSSIBLE_TRAVEL.name())).isFalse();
    }

    @Test
    void evaluate_returnsRiskScoreImpact_whenTransactionCountMeetsThreshold() {
        RuleEntity rule = velocityRule(40, 60, 5);
        TransactionEventDto request = transactionEvent("ACC-001", TransactionType.PURCHASE);

        when(checkRepository.countByAccountIdInWindow(eq("ACC-001"), any(OffsetDateTime.class))).thenReturn(5L);

        assertThat(evaluator.evaluate(rule, request)).isEqualTo(40);
    }

    @Test
    void evaluate_returnsRiskScoreImpact_whenTransactionCountExceedsThreshold() {
        RuleEntity rule = velocityRule(40, 60, 3);
        TransactionEventDto request = transactionEvent("ACC-001", TransactionType.PURCHASE);

        when(checkRepository.countByAccountIdInWindow(eq("ACC-001"), any(OffsetDateTime.class))).thenReturn(10L);

        assertThat(evaluator.evaluate(rule, request)).isEqualTo(40);
    }

    @Test
    void evaluate_returnsZero_whenTransactionCountBelowThreshold() {
        RuleEntity rule = velocityRule(40, 60, 5);
        TransactionEventDto request = transactionEvent("ACC-001", TransactionType.PURCHASE);

        when(checkRepository.countByAccountIdInWindow(eq("ACC-001"), any(OffsetDateTime.class))).thenReturn(2L);

        assertThat(evaluator.evaluate(rule, request)).isZero();
    }

    @Test
    void evaluate_returnsZero_whenTransactionTypeNotInConfiguredScope() {
        RuleEntity rule = velocityRuleWithTypes(40, 60, 3, "ATM_WITHDRAWAL");
        TransactionEventDto request = transactionEvent("ACC-001", TransactionType.PURCHASE);

        assertThat(evaluator.evaluate(rule, request)).isZero();
    }

    @Test
    void evaluate_evaluatesAllTypes_whenTransactionTypeFilterIsAbsent() {
        RuleEntity rule = velocityRule(40, 60, 3);
        TransactionEventDto request = transactionEvent("ACC-001", TransactionType.ATM_WITHDRAWAL);

        when(checkRepository.countByAccountIdInWindow(eq("ACC-001"), any(OffsetDateTime.class))).thenReturn(5L);

        assertThat(evaluator.evaluate(rule, request)).isEqualTo(40);
    }

    @Test
    void evaluate_throwsIllegalArgumentException_whenWindowMinutesMissing() {
        RuleEntity rule = ruleWithConditions("{\"maxTransactions\": 5}");
        TransactionEventDto request = transactionEvent("ACC-001", TransactionType.PURCHASE);

        assertThatThrownBy(() -> evaluator.evaluate(rule, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("windowMinutes");
    }

    @Test
    void evaluate_throwsIllegalArgumentException_whenMaxTransactionsMissing() {
        RuleEntity rule = ruleWithConditions("{\"windowMinutes\": 60}");
        TransactionEventDto request = transactionEvent("ACC-001", TransactionType.PURCHASE);

        assertThatThrownBy(() -> evaluator.evaluate(rule, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxTransactions");
    }

    @Test
    void evaluate_throwsIllegalArgumentException_forInvalidConditionsJson() {
        RuleEntity rule = ruleWithConditions("not-json");

        assertThatThrownBy(() -> evaluator.evaluate(rule, transactionEvent("ACC-001", TransactionType.PURCHASE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid conditions JSON");
    }

    @Test
    void evaluate_throwsIllegalArgumentException_whenWindowMinutesIsZero() {
        RuleEntity rule = ruleWithConditions("{\"windowMinutes\": 0, \"maxTransactions\": 5}");
        TransactionEventDto request = transactionEvent("ACC-001", TransactionType.PURCHASE);

        assertThatThrownBy(() -> evaluator.evaluate(rule, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    // ---- helpers ----

    private static RuleEntity velocityRule(int impact, int windowMinutes, int maxTransactions) {
        return ruleWithConditions(impact, String.format(
                "{\"windowMinutes\": %d, \"maxTransactions\": %d}", windowMinutes, maxTransactions));
    }

    private static RuleEntity velocityRuleWithTypes(int impact, int windowMinutes, int maxTransactions, String... types) {
        String typesJson = "[\"" + String.join("\", \"", types) + "\"]";
        return ruleWithConditions(impact, String.format(
                "{\"windowMinutes\": %d, \"maxTransactions\": %d, \"transactionTypes\": %s}",
                windowMinutes, maxTransactions, typesJson));
    }

    private static RuleEntity ruleWithConditions(String conditions) {
        return ruleWithConditions(40, conditions);
    }

    private static RuleEntity ruleWithConditions(int impact, String conditions) {
        return RuleEntity.builder()
                .id(UUID.randomUUID())
                .name("Velocity Rule")
                .ruleType(CustomRuleType.VELOCITY.name())
                .evalMode(EvalMode.EVALUATOR)
                .riskScoreImpact(impact)
                .active(true)
                .conditions(conditions)
                .build();
    }

    private static TransactionEventDto transactionEvent(String accountId, TransactionType type) {
        return new TransactionEventDto(
                "tx-vel-01", new BigDecimal("500.00"), "ZAR",
                accountId, "MER-001", type,
                OffsetDateTime.now(), "ZA", "Johannesburg",
                -26.2041, 28.0473, "10.0.0.1"
        );
    }
}
