package za.co.capitecbank.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import za.co.capitecbank.domain.model.exception.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import za.co.capitecbank.domain.entity.FraudCheckRequestEntity;
import za.co.capitecbank.domain.entity.RuleEntity;
import za.co.capitecbank.domain.model.FraudOutcome;
import za.co.capitecbank.domain.model.LogKey;
import za.co.capitecbank.domain.repo.CheckRepository;
import za.co.capitecbank.domain.repo.RuleRepository;
import za.co.capitecbank.dto.FraudCheckResponse;
import za.co.capitecbank.dto.TransactionEventDto;
import za.co.capitecbank.mapper.FraudCheckMapper;
import za.co.capitecbank.service.evaluator.RuleEvaluator;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudCheckService {

    @Value("${fraud-check.high-score-threshold}")
    private Integer highScoreThreshold;

    @Value("${fraud-check.medium-score-threshold}")
    private  Integer mediumScoreThreshold;

    private final List<RuleEvaluator> evaluators;
    private final RuleRepository ruleRepository;
    private final CheckRepository checkRepository;

    /** Evaluates all active rules against the transaction and persists the fraud check result. */
    public FraudCheckResponse performCheck(TransactionEventDto request){
        log.atInfo().addKeyValue(LogKey.TRANSACTION_ID.getKey(), request.transactionId()).log("Starting fraud check");
        List<RuleEntity> activeRules = ruleRepository.findByActiveTrue();

        int totalScore = 0;
        List<String> reasons = new java.util.ArrayList<>();

        for (RuleEntity rule : activeRules) {
            RuleEvaluator evaluator = evaluators.stream()
                    .filter(e -> e.supports(rule.getEvalMode(), rule.getRuleType()))
                    .findFirst()
                    .orElse(null);

            if (evaluator == null) {
                log.warn("No evaluator for evalMode={} ruleType={}", rule.getEvalMode(), rule.getRuleType());
                continue;
            }

            int scoreContribution = evaluator.evaluate(rule, request);
            if (scoreContribution > 0) {
                reasons.add(rule.getName());
            }
            totalScore += scoreContribution;
            log.atDebug().addKeyValue("Transaction-Id", request.transactionId())
                    .log("Rule [{}] for transactionID [{}] evaluated to [{}]",
                    rule.getName(), request.transactionId() ,totalScore);
        }

        int cappedScore = Math.min(totalScore, 100);
        FraudOutcome outcome = calculateOutcome(cappedScore);
        FraudCheckRequestEntity fraudCheckRequest = checkRepository.save(FraudCheckMapper.buildFraudCheckRequestEntity(request, outcome, cappedScore, reasons));
        FraudCheckResponse response = FraudCheckMapper.buildFraudCheckResponse(fraudCheckRequest);
        log.atInfo().addKeyValue(LogKey.TRANSACTION_ID.getKey(), request.transactionId())
                .log("Fraud check completed: outcome={}, score={}", outcome, cappedScore);
        return response;
    }

    /** Looks up a previously stored fraud check result by transaction ID. */
    public FraudCheckResponse findCheckResults(final String transactionId) {
        log.atDebug().addKeyValue(LogKey.TRANSACTION_ID.getKey(), transactionId).log("Looking up fraud check result");
        return this.checkRepository.findByTransactionId(transactionId)
                .map(FraudCheckMapper::buildFraudCheckResponse)
                .orElseThrow(() -> new EntityNotFoundException("No fraud check found for transactionId: " + transactionId));
    }

    private FraudOutcome calculateOutcome(int totalScore){
       if (totalScore >= highScoreThreshold) return FraudOutcome.HIGH;
       if (totalScore > mediumScoreThreshold) return FraudOutcome.MEDIUM;
       return FraudOutcome.LOW;
    }


}
