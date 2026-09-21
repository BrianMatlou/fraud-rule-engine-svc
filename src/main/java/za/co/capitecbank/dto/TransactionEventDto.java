package za.co.capitecbank.dto;

import za.co.capitecbank.domain.model.TransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionEventDto(
    String transactionId,
    BigDecimal amount,
    String currency,
    String accountId,
    String merchantId,
    TransactionType transactionType,
    OffsetDateTime transactionTimestamp,
    String country,
    String city,
    Double latitude,
    Double longitude,
    String ipAddress
) {}
