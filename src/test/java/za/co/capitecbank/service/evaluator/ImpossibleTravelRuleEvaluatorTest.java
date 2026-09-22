package za.co.capitecbank.service.evaluator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import za.co.capitecbank.domain.entity.FraudCheckRequestEntity;
import za.co.capitecbank.domain.entity.RuleEntity;
import za.co.capitecbank.domain.model.CustomRuleType;
import za.co.capitecbank.domain.model.EvalMode;
import za.co.capitecbank.domain.model.TransactionType;
import za.co.capitecbank.domain.repo.CheckRepository;
import za.co.capitecbank.dto.TransactionEventDto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImpossibleTravelRuleEvaluatorTest {

    @Mock private CheckRepository checkRepository;

    private ImpossibleTravelRuleEvaluator evaluator;

    // Cape Town coordinates
    private static final double CT_LAT = -33.9249;
    private static final double CT_LON = 18.4241;

    // Johannesburg coordinates (~1265 km from Cape Town)
    private static final double JHB_LAT = -26.2041;
    private static final double JHB_LON = 28.0473;

    @BeforeEach
    void setUp() {
        evaluator = new ImpossibleTravelRuleEvaluator(checkRepository, new ObjectMapper());
    }

    @Test
    void supports_returnsTrue_forImpossibleTravelEvaluatorMode() {
        assertThat(evaluator.supports(EvalMode.EVALUATOR, CustomRuleType.IMPOSSIBLE_TRAVEL.name())).isTrue();
    }

    @Test
    void supports_returnsFalse_forSpelMode() {
        assertThat(evaluator.supports(EvalMode.SPEL, CustomRuleType.IMPOSSIBLE_TRAVEL.name())).isFalse();
    }

    @Test
    void supports_returnsFalse_forDifferentCustomRuleType() {
        assertThat(evaluator.supports(EvalMode.EVALUATOR, CustomRuleType.VELOCITY.name())).isFalse();
    }

    @Test
    void evaluate_returnsZero_whenNoPriorTransactionExists() {
        RuleEntity rule = impossibleTravelRule(50, 900);
        TransactionEventDto request = transactionEvent("ACC-001", CT_LAT, CT_LON, OffsetDateTime.now());

        when(checkRepository.findMostRecentByAccountIdBefore(eq("ACC-001"), any())).thenReturn(Optional.empty());

        assertThat(evaluator.evaluate(rule, request)).isZero();
    }

    @Test
    void evaluate_returnsZero_whenCurrentTransactionHasNoCoordinates() {
        RuleEntity rule = impossibleTravelRule(50, 900);
        TransactionEventDto request = transactionEventNoCoords("ACC-001");

        assertThat(evaluator.evaluate(rule, request)).isZero();
    }

    @Test
    void evaluate_returnsZero_whenPriorTransactionHasNoCoordinates() {
        RuleEntity rule = impossibleTravelRule(50, 900);
        OffsetDateTime now = OffsetDateTime.now();
        TransactionEventDto request = transactionEvent("ACC-001", CT_LAT, CT_LON, now);
        FraudCheckRequestEntity prior = priorEntity("ACC-001", null, null, now.minusMinutes(10));

        when(checkRepository.findMostRecentByAccountIdBefore(eq("ACC-001"), any())).thenReturn(Optional.of(prior));

        assertThat(evaluator.evaluate(rule, request)).isZero();
    }

    @Test
    void evaluate_returnsRiskScoreImpact_whenImpliedSpeedExceedsThreshold() {
        // Cape Town → Johannesburg (~1265 km) in 30 minutes → ~2530 km/h → exceeds 900 km/h
        RuleEntity rule = impossibleTravelRule(50, 900);
        OffsetDateTime priorTime = OffsetDateTime.now().minusMinutes(30);
        OffsetDateTime currentTime = OffsetDateTime.now();

        TransactionEventDto request = transactionEvent("ACC-001", JHB_LAT, JHB_LON, currentTime);
        FraudCheckRequestEntity prior = priorEntity("ACC-001", CT_LAT, CT_LON, priorTime);

        when(checkRepository.findMostRecentByAccountIdBefore(eq("ACC-001"), any())).thenReturn(Optional.of(prior));

        assertThat(evaluator.evaluate(rule, request)).isEqualTo(50);
    }

    @Test
    void evaluate_returnsZero_whenImpliedSpeedIsBelowThreshold() {
        // Cape Town → Johannesburg (~1265 km) in 2 hours → ~632 km/h → below 900 km/h
        RuleEntity rule = impossibleTravelRule(50, 900);
        OffsetDateTime priorTime = OffsetDateTime.now().minusHours(2);
        OffsetDateTime currentTime = OffsetDateTime.now();

        TransactionEventDto request = transactionEvent("ACC-001", JHB_LAT, JHB_LON, currentTime);
        FraudCheckRequestEntity prior = priorEntity("ACC-001", CT_LAT, CT_LON, priorTime);

        when(checkRepository.findMostRecentByAccountIdBefore(eq("ACC-001"), any())).thenReturn(Optional.of(prior));

        assertThat(evaluator.evaluate(rule, request)).isZero();
    }

    @Test
    void evaluate_returnsZero_whenTimeDeltaIsNonPositive() {
        RuleEntity rule = impossibleTravelRule(50, 900);
        OffsetDateTime ts = OffsetDateTime.now();

        TransactionEventDto request = transactionEvent("ACC-001", JHB_LAT, JHB_LON, ts);
        FraudCheckRequestEntity prior = priorEntity("ACC-001", CT_LAT, CT_LON, ts); // same timestamp

        when(checkRepository.findMostRecentByAccountIdBefore(eq("ACC-001"), any())).thenReturn(Optional.of(prior));

        assertThat(evaluator.evaluate(rule, request)).isZero();
    }

    @Test
    void evaluate_returnsZero_whenTransactionTypeNotInConfiguredScope() {
        RuleEntity rule = ruleWithTypes(50, 900, "ATM_WITHDRAWAL");
        TransactionEventDto request = transactionEvent("ACC-001", JHB_LAT, JHB_LON, OffsetDateTime.now());

        // PURCHASE is not in scope — should return 0 without hitting the DB
        assertThat(evaluator.evaluate(rule, request)).isZero();
    }

    @Test
    void evaluate_throwsIllegalArgumentException_forInvalidConditionsJson() {
        RuleEntity rule = ruleWithConditions("not-json");

        assertThatThrownBy(() -> evaluator.evaluate(rule, transactionEvent("ACC-001", CT_LAT, CT_LON, OffsetDateTime.now())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid conditions JSON");
    }

    @Test
    void haversineKm_computesReasonableDistance_betweenCapeTownAndJohannesburg() {
        double distance = evaluator.haversineKm(CT_LAT, CT_LON, JHB_LAT, JHB_LON);

        // Great-circle distance CT→JHB is approximately 1265 km (±20 km tolerance)
        assertThat(distance).isCloseTo(1265.0, within(20.0));
    }

    @Test
    void haversineKm_returnsZero_forSameCoordinates() {
        double distance = evaluator.haversineKm(CT_LAT, CT_LON, CT_LAT, CT_LON);

        assertThat(distance).isCloseTo(0.0, within(0.001));
    }

    // ---- helpers ----

    private static RuleEntity impossibleTravelRule(int impact, double maxSpeedKmh) {
        return ruleWithConditions(impact, String.format("{\"maxSpeedKmh\": %.0f}", maxSpeedKmh));
    }

    private static RuleEntity ruleWithTypes(int impact, double maxSpeedKmh, String... types) {
        String typesJson = "[\"" + String.join("\", \"", types) + "\"]";
        return ruleWithConditions(impact, String.format(
                "{\"maxSpeedKmh\": %.0f, \"transactionTypes\": %s}", maxSpeedKmh, typesJson));
    }

    private static RuleEntity ruleWithConditions(String conditions) {
        return ruleWithConditions(50, conditions);
    }

    private static RuleEntity ruleWithConditions(int impact, String conditions) {
        return RuleEntity.builder()
                .id(UUID.randomUUID())
                .name("Impossible Travel Rule")
                .ruleType(CustomRuleType.IMPOSSIBLE_TRAVEL.name())
                .evalMode(EvalMode.EVALUATOR)
                .riskScoreImpact(impact)
                .active(true)
                .conditions(conditions)
                .build();
    }

    private static TransactionEventDto transactionEvent(String accountId, double lat, double lon, OffsetDateTime ts) {
        return new TransactionEventDto(
                "tx-travel-01", new BigDecimal("500.00"), "ZAR",
                accountId, "MER-001", TransactionType.PURCHASE,
                ts, "ZA", "City", lat, lon, "10.0.0.1"
        );
    }

    private static TransactionEventDto transactionEventNoCoords(String accountId) {
        return new TransactionEventDto(
                "tx-travel-02", new BigDecimal("100.00"), "ZAR",
                accountId, "MER-001", TransactionType.PURCHASE,
                OffsetDateTime.now(), "ZA", "City", null, null, "10.0.0.2"
        );
    }

    private static FraudCheckRequestEntity priorEntity(String accountId, Double lat, Double lon, OffsetDateTime ts) {
        return FraudCheckRequestEntity.builder()
                .id(UUID.randomUUID())
                .transactionId("tx-prior-01")
                .accountId(accountId)
                .latitude(lat)
                .longitude(lon)
                .transactionTimestamp(ts)
                .createDateTime(ts)
                .build();
    }
}
