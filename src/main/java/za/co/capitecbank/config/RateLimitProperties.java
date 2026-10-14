package za.co.capitecbank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fraud-check.rate-limit")
public record RateLimitProperties(BucketConfig fraudCheck, BucketConfig rules) {

    public record BucketConfig(int capacity, int refillTokens, int refillPeriodSeconds) {}
}
