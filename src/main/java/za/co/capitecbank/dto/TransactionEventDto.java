package za.co.capitecbank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionEventDto(
    @NotBlank String transactionId,
    @NotNull @Positive BigDecimal amount,
    @NotBlank String currency,
    @NotBlank String accountId,
    String merchantId,
    String transactionType,
    @NotNull OffsetDateTime transactionTimestamp,
    String country,
    String city,
    Double latitude,
    Double longitude,
    String ipAddress
) {}
