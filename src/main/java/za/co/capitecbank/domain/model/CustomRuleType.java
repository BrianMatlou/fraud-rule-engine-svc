package za.co.capitecbank.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CustomRuleType {
    VELOCITY(EvalMode.EVALUATOR),
    IMPOSSIBLE_TRAVEL(EvalMode.EVALUATOR);

    private final EvalMode mode;
}