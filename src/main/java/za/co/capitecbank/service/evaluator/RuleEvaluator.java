package za.co.capitecbank.service.evaluator;

import za.co.capitecbank.domain.entity.RuleEntity;
import za.co.capitecbank.domain.model.EvalMode;
import za.co.capitecbank.dto.TransactionEventDto;

public interface RuleEvaluator {

    boolean supports(EvalMode evalMode, String ruleType);

    int evaluate(RuleEntity rule, TransactionEventDto request);
}
