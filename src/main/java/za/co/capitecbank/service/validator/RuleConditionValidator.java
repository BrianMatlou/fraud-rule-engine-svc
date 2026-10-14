package za.co.capitecbank.service.validator;

import org.springframework.core.io.Resource;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import za.co.capitecbank.domain.model.EvalMode;
import za.co.capitecbank.dto.CreateRuleRequestDto;
import za.co.capitecbank.dto.TransactionEventDto;
import za.co.capitecbank.service.evaluator.RuleEvaluator;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RuleConditionValidator {

    @Value("classpath:validation/test-fraud-check-request.json")
    private Resource testRequest;

    private final List<RuleEvaluator> evaluators;

    private final ObjectMapper objectMapper;

    private final SpelExpressionParser spelExpressionParser = new SpelExpressionParser();

    public void validate(CreateRuleRequestDto request) {
        validate(request.conditions(), request.evalMode(), request.ruleType());
    }

    public void validate(JsonNode conditions, EvalMode evalMode, String ruleType) {

        if (evalMode == EvalMode.EVALUATOR) {
            boolean hasEvaluator = evaluators != null && evaluators.stream()
                    .anyMatch(e -> e.supports(EvalMode.EVALUATOR, ruleType));
            if (!hasEvaluator) {
                throw new IllegalStateException(
                        "No evaluator registered for ruleType: " + ruleType
                );
            }
            return;
        }

        if (conditions == null || conditions.isNull()) {
            throw new IllegalStateException("Conditions expression must be provided for SpEL rules");
        }

        JsonNode expressionNode = conditions.get("expression");
        if (expressionNode == null) {
            throw new IllegalStateException("SpEL rules require a non-blank 'expression' key in conditions");
        }
        try {
            String expression = expressionNode.asText();
            Expression compiledExpression = spelExpressionParser.parseExpression(expression);

            SimpleEvaluationContext simpleEvaluationContext = SimpleEvaluationContext
                    .forReadOnlyDataBinding()
                    .withRootObject(buildTestRequest())
                    .build();

            Object result = compiledExpression.getValue(simpleEvaluationContext);

            if (result != null && !(result instanceof Boolean)) {
                throw new IllegalStateException("Expression must return a boolean value but returned: " + result.getClass().getName());
            }
        } catch (ParseException exception) {
            throw new IllegalStateException("Invalid expression in conditions: " + exception.getSimpleMessage(), exception);
        } catch (EvaluationException exception) {
            throw new IllegalStateException("Expression references unknown field or has a type error: " + exception.getSimpleMessage(), exception);
        }
    }

    private TransactionEventDto buildTestRequest() {
        try {
            return this.objectMapper.readValue(testRequest.getInputStream(), TransactionEventDto.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load test fraud request for SpEL validation", e);
        }

    }

}
