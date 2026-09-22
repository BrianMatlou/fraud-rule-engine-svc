package za.co.capitecbank.service.evaluator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import za.co.capitecbank.domain.entity.RuleEntity;
import za.co.capitecbank.domain.model.EvalMode;
import za.co.capitecbank.domain.model.CustomRuleType;
import za.co.capitecbank.domain.repo.CheckRepository;
import za.co.capitecbank.dto.TransactionEventDto;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/*
 * Velocity Evaluator — detects burst transaction activity per account.
 *
 * How it works:
 *   1. Optionally filters by transaction type — if `transactionTypes` is set in the rule
 *      conditions, only transactions whose type appears in that list are evaluated.
 *      Transactions outside the list score 0 without hitting the database.
 *   2. Computes a rolling look-back window: windowStart = transactionTimestamp - windowMinutes.
 *   3. Counts all previously persisted fraud-check records for the same accountId whose
 *      transactionTimestamp falls within [windowStart, now). The current transaction is NOT
 *      yet persisted at evaluation time (FraudCheckService saves after all evaluators run),
 *      so the count reflects only completed prior transactions.
 *   4. Fires (returns riskScoreImpact) when count >= maxTransactions, meaning the account
 *      has already reached the threshold and this transaction exceeds it.
 *
 * Required conditions JSON fields:
 *   - windowMinutes   (integer, > 0) : rolling look-back period in minutes
 *   - maxTransactions (integer, > 0) : upper bound; rule fires when prior count meets this value
 *
 * Optional conditions JSON fields:
 *   - transactionTypes (array of strings) : limit evaluation to specific transaction types,
 *     e.g. ["ATM_WITHDRAWAL", "WITHDRAWAL"]. Omit or leave empty to apply to all types.
 *
 * Example conditions:
 *   {"windowMinutes": 60, "maxTransactions": 5, "transactionTypes": ["ATM_WITHDRAWAL"]}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VelocityRuleEvaluator implements RuleEvaluator {

    private final CheckRepository checkRepository;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(EvalMode evalMode, String ruleType) {
        return EvalMode.EVALUATOR == evalMode && CustomRuleType.VELOCITY.name().equals(ruleType);
    }

    @Override
    public int evaluate(RuleEntity rule, TransactionEventDto request) {
        Map<String, Object> conditions;
        try {
            conditions = objectMapper.readValue(rule.getConditions(), new TypeReference<>() {});
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid conditions JSON for rule '" + rule.getName() + "'", e);
        }

        List<String> transactionTypes = extractTransactionTypes(conditions);
        if (!transactionTypes.isEmpty() && !transactionTypes.contains(request.transactionType().name())) {
            log.debug("Velocity rule '{}': skipped — transactionType [{}] not in scope {}",
                    rule.getName(), request.transactionType(), transactionTypes);
            return 0;
        }

        int windowMinutes = parseIntRequired(conditions, "windowMinutes", rule.getName());
        int maxTransactions = parseIntRequired(conditions, "maxTransactions", rule.getName());

        if (windowMinutes <= 0 || maxTransactions <= 0) {
            throw new IllegalArgumentException(
                    "Rule '" + rule.getName() + "': windowMinutes and maxTransactions must be positive");
        }

        OffsetDateTime windowStart = request.transactionTimestamp().minusMinutes(windowMinutes);
        long count = checkRepository.countByAccountIdInWindow(request.accountId(), windowStart);

        log.debug("Velocity check account [{}]: {} txns in last {}min (max: {})",
                request.accountId(), count, windowMinutes, maxTransactions);

        if (count >= maxTransactions) {
            log.warn("Velocity rule '{}' triggered for account [{}]: {} txns in {}min window",
                    rule.getName(), request.accountId(), count, windowMinutes);
            return rule.getRiskScoreImpact();
        }
        return 0;
    }

    private List<String> extractTransactionTypes(Map<String, Object> conditions) {
        Object raw = conditions.get("transactionTypes");
        if (raw == null) return List.of();
        if (raw instanceof Collection<?> col) {
            return col.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private int parseIntRequired(Map<String, Object> conditions, String key, String ruleName) {
        Object value = conditions.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "Rule '" + ruleName + "' missing required conditions key: '" + key + "'");
        }
        try {
            return ((Number) value).intValue();
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(
                    "Rule '" + ruleName + "': conditions key '" + key + "' must be a number", e);
        }
    }
}