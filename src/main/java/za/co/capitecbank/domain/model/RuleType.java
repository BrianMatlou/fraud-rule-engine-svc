package za.co.capitecbank.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RuleType {
    AMOUNT_THRESHOLD(EvalMode.SPEL),
    COUNTRY_BLACKLIST(EvalMode.SPEL),
    VELOCITY(EvalMode.EVALUATOR),
    IMPOSSIBLE_TRAVEL(EvalMode.EVALUATOR);

    private final EvalMode mode;
}