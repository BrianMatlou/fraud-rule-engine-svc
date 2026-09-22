package za.co.capitecbank.mapper;


import lombok.experimental.UtilityClass;
import za.co.capitecbank.domain.entity.FraudCheckRequestEntity;
import za.co.capitecbank.domain.model.FraudOutcome;
import za.co.capitecbank.dto.TransactionEventDto;
import za.co.capitecbank.dto.FraudCheckResponse;

import java.util.List;

@UtilityClass
public class FraudCheckMapper {

    public FraudCheckRequestEntity buildFraudCheckRequestEntity(TransactionEventDto requestDto, FraudOutcome outcome, int riskScore, List<String> reasons) {
        return FraudCheckRequestEntity.builder()
                .transactionId(requestDto.transactionId())
                .amount(requestDto.amount())
                .currency(requestDto.currency())
                .accountId(requestDto.accountId())
                .merchantId(requestDto.merchantId())
                .transactionType(requestDto.transactionType())
                .transactionTimestamp(requestDto.transactionTimestamp())
                .country(requestDto.country())
                .city(requestDto.city())
                .latitude(requestDto.latitude())
                .longitude(requestDto.longitude())
                .ipAddress(requestDto.ipAddress())
                .outcome(outcome)
                .riskScore(riskScore)
                .reasons(reasons)
                .build();
    }

    public FraudCheckResponse buildFraudCheckResponse(FraudCheckRequestEntity entity) {
        return FraudCheckResponse.builder()
                .checkId(entity.getId())
                .transactionId(entity.getTransactionId())
                .outcome(entity.getOutcome())
                .riskScore(entity.getRiskScore())
                .reasons(entity.getReasons())
                .checkedAt(entity.getCreateDateTime())
                .build();
    }
}
