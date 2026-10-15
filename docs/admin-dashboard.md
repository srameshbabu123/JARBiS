# Python internal analytics backend

The client-facing trading backend remains Java. Only `analytics-service/` has moved from Spring Boot to Python 3.12. There are no frontend pages or chart assets. Your teammate connects their frontend to the HTTP endpoints below.

## Architecture and responsibilities

1. Java settlement commits holdings/cash/trade changes and an outbox event together.
2. The Java outbox relays write completed trades and order evidence directly to OLAP through `OlapEventWriter`. No analytics Kafka topics or Python consumer are used.
3. Each OLAP write commits independently before the OLTP delivery timestamp is updated. Failed writes stay pending. Event UUIDs and transaction IDs make retries idempotent.
4. FastAPI serves the reporting API. It queries only OLAP, using a bounded psycopg pool. PostgreSQL calculates the summaries, distinct-client counts, currency filters, and rankings.
5. pandas prepares the bounded daily activity dataset, filling missing days with zero while preserving Decimal values internally. Raw trade tables are not loaded wholesale into pandas. Empty datasets still return an empty activity array.
6. The teammate's frontend draws interactive charts from JSON. No server-generated chart images are implemented; Plotly/Matplotlib or report exports can be added later if required.

Python runs only the HTTP analytics/operations API. Java owns trading-data ingestion. Redis remains the market-quote cache. Python still writes sessions, dispute cases, notes and access history; it does not ingest trading events.

## Files to walk through

| File | Responsibility |
| --- | --- |
| `analytics/config.py` | Explicit properties configuration, with environment overrides |
| `analytics/api.py` | HTTP routes, validation, CORS, login, session and CSRF behavior |
| `analytics/sessions.py` | Revocable PostgreSQL-backed sessions, idle expiry, hashed cookie tokens |
| `analytics/models.py` | Event schema and report-filter validation |
| Java `messaging/OlapEventWriter.java` | Atomic, idempotent direct OLAP writes |
| `analytics/reports.py` | SQL reporting and pandas chart preparation |
| `analytics/database.py` | Connection pooling and checksummed migrations |
| `migrations/` | Additive SQL; original tables retained, session table added |

## Configuration: application.properties and environment

`analytics-service/application.properties` contains non-secret defaults. Python does not automatically understand Java properties files; our config loader explicitly reads simple `KEY=value` entries. It then applies environment overrides. It does not automatically load `.env` and does not support Java `${...}` substitution or properties escape syntax.

Set credentials in environment variables, or create an untracked `application-local.properties` and select it with `ANALYTICS_PROPERTIES_FILE=/absolute/path/to/application-local.properties`. This file must include the full configuration you need; it replaces the default properties file. No secrets belong in a committed properties file.

Settings:

| Setting | Purpose |
| --- | --- |
| ANALYTICS_DB_URL | PostgreSQL URL, e.g. `postgresql://localhost:8091/jarbis_olap` |
| ANALYTICS_DB_USER / ANALYTICS_DB_PASSWORD | OLAP credentials |
| ANALYTICS_ADMIN_USER / ANALYTICS_ADMIN_PASSWORD_HASH | Provisioned internal account and BCrypt hash |
| ANALYTICS_ALLOWED_ORIGINS | Comma-separated explicit frontend origins; empty means same-origin only |
| ANALYTICS_SECURE_COOKIE | true behind HTTPS; false only for local HTTP |
| ANALYTICS_TOPIC | Defaults to trading.settled.v1 |
| ANALYTICS_CONSUMER_GROUP | Defaults to jarbis-analytics-v1 |

Existing `jdbc:postgresql://` analytics URLs are accepted and normalized during transition. Shared-server connections use host port 8091 for OLAP; database containers on the same Docker network use `postgres-olap:5432`. Java OLTP still uses host port 8090 when connecting through the shared server. These are separate from API port 8082.

## Local Python development

```bash
cd analytics-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements-dev.txt
```

Generate a BCrypt hash interactively:

```bash
python -c 'import bcrypt,getpass; print(bcrypt.hashpw(getpass.getpass("Internal password: ").encode(),bcrypt.gensalt()).decode())'
```

Configure the settings above, then start the two processes in separate terminals:

