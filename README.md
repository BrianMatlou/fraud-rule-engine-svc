# Transaction Fraud Check Service

A production-grade Spring Boot service that processes transaction events, evaluates them against configurable fraud rules, and persists outcomes for retrieval.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Prerequisites](#prerequisites)
3. [Environment Variables](#environment-variables)
4. [Start Infrastructure](#start-infrastructure)
5. [Keycloak Setup](#keycloak-setup)
6. [Build and Run the Service](#build-and-run-the-service)
7. [Fraud Rules](#fraud-rules)
   - [Rule Types](#rule-types)
   - [Create a SpEL Rule](#create-a-spel-rule)
   - [Create an Evaluator Rule](#create-an-evaluator-rule)
   - [List / Get / Update / Delete Rules](#list--get--update--delete-rules)
8. [Send Transaction Events via Kafka](#send-transaction-events-via-kafka)
9. [Fraud Check Results API](#fraud-check-results-api)
10. [Scoring Logic](#scoring-logic)
11. [API Reference](#api-reference)
12. [Testing](#testing)
13. [Swagger UI](#swagger-ui)

---

## Architecture Overview

```
Upstream systems
      │
      ▼ Kafka topic: transaction-events
┌─────────────────────────────────────────┐
│  TransactionEventsConsumer              │
│  (x-actor-id header → AuditContext)     │
└────────────────┬────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────┐
│  FraudCheckService                      │
│  • loads active RuleEntity records      │
│  • dispatches each to RuleEvaluator     │
│  • sums risk scores (cap 100)           │
│  • persists FraudCheckRequestEntity     │
└────────────────┬────────────────────────┘
                 │
       ┌─────────┴──────────┐
       ▼                    ▼
  SpEL Rule Evaluators    Custom evaluators
  AMOUNT_THRESHOLD     VELOCITY
  COUNTRY_BLACKLIST    IMPOSSIBLE_TRAVEL
```

**Infrastructure:**

| Service      | Port  | Purpose                       |
|--------------|-------|-------------------------------|
| PostgreSQL   | 5432  | Fraud checks + Keycloak data  |
| Keycloak     | 9090  | OAuth2 / JWT issuer           |
| Kafka        | 9092  | Transaction event stream      |
| Kafka UI     | 8090  | Kafka topic browser           |
| Application  | 8080  | REST API                      |

---

## Prerequisites

- Java 25
- Maven (or use the included `./mvnw`)
- Docker and Docker Compose

---

## Environment Variables

Create `config/.env` — this file is read by Docker Compose and required before starting anything.

```bash
# config/.env
POSTGRES_DB=transaction_fraud
POSTGRES_USER=fraud_user
POSTGRES_PASSWORD=fraud_pass
```

For **local dev** (running the app with `./mvnw spring-boot:run` instead of Docker), also export:

```bash
export DATASOURCE_URL=jdbc:postgresql://localhost:5432/transaction_fraud
export DATASOURCE_USERNAME=fraud_user
export DATASOURCE_PASSWORD=fraud_pass
```

---

## Start Infrastructure

### Option A — Full Docker stack (recommended)

Starts PostgreSQL, Keycloak, Kafka, Kafka UI, **and the application** in one command:

```bash
docker compose -f config/docker-compose.yml --env-file config/.env up -d
```

Wait for all services to be healthy:

```bash
docker compose -f config/docker-compose.yml ps
```

All five services (`postgres`, `keycloak`, `kafka`, `kafka-ui`, `app`) must show `healthy` or `running`. The application is ready when `transaction-fraud-app` reports healthy.

### Option B — Infrastructure only (for local dev)

Starts only the backing services; run the application separately with Maven:

```bash
docker compose -f config/docker-compose.yml --env-file config/.env up -d postgres keycloak kafka kafka-ui
```

**Kafka UI** is available at [http://localhost:8090](http://localhost:8090) — use it to browse topics and publish test events.

---

## Keycloak Setup

The application validates JWTs issued by `http://localhost:9090/realms/capitec`. You need to create this realm and a client once after first boot.

> **Note:** Keycloak data is stored in PostgreSQL. The realm and client survive container restarts as long as the `postgres` volume is intact. If you run `docker compose down -v` (removes volumes), repeat this setup.

### 1. Create the realm

1. Open [http://localhost:9090](http://localhost:9090)
2. Log in with `admin` / `admin123`
3. Click **Create realm** → set **Realm name** to `capitec` → **Create**

---

### 2. Create a client

You can create the client via the UI or via the Admin REST API. The API approach is faster and scriptable.

#### Option A — Keycloak Admin UI

1. In the `capitec` realm, go to **Clients** → **Create client**
2. Fill in the following on **General Settings**:
   - **Client type**: `OpenID Connect`
   - **Client ID**: `fraud-service-client`
3. Click **Next** → on **Capability config**:
   - **Client authentication**: `ON`
   - **Authorization**: `OFF`
   - **Authentication flow**: uncheck everything except **Service accounts roles**
4. Click **Next** → **Save**
5. Go to the **Credentials** tab → copy the auto-generated **Client secret**

#### Option B — Admin REST API (one-shot, no browser)

Pick a secret you want to use (or generate one), then run:

```bash
# 1. Get an admin token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:9090/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=admin123" \
  | jq -r '.access_token')

# 2. Create the client with a specific secret
curl -s -X POST http://localhost:9090/admin/realms/capitec/clients \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "fraud-service-client",
    "enabled": true,
    "clientAuthenticatorType": "client-secret",
    "secret": "xWpjsAlEzGQyvpmaH8ssGGji1mLutHDK",
    "serviceAccountsEnabled": true,
    "standardFlowEnabled": false,
    "directAccessGrantsEnabled": true,
    "publicClient": false,
    "protocol": "openid-connect"
  }'
```

A `201 Created` response with an empty body confirms success.

**Verify the client exists:**

```bash
curl -s http://localhost:9090/admin/realms/capitec/clients \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  | jq '[.[] | {clientId, enabled, serviceAccountsEnabled}]'
```

`fraud-service-client` should appear with `"serviceAccountsEnabled": true`.

---

### 3. Assign roles to the client

Every JWT must carry at least one realm role. Roles control what the bearer can do:

| Role             | Who holds it                  | Permitted operations                              |
|------------------|-------------------------------|---------------------------------------------------|
| `FRAUD_ADMIN`    | Operations / admin users      | Create, update, delete rules; query check results |
| `FRAUD_ANALYST`  | Fraud analysts                | Read rules; query check results                   |
| `FRAUD_CONSUMER` | Upstream service accounts     | Query check results only                          |

`fraud-service-client` should hold `FRAUD_CONSUMER`. Assign it via the Admin REST API:

```bash
# Get an admin token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:9090/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=admin123" \
  | jq -r '.access_token')

# Create the FRAUD_CONSUMER realm role (skip if it already exists — 409 is fine)
curl -s -X POST http://localhost:9090/admin/realms/capitec/roles \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"FRAUD_CONSUMER","description":"Read-only access to fraud check results for service accounts"}'

# Get the fraud-service-client internal UUID
CLIENT_UUID=$(curl -s "http://localhost:9090/admin/realms/capitec/clients?clientId=fraud-service-client" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].id')

# Get the service account user UUID
SA_USER_ID=$(curl -s "http://localhost:9090/admin/realms/capitec/clients/$CLIENT_UUID/service-account-user" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.id')

# Get the FRAUD_CONSUMER role definition
ROLE=$(curl -s "http://localhost:9090/admin/realms/capitec/roles/FRAUD_CONSUMER" \
  -H "Authorization: Bearer $ADMIN_TOKEN")

# Assign the role to the service account
curl -s -X POST "http://localhost:9090/admin/realms/capitec/users/$SA_USER_ID/role-mappings/realm" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "[$(echo $ROLE | jq '{id,name}')]"
```

**Assign via UI instead:**
1. **Clients** → `fraud-service-client` → **Service accounts roles** tab
2. **Assign role** → filter by realm → select `FRAUD_CONSUMER` → **Assign**

To create additional human users, follow step 4 below.

---

### 4. Create human users

Human users authenticate with the **Password Credentials** grant and require `FRAUD_ADMIN` (full access) or `FRAUD_ANALYST` (read-only) roles. Service accounts use `FRAUD_CONSUMER` (step 3).

#### Admin REST API

```bash
# Get an admin token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:9090/realms/master/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=admin123" \
  | jq -r '.access_token')

# Create the FRAUD_ADMIN realm role (skip if it already exists — 409 is fine)
curl -s -X POST http://localhost:9090/admin/realms/capitec/roles \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"FRAUD_ADMIN","description":"Full access: create, update, delete rules and query check results"}'

# Create the FRAUD_ANALYST realm role (skip if it already exists — 409 is fine)
curl -s -X POST http://localhost:9090/admin/realms/capitec/roles \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"FRAUD_ANALYST","description":"Read-only access to rules and fraud check results"}'

# Create user brian (FRAUD_ADMIN)
curl -s -X POST http://localhost:9090/admin/realms/capitec/users \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "brian",
    "firstName": "Brian",
    "lastName": "Matlou",
    "email": "brian@capitecbank.co.za",
    "enabled": true,
    "emailVerified": true,
    "credentials": [{ "type": "password", "value": "admin", "temporary": false }]
  }'

# Get the user UUID
USER_ID=$(curl -s "http://localhost:9090/admin/realms/capitec/users?username=brian" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].id')

# Get the FRAUD_ADMIN role definition
ROLE=$(curl -s "http://localhost:9090/admin/realms/capitec/roles/FRAUD_ADMIN" \
  -H "Authorization: Bearer $ADMIN_TOKEN")

# Assign FRAUD_ADMIN to the user
curl -s -X POST "http://localhost:9090/admin/realms/capitec/users/$USER_ID/role-mappings/realm" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "[$(echo $ROLE | jq '{id,name}')]"
```

A `204 No Content` response on the final call confirms the role is assigned.

**For a read-only analyst**, repeat with `"username": "analyst"` and assign `FRAUD_ADMIN` → `FRAUD_ANALYST` in the role step.

#### UI alternative

1. **Users** → **Add user** → fill in username, first name, last name, email → toggle **Email verified** ON → **Create**
2. **Credentials** tab → **Set password** → enter password → toggle **Temporary** OFF → **Save**
3. **Role mappings** tab → **Assign role** → filter by realm → select `FRAUD_ADMIN` → **Assign**

---

### 5. Obtain a token

#### Service-to-service (client credentials)

Used by upstream services that hold the `FRAUD_CONSUMER` role.

```bash
export CLIENT_SECRET=xWpjsAlEzGQyvpmaH8ssGGji1mLutHDK   # or the secret from the UI Credentials tab

export TOKEN=$(curl -s -X POST http://localhost:9090/realms/capitec/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "client_id=fraud-service-client" \
  -d "client_secret=$CLIENT_SECRET" \
  | jq -r '.access_token')

echo $TOKEN | cut -c1-40
```

#### Human user (password credentials)

Used by `FRAUD_ADMIN` or `FRAUD_ANALYST` users who manage rules via the API or Postman.

```bash
export TOKEN=$(curl -s -X POST http://localhost:9090/realms/capitec/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=fraud-service-client" \
  -d "client_secret=xWpjsAlEzGQyvpmaH8ssGGji1mLutHDK" \
  -d "username=brian" \
  -d "password=admin" \
  | jq -r '.access_token')

echo $TOKEN | cut -c1-40
```

Tokens expire in **5 minutes**. Re-run the relevant block above to refresh before making API calls.

**Common errors:**

| Error | Cause | Fix |
|---|---|---|
| `invalid_client` | Client does not exist or secret is wrong | Re-run Option B in step 2, or check the Credentials tab in the UI |
| `Realm not found` | `capitec` realm was never created | Complete step 1 first |
| `null` from `jq` | Token request returned an error object | Remove `jq -r '.access_token'` and inspect the full response |
| `unauthorized_client` | `directAccessGrantsEnabled` is `false` | Re-create the client with `directAccessGrantsEnabled: true` (step 2) |
| `invalid_grant: Account is not fully set up` | User missing `firstName`, `lastName`, `email`, or `emailVerified` | Patch the user via Admin API (step 4) |
| `403 Forbidden` | Token lacks the required role | Verify role mapping (steps 3–4) |

---

## Build and Run the Service

### Docker (Option A — full stack already running)

If you started the full Docker stack above, the application is already running on port `8080`. No additional steps needed.

Build and run the Docker image:

```bash
# 1. Build the JAR
./mvnw clean package -DskipTests -B

# 2. Build the Docker image
docker build -t fraud-rule-engine .

# 3. Run (with infrastructure already up via docker compose)
docker run -d --name fraud-rule-engine \
  --network config_default \
  -p 8080:8080 \
  -e DATASOURCE_URL=jdbc:postgresql://postgres:5432/transaction_fraud \
  -e DATASOURCE_USERNAME=fraud_user \
  -e DATASOURCE_PASSWORD=fraud_pass \
  -e OAUTH2_ISSUER_URI=http://localhost:9090/realms/capitec \
  -e SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://keycloak:8080/realms/capitec/protocol/openid-connect/certs \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:29092 \
  fraud-rule-engine
```

### Local dev (Option B)

Ensure the infrastructure services are running (Option B above), then:

```bash
# Build
./mvnw clean install

# Run
./mvnw spring-boot:run

# Run tests
./mvnw test
```

The service starts on port `8080`.

### Health check

```bash
curl http://localhost:8080/actuator/health
```

---

## Fraud Rules

### Rule Types

There are four rule types split across two evaluation modes:

| Rule Type           | Eval Mode   | How it works                                                |
|---------------------|-------------|-------------------------------------------------------------|
| `AMOUNT_THRESHOLD`  | `SPEL`      | SpEL expression evaluated against the transaction fields    |
| `COUNTRY_BLACKLIST` | `SPEL`      | SpEL expression evaluated against the transaction fields    |
| `VELOCITY`          | `EVALUATOR` | Custom evaluator (checks transaction frequency per account) |
| `IMPOSSIBLE_TRAVEL` | `EVALUATOR` | Custom evaluator (checks geolocation jumps between events)  |

**SpEL rules** use a `conditions` JSON object with a single `expression` key. The expression root object is the transaction, so any transaction field can be referenced directly.

**Available fields in SpEL expressions:**

| Field                  | Type                 | Example value           |
|------------------------|----------------------|-------------------------|
| `transactionId`        | String               | `"txn-abc-123"`         |
| `amount`               | BigDecimal           | `1500.00`               |
| `currency`             | String               | `"ZAR"`                 |
| `accountId`            | String               | `"acc-001"`             |
| `merchantId`           | String               | `"merch-xyz"`           |
| `transactionType`      | TransactionType enum | `PURCHASE`              |
| `transactionTimestamp` | OffsetDateTime       | `2024-01-15T10:30:00Z`  |
| `country`              | String               | `"ZA"`                  |
| `city`                 | String               | `"Cape Town"`           |
| `latitude`             | Double               | `-33.9249`              |
| `longitude`            | Double               | `18.4241`               |
| `ipAddress`            | String               | `"192.168.1.100"`       |

**Valid `transactionType` values:** `PURCHASE`, `WITHDRAWAL`, `DEPOSIT`, `TRANSFER`, `REFUND`, `ATM_WITHDRAWAL`

---

### Create a SpEL Rule

#### AMOUNT_THRESHOLD — flag high-value transactions

```bash
curl -s -X POST http://localhost:8080/api/v1/rules/create \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "High Value Transaction",
    "description": "Flags any single transaction above R50,000",
    "ruleType": "AMOUNT_THRESHOLD",
    "evalMode": "SPEL",
    "riskScoreImpact": 60,
    "conditions": {
      "expression": "amount > 50000"
    }
  }'
```

#### AMOUNT_THRESHOLD — flag ATM withdrawals over a threshold

```bash
curl -s -X POST http://localhost:8080/api/v1/rules/create \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Large ATM Withdrawal",
    "description": "Flags ATM withdrawals above R10,000",
    "ruleType": "AMOUNT_THRESHOLD",
    "evalMode": "SPEL",
    "riskScoreImpact": 40,
    "conditions": {
      "expression": "transactionType.name() == '\''ATM_WITHDRAWAL'\'' && amount > 10000"
    }
  }'
```

#### COUNTRY_BLACKLIST — flag transactions from restricted countries

```bash
curl -s -X POST http://localhost:8080/api/v1/rules/create \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Sanctioned Country Block",
    "description": "Flags transactions originating from sanctioned countries",
    "ruleType": "COUNTRY_BLACKLIST",
    "evalMode": "SPEL",
    "riskScoreImpact": 80,
    "conditions": {
      "expression": "country == '\''KP'\'' || country == '\''IR'\'' || country == '\''CU'\''"
    }
  }'
```

#### COUNTRY_BLACKLIST — flag foreign currency on domestic accounts

```bash
curl -s -X POST http://localhost:8080/api/v1/rules/create \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Foreign Currency on Domestic Account",
    "description": "Flags non-ZAR transactions from South African IPs",
    "ruleType": "COUNTRY_BLACKLIST",
    "evalMode": "SPEL",
    "riskScoreImpact": 30,
    "conditions": {
      "expression": "country == '\''ZA'\'' && currency != '\''ZAR'\''"
    }
  }'
```

---

### Create an Evaluator Rule

Evaluator rules do not use SpEL expressions. The `conditions` field carries configuration parameters consumed by the custom evaluator.

#### VELOCITY — flag accounts with too many transactions

Counts completed transactions for the same account within a rolling time window. Fires when the count meets or exceeds `maxTransactions`.

```bash
curl -s -X POST http://localhost:8080/api/v1/rules/create \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "High ATM Withdrawal Velocity",
    "description": "Flags accounts with 5 or more ATM withdrawals in 60 minutes",
    "ruleType": "VELOCITY",
    "evalMode": "EVALUATOR",
    "riskScoreImpact": 50,
    "conditions": {
      "windowMinutes": 60,
      "maxTransactions": 5,
      "transactionTypes": ["ATM_WITHDRAWAL"]
    }
  }'
```

**`conditions` fields:**
- `windowMinutes` (integer, required, > 0) — rolling look-back period in minutes
- `maxTransactions` (integer, required, > 0) — rule fires when prior count meets this value
- `transactionTypes` (array of strings, optional) — restrict to specific types, e.g. `["ATM_WITHDRAWAL", "WITHDRAWAL"]`. Omit to apply to all types.

#### IMPOSSIBLE_TRAVEL — flag geolocation anomalies

Compares the current transaction's coordinates against the most recent prior transaction for the same account. Derives implied travel speed (Haversine distance ÷ elapsed time). Fires when implied speed exceeds `maxSpeedKmh`.

```bash
curl -s -X POST http://localhost:8080/api/v1/rules/create \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Impossible Travel — ATM",
    "description": "Flags ATM withdrawals implying travel faster than 900 km/h between successive events",
    "ruleType": "IMPOSSIBLE_TRAVEL",
    "evalMode": "EVALUATOR",
    "riskScoreImpact": 60,
    "conditions": {
      "maxSpeedKmh": 900,
      "transactionTypes": ["ATM_WITHDRAWAL"]
    }
  }'
```

**`conditions` fields:**
- `maxSpeedKmh` (number, optional, default `900`) — implied speed above this value triggers the rule. Use `900` for a commercial aviation baseline; use `200` to flag road-speed-only anomalies.
- `transactionTypes` (array of strings, optional) — restrict to specific types. Omit to apply to all types.

---

### List / Get / Update / Delete Rules

**List all rules:**

```bash
curl -s http://localhost:8080/api/v1/rules \
  -H "Authorization: Bearer $TOKEN" | jq
```

**Get a rule by ID:**

```bash
curl -s http://localhost:8080/api/v1/rules/<RULE_UUID> \
  -H "Authorization: Bearer $TOKEN" | jq
```

**Update a rule** (all fields optional — only supplied fields are updated):

```bash
curl -s -X PUT http://localhost:8080/api/v1/rules/<RULE_UUID> \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "riskScoreImpact": 75,
    "active": true,
    "conditions": {
      "expression": "amount > 75000"
    }
  }'
```

**Disable a rule** (set `active: false` to exclude it from evaluation without deleting):

```bash
curl -s -X PUT http://localhost:8080/api/v1/rules/<RULE_UUID> \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"active": false}'
```

**Delete a rule permanently:**

```bash
curl -s -X DELETE http://localhost:8080/api/v1/rules/<RULE_UUID> \
  -H "Authorization: Bearer $TOKEN"
```

---

## Send Transaction Events via Kafka

Transaction events arrive on the `transaction-events` topic. Each message **must** include an `x-actor-id` header (the UUID of the system or user publishing the event) for audit tracking.

### Option A — Kafka UI (browser)

1. Open [http://localhost:8090](http://localhost:8090)
2. Select cluster `transaction-fraud` → **Topics** → `transaction-events`
3. Click **Produce Message**
4. Add header: key `x-actor-id`, value `550e8400-e29b-41d4-a716-446655440000`
5. Paste the message body below into the **Value** field and click **Produce**

### Option B — kcat CLI

```bash
# Install kcat: brew install kcat

# Two requirements for correct delivery:
# 1. Use 127.0.0.1 — kcat resolves localhost to IPv6 ([::1]) but Kafka listens on IPv4 only.
# 2. Pipe through jq -c '.' — kcat uses newline as the message delimiter, so a
#    multi-line JSON body becomes one message per line. jq -c compacts to a single line.

echo '{
  "transactionId": "txn-001",
  "amount": 75000.00,
  "currency": "ZAR",
  "accountId": "acc-123",
  "merchantId": "merch-456",
  "transactionType": "PURCHASE",
  "transactionTimestamp": "2024-01-15T10:30:00Z",
  "country": "ZA",
  "city": "Cape Town",
  "latitude": -33.9249,
  "longitude": 18.4241,
  "ipAddress": "196.25.1.100"
}' | jq -c '.' | kcat -b 127.0.0.1:9092 \
     -t transaction-events \
     -H "x-actor-id=payments-service" \
     -P
```

### Sample event payloads

**High-value purchase (triggers AMOUNT_THRESHOLD if rule ≥ 50k exists):**

```json
{
  "transactionId": "txn-high-001",
  "amount": 95000.00,
  "currency": "ZAR",
  "accountId": "acc-001",
  "merchantId": "merch-luxury",
  "transactionType": "PURCHASE",
  "transactionTimestamp": "2024-01-15T10:30:00Z",
  "country": "ZA",
  "city": "Johannesburg",
  "latitude": -26.2041,
  "longitude": 28.0473,
  "ipAddress": "196.25.1.100"
}
```

**Sanctioned country transaction (triggers COUNTRY_BLACKLIST):**

```json
{
  "transactionId": "txn-sanction-001",
  "amount": 500.00,
  "currency": "USD",
  "accountId": "acc-002",
  "merchantId": "merch-foreign",
  "transactionType": "TRANSFER",
  "transactionTimestamp": "2024-01-15T11:00:00Z",
  "country": "KP",
  "city": "Pyongyang",
  "latitude": 39.0392,
  "longitude": 125.7625,
  "ipAddress": "175.45.176.1"
}
```

**Normal low-risk transaction:**

```json
{
  "transactionId": "txn-normal-001",
  "amount": 250.00,
  "currency": "ZAR",
  "accountId": "acc-003",
  "merchantId": "merch-grocery",
  "transactionType": "PURCHASE",
  "transactionTimestamp": "2024-01-15T12:00:00Z",
  "country": "ZA",
  "city": "Cape Town",
  "latitude": -33.9249,
  "longitude": 18.4241,
  "ipAddress": "196.25.2.50"
}
```

---

## Fraud Check Results API

After a transaction is processed (either via Kafka or directly through the service), retrieve the result by `transactionId`:

```bash
curl -s http://localhost:8080/api/v1/fraud/check/txn-high-001 \
  -H "Authorization: Bearer $TOKEN" | jq
```

**Response:**

```json
{
  "checkId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "transactionId": "txn-high-001",
  "outcome": "HIGH",
  "riskScore": 60,
  "reason": null,
  "checkedAt": "2024-01-15T10:30:05.123456Z"
}
```

**Outcome values:**

| Outcome  | Condition          |
|----------|--------------------|
| `HIGH`   | riskScore >= 100   |
| `MEDIUM` | riskScore > 50     |
| `LOW`    | riskScore <= 50    |

---

## Scoring Logic

1. All **active** rules are loaded from the database on each check.
2. Each rule is dispatched to the matching `RuleEvaluator` (matched by `evalMode` + `ruleType`).
3. The evaluator returns either `riskScoreImpact` (rule matched) or `0` (no match).
4. All scores are summed and **capped at 100**.
5. The capped score determines `FraudOutcome`.

**Example — two rules both match:**

| Rule                   | riskScoreImpact | Matched |
|------------------------|-----------------|---------|
| High Value Transaction | 60              | Yes     |
| Sanctioned Country     | 80              | Yes     |
| **Total (capped)**     | **100**         |         |
| **Outcome**            | **HIGH**        |         |

---

## API Reference

### Rules

| Method   | Path                       | Description              | Auth     |
|----------|----------------------------|--------------------------|----------|
| `POST`   | `/api/v1/rules/create`     | Create a rule            | Required |
| `GET`    | `/api/v1/rules`            | List all rules           | Required |
| `GET`    | `/api/v1/rules/{id}`       | Get rule by UUID         | Required |
| `PUT`    | `/api/v1/rules/{id}`       | Update a rule            | Required |
| `DELETE` | `/api/v1/rules/{id}`       | Delete a rule            | Required |

### Fraud Checks

| Method | Path                                    | Description                              | Auth     |
|--------|-----------------------------------------|------------------------------------------|----------|
| `GET`  | `/api/v1/fraud/check/{transactionId}`   | Get fraud check result by transaction ID | Required |

### Create Rule — Request Body

```json
{
  "name": "string (required)",
  "description": "string (required)",
  "ruleType": "AMOUNT_THRESHOLD | COUNTRY_BLACKLIST | VELOCITY | IMPOSSIBLE_TRAVEL",
  "evalMode": "SPEL | EVALUATOR",
  "riskScoreImpact": 0,
  "conditions": {}
}
```

### Update Rule — Request Body (all fields optional)

```json
{
  "name": "string",
  "description": "string",
  "riskScoreImpact": 0,
  "active": true,
  "conditions": {}
}
```

### Fraud Check — Response Body

```json
{
  "checkId": "uuid",
  "transactionId": "string",
  "outcome": "HIGH | MEDIUM | LOW",
  "riskScore": 0,
  "reason": "string | null",
  "checkedAt": "ISO 8601 datetime"
}
```

### Error Response

All error responses follow a consistent shape:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "name: must not be blank",
  "timestamp": "2024-01-15T10:30:00Z",
  "path": "/api/v1/rules/create"
}
```

---

## Testing

### Run all tests

```bash
./mvnw test
```

### Run a specific test class

```bash
./mvnw test -Dtest=TransactionFraudCheckApplicationTests
./mvnw test -Dtest=VelocityRuleEvaluatorTest
./mvnw test -Dtest=ImpossibleTravelRuleEvaluatorTest
```

### Evaluator unit test scenarios

| Test class | Key scenarios |
|---|---|
| `VelocityRuleEvaluatorTest` | `supports()` correct/wrong mode; count below/at/above threshold; missing required conditions keys; invalid JSON; `windowMinutes=0`; window start computation verified via `ArgumentCaptor` |
| `ImpossibleTravelRuleEvaluatorTest` | No prior transaction; null coordinates (current and prior); same location; speed below/at/above threshold; zero and negative time delta; default `maxSpeedKmh`; Haversine accuracy (London→New York ≈ 5570 km ± 10 km) |

---

## Swagger UI

Available without authentication at:

```
http://localhost:8080/swagger-ui.html
```

The OpenAPI spec (for import into Postman or Insomnia) is at:

```
http://localhost:8080/v3/api-docs
```
