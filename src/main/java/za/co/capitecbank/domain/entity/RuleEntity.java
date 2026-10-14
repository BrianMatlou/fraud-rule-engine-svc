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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import za.co.capitecbank.domain.model.EvalMode;

import java.time.OffsetDateTime;
import java.util.UUID;

import static java.time.ZoneOffset.UTC;

@Setter
@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fraud_rule")
@EntityListeners(AuditingEntityListener.class)
public class RuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String ruleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvalMode evalMode;

    @Column(nullable = false)
    private Integer riskScoreImpact;

    @Column(nullable = false)
    private Boolean active;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String conditions;

    @CreatedBy
    @Column(nullable = false, updatable = false)
    private String createdBy;

    private OffsetDateTime createdDateTime;

    private OffsetDateTime updatedDateTime;

    @PrePersist
    public void onCreate() {
        this.createdDateTime = OffsetDateTime.now(UTC);
        if (this.active == null) {
            this.active = true;
        }

        if (this.updatedDateTime == null) {
            this.updatedDateTime = this.createdDateTime;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedDateTime = OffsetDateTime.now(UTC);
    }
}