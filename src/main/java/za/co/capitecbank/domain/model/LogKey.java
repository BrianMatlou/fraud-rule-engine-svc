package za.co.capitecbank.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LogKey {
    TRANSACTION_ID("transaction-id"),
    RULE_ID("rule-id");

    private final String key;
}
