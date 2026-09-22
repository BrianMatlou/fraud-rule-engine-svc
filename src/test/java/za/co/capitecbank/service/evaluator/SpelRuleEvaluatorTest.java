package za.co.capitecbank.service.evaluator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import za.co.capitecbank.domain.entity.RuleEntity;
import za.co.capitecbank.domain.model.EvalMode;
import za.co.capitecbank.domain.model.TransactionType;
import za.co.capitecbank.dto.TransactionEventDto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpelRuleEvaluatorTest {

    private SpelRuleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new SpelRuleEvaluator(new ObjectMapper());
    }

    @Test
    void supports_returnsTrue_forSpelMode() {
        assertThat(evaluator.supports(EvalMode.SPEL, "ANY_TYPE")).isTrue();
    }

    @Test
    void supports_returnsFalse_forEvaluatorMode() {
        assertThat(evaluator.supports(EvalMode.EVALUATOR, "VELOCITY")).isFalse();
    }

    @Test
    void evaluate_returnsRiskScoreImpact_whenExpressionEvaluatesToTrue() {
        RuleEntity rule = ruleWithExpression(50, "amount > 100");
        TransactionEventDto request = transactionEvent(new BigDecimal("500.00"));

        assertThat(evaluator.evaluate(rule, request)).isEqualTo(50);
    }

    @Test
    void evaluate_returnsZero_whenExpressionEvaluatesToFalse() {
        RuleEntity rule = ruleWithExpression(50, "amount > 1000");
        TransactionEventDto request = transactionEvent(new BigDecimal("200.00"));

        assertThat(evaluator.evaluate(rule, request)).isZero();
    }

    @Test
    void evaluate_throwsIllegalArgumentException_forInvalidConditionsJson() {
        RuleEntity rule = RuleEntity.builder()
                .id(UUID.randomUUID())
                .name("Bad JSON")
                .conditions("not-valid-json{{}}")
                .riskScoreImpact(30)
                .build();

        assertThatThrownBy(() -> evaluator.evaluate(rule, transactionEvent(BigDecimal.ONE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid conditions JSON");
    }

    @Test
    void evaluate_returnsZero_whenSpelEvaluationFails() {
        // References a non-existent property — EvaluationException is caught, returns 0
        RuleEntity rule = ruleWithExpression(40, "nonExistentField == 'x'");

        assertThat(evaluator.evaluate(rule, transactionEvent(BigDecimal.ONE))).isZero();
    }

    @Test
    void evaluate_cachesCompiledExpression_acrossConsecutiveCalls() {
        UUID ruleId = UUID.randomUUID();
        RuleEntity rule = RuleEntity.builder()
                .id(ruleId)
                .name("Cached Rule")
                .conditions("{\"expression\": \"amount > 0\"}")
                .riskScoreImpact(25)
                .build();
        TransactionEventDto request = transactionEvent(new BigDecimal("100.00"));

        int first = evaluator.evaluate(rule, request);
        int second = evaluator.evaluate(rule, request);

        assertThat(first).isEqualTo(25);
        assertThat(second).isEqualTo(25);
    }

    @Test
    void evaluate_handlesStringEquality_inExpression() {
        RuleEntity rule = ruleWithExpression(35, "currency == 'USD'");
        TransactionEventDto zarRequest = transactionEvent(BigDecimal.TEN);

        // ZAR transaction should not trigger USD rule
        assertThat(evaluator.evaluate(rule, zarRequest)).isZero();
    }

    // ---- helpers ----

    private static RuleEntity ruleWithExpression(int impact, String spelExpression) {
        return RuleEntity.builder()
                .id(UUID.randomUUID())
                .name("SpEL Rule")
                .conditions("{\"expression\": \"" + spelExpression + "\"}")
                .riskScoreImpact(impact)
                .build();
    }

    private static TransactionEventDto transactionEvent(BigDecimal amount) {
        return new TransactionEventDto(
                "tx-spel-01", amount, "ZAR",
                "ACC-001", "MER-001", TransactionType.PURCHASE,
                OffsetDateTime.now(), "ZA", "Cape Town",
                -33.9249, 18.4241, "127.0.0.1"
        );
    }
}
