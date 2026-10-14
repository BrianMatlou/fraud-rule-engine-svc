CREATE TABLE fraud_check_request (
    id                    UUID          PRIMARY KEY,
    transaction_id        VARCHAR(255)  NOT NULL UNIQUE,
    create_date_time      TIMESTAMPTZ   NOT NULL,
    update_date_time      TIMESTAMPTZ   NOT NULL,
    created_by            VARCHAR(255)  NOT NULL,
    outcome               VARCHAR(50),
    amount                NUMERIC(19, 4),
    currency              VARCHAR(255),
    account_id            VARCHAR(255),
    merchant_id           VARCHAR(255),
    transaction_type      VARCHAR(255),
    transaction_timestamp TIMESTAMPTZ,
    country               VARCHAR(255),
    city                  VARCHAR(255),
    latitude              DOUBLE PRECISION,
    longitude             DOUBLE PRECISION,
    ip_address            VARCHAR(255),
    risk_score            INTEGER,
    reason                jsonb,
    payload               TEXT
);

CREATE INDEX idx_fraud_check_account_id            ON fraud_check_request (account_id);
CREATE INDEX idx_fraud_check_transaction_timestamp ON fraud_check_request (transaction_timestamp);

CREATE TABLE fraud_rule (
    id                UUID         PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    description       VARCHAR(255),
    rule_type         VARCHAR(255) NOT NULL,
    eval_mode         VARCHAR(255) NOT NULL,
    risk_score_impact INTEGER      NOT NULL,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    conditions        JSONB,
    created_by        VARCHAR(255) NOT NULL,
    created_date_time TIMESTAMPTZ,
    updated_date_time TIMESTAMPTZ
);

CREATE INDEX idx_fraud_rule_active ON fraud_rule (active);
