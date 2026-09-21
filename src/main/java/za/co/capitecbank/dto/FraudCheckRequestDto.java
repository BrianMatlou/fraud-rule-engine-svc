package za.co.capitecbank.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FraudCheckRequestDto(
    String transactionId,
    BigDecimal amount,
    String currency,
    String accountId,
    String merchantId,
    String transactionType,
    OffsetDateTime transactionTimestamp,
    String country,
    String city,
    Double latitude,
    Double longitude,
    String ipAddress
) {}