```bash
uvicorn analytics.api:create_app --factory --host 127.0.0.1 --port 8082
```

For Docker, copy `.env.analytics.example` to `.env.analytics`, replace all credential placeholders and the BCrypt hash, and run from the repository root:

```bash
docker compose --env-file .env.analytics --profile analytics up -d --build
```

Compose uses the env file to populate container settings; Python itself does not read that file. The `analytics` HTTP service uses only OLAP. Start it once before enabling Java delivery so it applies migrations. The example trading URL uses local Compose PostgreSQL; explicitly set the shared-server URLs when deploying centrally. Bindings default to localhost; use a tunnel or TLS reverse proxy for remote access.

Enable `BROKERAGE_ANALYTICS_ENABLED=true` only on the designated Java deployment. All relays for one shared OLTP must target the same OLAP database. Configure Java `brokerage.analytics.db-url`, `brokerage.analytics.db-user`, and `brokerage.analytics.db-password` in application.properties or their BROKERAGE_ANALYTICS_DB_* environment equivalents. Host URL: jdbc:postgresql://localhost:8091/jarbis_olap; Docker URL: jdbc:postgresql://postgres-olap:5432/jarbis_olap. Stop the old Python consumer before switching. To replay previously Kafka-delivered rows safely, reset published_at to NULL in analytics_outbox and order_audit_outbox during a maintenance window; existing OLAP event IDs prevent duplicate inserts. Delivery retries if migrations are not yet applied.

## Existing database migration

No volumes need to be deleted. Startup takes a PostgreSQL advisory lock and applies additive SQL with a SHA-256 ledger in `reporting_migrations.python_history`. Recorded Flyway V1/V2 versions are adopted without rerunning their data backfills; fresh databases run the original idempotent SQL. The old Flyway history remains intact; do not run the retired Java analytics application against the same deployment. A new V3 adds internal sessions. Do not modify already applied migration files; add another version instead.

New volumes initialize the OLAP schema without fixtures. Existing facts are LEGACY, not live backfill. Load sample SQL only into a disposable database before initial analytics migrations. Never mix synthetic trade IDs with live ingestion; the projector rejects collisions. Old trades without a trustworthy execution timestamp are not assigned a fabricated live timestamp.

## Frontend API contract

Existing paths, JSON keys, and status codes are retained. The previous Java in-memory login sessions do not transfer: staff must sign in again after migration. Python sessions persist in OLAP and expire after 30 minutes of inactivity; logout revokes them. Cookie values are hashed in storage, and successful login rotates both session and CSRF tokens.

All browser requests must include cookies (`credentials: 'include'` / Angular `withCredentials: true`). Use a same-site deployment for the strict cookie; allowing a CORS origin does not make cross-site cookies work.

1. GET `/session/csrf`: public; returns `parameterName`, `headerName`, and `token`, initializing an anonymous session.
2. POST `/login`: form-urlencoded `username` and `password`, with the returned `X-CSRF-TOKEN` header. Success 204, bad credentials 401, invalid CSRF 403. No HTML/redirect.
3. Fetch `/session/csrf` again after login for the rotated token.
4. GET `/session/me`: returns `username` and `roles: ["ROLE_ANALYST"]`; anonymous callers receive 401. Client JWTs do not grant internal access.
5. POST `/logout`: current CSRF header; returns 204 and invalidates the session.

One analyst and one operations account are supported; individual staff identities/SSO and login rate limiting at the deployment boundary are further production work.

| GET endpoint | Response |
| --- | --- |
| `/admin/analytics/options` | instruments, segments (account type), currencies, datasets with first/last dates |
| `/admin/analytics/dashboard` | summary, previous, activity, instruments, clients, ingestion, filter |
| `/admin/analytics/trades` | rows, page, pageSize (25), total |
| `/actuator/health` | public database reachability check; not proof of delivery freshness |

Dashboard/trades filters: inclusive UTC `from` and `to` ISO dates (maximum 367 days), optional numeric `instrumentId`, optional `segment` from options, `currency` (default USD), `dataset` (LIVE default or LEGACY). Dashboard defaults to the last 30 days; trades requires dates and accepts zero-based `page`. Invalid parameters return 400.

