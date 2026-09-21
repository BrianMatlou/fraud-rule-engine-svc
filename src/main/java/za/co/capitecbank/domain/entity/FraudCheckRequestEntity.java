package za.co.capitecbank.domain.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import za.co.capitecbank.domain.model.FraudOutcome;
import za.co.capitecbank.domain.model.TransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static java.time.ZoneOffset.UTC;

@Setter
@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fraud_check")
@EntityListeners(AuditingEntityListener.class)
public class FraudCheckRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String transactionId;

    @Column(nullable = false)
    private OffsetDateTime createDateTime;

    @Column(nullable = false)
    private OffsetDateTime updateDateTime;

    @CreatedBy
    @Column(nullable = false, updatable = false)
    private String createdBy;

    @Enumerated(EnumType.STRING)
    private FraudOutcome outcome;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    private String currency;

    private String accountId;

    private String merchantId;

    private String transactionType;

    private OffsetDateTime transactionTimestamp;

    private String country;

    private String city;

    private Double latitude;

    private Double longitude;

    private String ipAddress;

    private Integer riskScore;

    private String reason;

    private String payload;

    @PrePersist
    public void onCreate() {
        this.createDateTime = OffsetDateTime.now(UTC);
        if (this.updateDateTime == null) {
            this.updateDateTime = this.createDateTime;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updateDateTime = OffsetDateTime.now(UTC);
    }
}