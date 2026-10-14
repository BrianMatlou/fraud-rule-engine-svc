package za.co.capitecbank.service.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import za.co.capitecbank.domain.model.CustomRuleType;
import za.co.capitecbank.domain.model.EvalMode;
import za.co.capitecbank.dto.CreateRuleRequestDto;
import za.co.capitecbank.service.evaluator.ImpossibleTravelRuleEvaluator;
import za.co.capitecbank.service.evaluator.RuleEvaluator;
import za.co.capitecbank.service.evaluator.SpelRuleEvaluator;
import za.co.capitecbank.service.evaluator.VelocityRuleEvaluator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RuleConditionValidatorTest {

    private ObjectMapper objectMapper;
    private RuleConditionValidator validator;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        RuleEvaluator spelEvaluator = new SpelRuleEvaluator(objectMapper);
        RuleEvaluator velocityEvaluator = new VelocityRuleEvaluator(
                mock(za.co.capitecbank.domain.repo.CheckRepository.class), objectMapper);
        RuleEvaluator travelEvaluator = new ImpossibleTravelRuleEvaluator(
                mock(za.co.capitecbank.domain.repo.CheckRepository.class), objectMapper);

        validator = new RuleConditionValidator(
                List.of(spelEvaluator, velocityEvaluator, travelEvaluator), objectMapper);
        ReflectionTestUtils.setField(validator, "testRequest",
                new ClassPathResource("validation/test-fraud-check-request.json"));
    }

    @Test
    void validate_passes_forEvaluatorModeWithRegisteredVelocityType() {
        CreateRuleRequestDto request = new CreateRuleRequestDto(
                "Velocity Rule", "Detects burst activity",
                CustomRuleType.VELOCITY.name(), EvalMode.EVALUATOR,
                40, objectMapper.createObjectNode()
        );

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void validate_passes_forEvaluatorModeWithImpossibleTravelType() {
        CreateRuleRequestDto request = new CreateRuleRequestDto(
                "Travel Rule", "Detects impossible travel",
                CustomRuleType.IMPOSSIBLE_TRAVEL.name(), EvalMode.EVALUATOR,
                50, objectMapper.createObjectNode()
        );

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void validate_throwsIllegalStateException_forEvaluatorModeWithUnknownRuleType() {
        CreateRuleRequestDto request = new CreateRuleRequestDto(
                "Unknown Rule", "No evaluator registered",
                "UNKNOWN_CUSTOM_TYPE", EvalMode.EVALUATOR,
                30, objectMapper.createObjectNode()
        );

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No evaluator registered for ruleType");
    }

    @Test
    void validate_passes_forSpelModeWithValidBooleanExpression() {
        ObjectNode conditions = objectMapper.createObjectNode();
        conditions.put("expression", "amount != null");

        CreateRuleRequestDto request = new CreateRuleRequestDto(
                "Amount Null Check", "Checks amount presence",
                "AMOUNT_CHECK", EvalMode.SPEL, 25, conditions
        );

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void validate_throws_whenSpelConditionsNodeIsNull() {
        CreateRuleRequestDto request = new CreateRuleRequestDto(
                "Null Cond Rule", "Missing conditions",
                "AMOUNT_CHECK", EvalMode.SPEL, 25, objectMapper.nullNode()
        );

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Conditions expression must be provided");
    }

    @Test
    void validate_throws_whenExpressionKeyMissingFromConditions() {
        ObjectNode conditions = objectMapper.createObjectNode();
        conditions.put("someOtherKey", "value");

        CreateRuleRequestDto request = new CreateRuleRequestDto(
                "Missing Expr", "No expression key",
                "AMOUNT_CHECK", EvalMode.SPEL, 25, conditions
        );

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'expression' key");
    }

    @Test
    void validate_throws_forInvalidSpelSyntax() {
        ObjectNode conditions = objectMapper.createObjectNode();
        conditions.put("expression", "amount >>>"); // malformed SpEL

        CreateRuleRequestDto request = new CreateRuleRequestDto(
                "Bad Syntax Rule", "Invalid SpEL expression",
                "AMOUNT_CHECK", EvalMode.SPEL, 25, conditions
        );

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid expression");
    }

    @Test
    void validate_throws_whenExpressionReturnsNonBooleanValue() {
        // 'transactionId' evaluates to a String on the test transaction — not a Boolean
        ObjectNode conditions = objectMapper.createObjectNode();
        conditions.put("expression", "transactionId");

        CreateRuleRequestDto request = new CreateRuleRequestDto(
                "Non-Bool Rule", "Returns a string, not a boolean",
                "AMOUNT_CHECK", EvalMode.SPEL, 25, conditions
        );

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boolean");
    }
}