`summary`/`previous` contain `trades`, `value`, `clients`, `instruments`. `activity` contains `day`, `trades`, `value`; pandas adds zero days inside nonempty ranges. `instruments` contains `id`, `name`, `trades`, `value`. `clients` contains `day`, `clients` and remains sparse. `ingestion` contains `last_event_at`, `last_ingested_at`, `processed_events`. Monetary values remain JSON numbers. Each trade row contains `id`, `instrument`, `quantity`, `price`, `value`, `currency`, `executed_at`, `buy_order_id`, `sell_order_id` and sorts newest first.

A fill counts once per transaction. Active clients are distinct across buyer/seller participation; a selected account-type segment counts only clients in that segment, while trade value includes a matching trade once. Currencies are never summed together; UNKNOWN cannot be interpreted as a valid monetary reporting total. Preceding comparisons use the adjacent equal-length period; a zero baseline has no meaningful percentage growth. No names, emails, or passwords are exposed. The latest ingestion timestamp is the latest received event, not proof that delivery is caught up.

## Tests

```bash
# Trading backend, repository root:
mvn test
# Python analytics, analytics-service directory:
python -m pytest -q
```

Unit/API tests need no Kafka or database. Real PostgreSQL tests require TEST_POSTGRES_URL pointing to a disposable database whose name is exactly `admin_dashboard_test`, plus TEST_POSTGRES_USER/PASSWORD. These tests DROP analytics schemas; never target shared data. Both postgresql:// and legacy jdbc:postgresql:// test URLs work. CI provisions its own PostgreSQL and runs migration, replay, rollback, reporting, session-expiry and real API tests. Java relay tests verify OLAP-before-delivery-mark ordering and retention on failure. A deployed Java-to-OLAP outage/recovery smoke test remains a deployment check. Python projection helpers are test fixtures only; production projection lives in Java.

## Remaining BRS work

This backend supports BR-16/17 analysis of completed fills. Full legal order-lifecycle audit, current-quote execution, client ownership enforcement, acceptance reservations, Java financial decimal migration, settlement concurrency, client session revocation, status push updates, and BR-18 differentiation remain separate trading work. No frontend, arbitrary natural-language chart generator, or administrative balance/trade editing is included.

## David: investigation and dispute workflow

Configure a separate operations identity with `ANALYTICS_OPERATIONS_USER=david` and `ANALYTICS_OPERATIONS_PASSWORD_HASH` (a separately generated BCrypt hash). These credentials are provisioned internally, never through client registration. Existing ANALYTICS_ADMIN_USER/HASH remain the analyst identity for Priya. Usernames must differ. Sessions now store roles; old analyst sessions are adopted as ANALYST. There is one provisioned identity per persona in this increment; individual staff accounts/SSO are still deployment work.

Permissions:

| Role | Access |
| --- | --- |
| ANALYST | Aggregate dashboard/options APIs; no individual-trade, investigation or dispute access |
| OPERATIONS | Aggregate reporting, individual fills, investigations, evidence exports, and dispute workflow |
| Client/anonymous | No admin APIs |

Every POST/PATCH under `/admin/` requires the current session's X-CSRF-TOKEN header. CORS now allows PATCH for explicitly configured origins. GET `/session/me` reports ROLE_ANALYST or ROLE_OPERATIONS.

### Source evidence

Java now persists `order_audit_outbox` rows in the same transaction as order submission, status/deletion requests, execution pricing, and successful matched fills. A row has a UUID and a database-generated sequence. Evidence has no cascading order foreign key, so deleting an order does not delete its history. PostgreSQL triggers reject edits/deletes of source evidence, while permitting publication timestamp updates. Startup installs this trigger after Hibernate creates/updates the table; the deployment role must have the required DDL privileges or deploy the same trigger through controlled migrations.

The lifecycle relay adds the source sequence and writes directly to OLAP through Java. Delivery is acknowledged only after OLAP commits. UUID deduplication preserves source sequence and supports retry; unexpected sequence collisions are rejected. Matched-fill evidence includes before/after cash and holding quantities, execution price and actual pricing basis. Failed settlement attempts are recorded after the financial transaction rolls back in a separate transaction; they do not claim that cash/holdings changes committed.

