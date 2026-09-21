package za.co.capitecbank.service.evaluator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import za.co.capitecbank.domain.entity.RuleEntity;
import za.co.capitecbank.domain.model.EvalMode;
import za.co.capitecbank.dto.TransactionEventDto;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpelRuleEvaluator implements RuleEvaluator {

    private final ObjectMapper objectMapper;

    private final SpelExpressionParser parser = new SpelExpressionParser();

    private final ConcurrentMap<UUID, Expression> cachedExpressions = new ConcurrentHashMap<>();


    @Override
    public boolean supports(EvalMode evalMode, String type) {
        return EvalMode.SPEL == evalMode;
    }


    @Override
    public int evaluate(final RuleEntity rule, final TransactionEventDto request) {
        try {
            Map<String, Object> conditions = this.objectMapper.readValue(
                    rule.getConditions(), new TypeReference<>() {}
            );

            String expression = conditions.get("expression").toString();
            if (expression == null || expression.isBlank()) {
                throw new IllegalArgumentException("Rule '" + rule.getName() + "' has no 'expression' key in conditions");
            }

            Expression compiledExpression = cachedExpressions.computeIfAbsent(rule.getId(), id -> parser.parseExpression(expression));

            SimpleEvaluationContext context = SimpleEvaluationContext
                    .forReadOnlyDataBinding()
                    .withRootObject(request)
                    .build();

            return Boolean.TRUE.equals(compiledExpression.getValue(context, Boolean.class)) ? rule.getRiskScoreImpact() : 0;
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid conditions JSON for rule '" + rule.getName() + "'", exception);
        } catch (EvaluationException exception) {
            log.error("SpEL evaluation failed for rule '{}': {}", rule.getName(), exception.getMessage());
            return 0;
        } catch (Exception exception) {
            throw new RuntimeException("Error evaluating rule '" + rule.getName() + "'", exception);
        }
    }
}
