package za.co.capitecbank.service.evaluator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import za.co.capitecbank.domain.entity.FraudCheckRequestEntity;
import za.co.capitecbank.domain.entity.RuleEntity;
import za.co.capitecbank.domain.model.EvalMode;
import za.co.capitecbank.domain.model.CustomRuleType;
import za.co.capitecbank.domain.repo.CheckRepository;
import za.co.capitecbank.dto.TransactionEventDto;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/*
 * Impossible Travel Evaluator — detects physically implausible movement between transactions.
 *
 * How it works:
 *   1. Optionally filters by transaction type — if `transactionTypes` is set in the rule
 *      conditions, only transactions whose type appears in that list are evaluated.
 *      Transactions outside the list score 0 without hitting the database.
 *   2. Guards against missing coordinates on the current transaction; returns 0 if absent.
 *   3. Fetches the most recent previously persisted transaction for the same accountId that
 *      occurred strictly before the current transactionTimestamp.
 *   4. Guards against missing coordinates on the prior transaction; returns 0 if absent.
 *   5. Calculates the great-circle distance between the two coordinate pairs using the
 *      Haversine formula (see haversineKm below).
 *   6. Calculates the elapsed time in seconds between the two transactions. Returns 0 if
 *      the delta is non-positive (handles out-of-order or replayed events safely).
 *   7. Derives the implied travel speed: distance (km) / time (hours).
 *   8. Fires (returns riskScoreImpact) when implied speed exceeds maxSpeedKmh.
 *
 * Optional conditions JSON fields:
 *   - maxSpeedKmh    (number,        default 900)  : km/h threshold above which travel is
 *                                                    considered impossible. 900 is just under
 *                                                    commercial aviation speed. Use 200 to flag
 *                                                    anything faster than road travel.
 *   - transactionTypes (array of strings)          : limit evaluation to specific types,
 *                                                    e.g. ["ATM_WITHDRAWAL"]. Omit to apply
 *                                                    to all transaction types.
 *
 * Example conditions:
 *   {"maxSpeedKmh": 900, "transactionTypes": ["ATM_WITHDRAWAL"]}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImpossibleTravelRuleEvaluator implements RuleEvaluator {

    private static final double DEFAULT_MAX_SPEED_KMH = 900.0;

    /*
     * Haversine formula reference:
     *   R. W. Sinnott, "Virtues of the Haversine", Sky and Telescope, vol. 68, no. 2, p. 159, 1984.
     *   https://en.wikipedia.org/wiki/Haversine_formula
     *
     * Earth radius used: 6371 km (mean radius per IUGG).
     * Accuracy: within ~0.3% for all distances; sufficient for fraud detection purposes.
     */
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final CheckRepository checkRepository;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(EvalMode evalMode, String ruleType) {
        return EvalMode.EVALUATOR == evalMode && CustomRuleType.IMPOSSIBLE_TRAVEL.name().equals(ruleType);
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
            log.debug("ImpossibleTravel rule '{}': skipped — transactionType [{}] not in scope {}",
                    rule.getName(), request.transactionType(), transactionTypes);
            return 0;
        }

        double maxSpeedKmh = conditions.containsKey("maxSpeedKmh")
                ? ((Number) conditions.get("maxSpeedKmh")).doubleValue()
                : DEFAULT_MAX_SPEED_KMH;

        if (request.latitude() == null || request.longitude() == null) {
            log.debug("ImpossibleTravel rule '{}': skipped — transaction [{}] has no coordinates",
                    rule.getName(), request.transactionId());
            return 0;
        }

        Optional<FraudCheckRequestEntity> priorOpt = checkRepository
                .findMostRecentByAccountIdBefore(request.accountId(), request.transactionTimestamp());

        if (priorOpt.isEmpty()) {
            log.debug("ImpossibleTravel rule '{}': no prior transaction for account [{}]",
                    rule.getName(), request.accountId());
            return 0;
        }

        FraudCheckRequestEntity prior = priorOpt.get();

        if (prior.getLatitude() == null || prior.getLongitude() == null) {
            log.debug("ImpossibleTravel rule '{}': prior transaction for account [{}] has no coordinates",
                    rule.getName(), request.accountId());
            return 0;
        }

        double distanceKm = haversineKm(
                prior.getLatitude(), prior.getLongitude(),
                request.latitude(), request.longitude()
        );

        long secondsDelta = Duration.between(
                prior.getTransactionTimestamp(), request.transactionTimestamp()).toSeconds();

        if (secondsDelta <= 0) {
            log.warn("ImpossibleTravel rule '{}': non-positive time delta ({}s) for account [{}]; skipping",
                    rule.getName(), secondsDelta, request.accountId());
            return 0;
        }

        double impliedSpeedKmh = distanceKm / (secondsDelta / 3600.0);

        log.debug("ImpossibleTravel account [{}]: distance={}km time={}h speed={}km/h (max: {})",
                request.accountId(),
                String.format("%.2f", distanceKm),
                String.format("%.4f", secondsDelta / 3600.0),
                String.format("%.2f", impliedSpeedKmh),
                maxSpeedKmh);

        if (impliedSpeedKmh > maxSpeedKmh) {
            log.warn("ImpossibleTravel rule '{}' triggered for account [{}]: {}km/h > {}km/h",
                    rule.getName(), request.accountId(),
                    String.format("%.2f", impliedSpeedKmh), maxSpeedKmh);
            return rule.getRiskScoreImpact();
        }
        return 0;
    }

    /*
     * Haversine formula — computes the great-circle distance between two points on a sphere.
     *
     * Steps:
     *   1. Convert latitude/longitude deltas from degrees to radians.
     *   2. Apply the Haversine identity:
     *        a = sin²(Δlat/2) + cos(lat1) · cos(lat2) · sin²(Δlon/2)
     *   3. Central angle: c = 2 · atan2(√a, √(1−a))
     *   4. Distance: d = R · c   (R = Earth's mean radius in km)
     *
     * Package-private to allow direct invocation in unit tests without reflection.
     */
    double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private List<String> extractTransactionTypes(Map<String, Object> conditions) {
        Object raw = conditions.get("transactionTypes");
        if (raw == null) return List.of();
        if (raw instanceof Collection<?> col) {
            return col.stream().map(Object::toString).toList();
        }
        return List.of();
    }
}