The current engine uses resting-order prices, so evidence explicitly states `marketQuoteRecorded=false` and `priceBasis=RESTING_ORDER_PRICE`. Submission evidence lists the limited checks actually performed and states `fullTradingRuleValidation=false`. No current-quote proof or historical acceptance is fabricated. Old orders can have fill-only evidence; the timeline exposes coverage gaps. Direct execution is labeled separately, including its absence of a matched transaction record.

### Operations endpoints

| Endpoint | Purpose |
| --- | --- |
| GET `/admin/investigations/orders` | Search latest captured lifecycle states by from/to (UTC), clientId, orderId, status and page |
| GET `/admin/investigations/orders/{id}/timeline` | Sequenced events, fills, source/ingestion timestamps and coverage gaps |
| GET `/admin/investigations/orders/{id}/export` | pandas CSV evidence export, including gap rows and spreadsheet-formula protection |
| GET `/admin/investigations/trades/{id}` | Individual recorded fill |
| POST `/admin/disputes` | Open a case tied to available order evidence |
| GET `/admin/disputes` | Paginated cases; optional status/clientId/orderId |
| GET `/admin/disputes/insights?from=...&to=...` | pandas case counts, categories, daily openings, open-age buckets and median resolution hours |
| GET `/admin/disputes/{id}` | Case with append-only action/note history |
| GET `/admin/disputes/{id}/evidence` | Frozen evidence captured when the case opened |
| PATCH `/admin/disputes/{id}` | Assignment or allowed status transition with optimistic version checking |
| POST `/admin/disputes/{id}/notes` | Add an attributed note |

Order search filters by the latest recorded event date, uses 25-row pages, and covers orders with lifecycle events. Exact order/trade lookups can return older fill-only history. Timeline responses cap events at 2000 and fills at 1000 and explicitly mark truncation; an operational archive extraction is needed above that cap. ProjectionAsOf is an ingestion timestamp, not a completeness guarantee. Lookup returns 404 when evidence is unavailable; this can also mean ingestion has not caught up.

Create request:

```json
{"requestId":"GENERATE-A-UUID","orderId":2,"category":"EXECUTION_PRICE","reason":"Client disputes the recorded execution price","assignedTo":"david"}
```

`requestId` is the idempotency key. An identical retry returns the existing case; reuse for a different request returns 409. The client ID is derived from evidence rather than trusted from the request. Category is EXECUTION_PRICE, UNAUTHORIZED_ORDER, CASH_BALANCE, HOLDINGS or OTHER. Assignment is restricted to a provisioned operations user, or null to unassign.

Update request:

```json
{"expectedVersion":1,"status":"IN_REVIEW","note":"Review started"}
```

Workflow: OPEN → IN_REVIEW → RESOLVED → CLOSED. Resolving requires a resolution of at least 10 characters. RESOLVED/CLOSED may reopen to IN_REVIEW with a reason in `note`. Closed cases reject notes until reopened. Each mutation increments `version`; stale updates return 409, so the frontend must reload. Notes are `{ "text": "..." }`. Case detail returns up to 1000 action/note records and exposes historyTruncated above that limit. Case origin, original reason, client/order link and frozen evidence cannot be edited; actions/notes and evidence/access records reject UPDATE/DELETE at database level. Case operations never reverse a trade or adjust balances.

pandas insights use case-opened dates, cap input at 10000 cases (larger queries return 400), exclude reopened active cases from resolution-time statistics, and define resolution time as time from opening to latest resolution for currently resolved/closed cases. Only from/to are accepted on dispute insights; trading filters are rejected. CSV export uses an additional logged timeline read. Investigation, case search/detail and insight reads are attributed in `operations.access_events`.

Database-owner/superuser access, retention/backup policy, restore testing, and runtime-role restrictions (including TRUNCATE/DDL) remain deployment responsibilities. The append-only triggers are not a claim of tamper-proof storage against a database administrator. A source audit write failure rolls back its financial transaction; if the database is unavailable even after rollback, durable failure-attempt capture cannot be guaranteed without an external recovery mechanism.

See [BRS/persona coverage](admin-brs-coverage.md) for remaining gaps. Do not describe the platform as fully BRS-compliant based solely on these endpoints.
