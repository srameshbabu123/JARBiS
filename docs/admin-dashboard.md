# Python internal analytics backend

The client-facing trading backend remains Java. Only `analytics-service/` has moved from Spring Boot to Python 3.12. There are no frontend pages or chart assets. Your teammate connects their frontend to the HTTP endpoints below.

## Architecture and responsibilities

1. Java settlement commits holdings/cash/trade changes and an outbox event together.
2. The Java outbox relay publishes `trading.settled.v1`. It marks delivery after Kafka acknowledgement.
3. `python -m analytics.consumer` validates events with Pydantic and writes OLAP facts plus a processed-event marker in one PostgreSQL transaction. Kafka offsets commit only after the database transaction succeeds. Replays do not duplicate fills. Invalid events stop the consumer without acknowledging that offset; the Compose restart policy retries. Investigate persistent failures rather than skipping financial events.
4. FastAPI serves the reporting API. It queries only OLAP, using a bounded psycopg pool. PostgreSQL calculates the summaries, distinct-client counts, currency filters, and rankings.
5. pandas prepares the bounded daily activity dataset, filling missing days with zero while preserving Decimal values internally. Raw trade tables are not loaded wholesale into pandas. Empty datasets still return an empty activity array.
6. The teammate's frontend draws interactive charts from JSON. No server-generated chart images are implemented; Plotly/Matplotlib or report exports can be added later if required.

The HTTP process and consumer are separate containers built from the same Python code. This lets ingestion restart independently of HTTP. Keep one consumer group for this projection, and stop the old Java analytics process before switching deployments. Redis remains the Java market-quote cache and is not the source of trade analytics.

## Files to walk through

| File | Responsibility |
| --- | --- |
| `analytics/config.py` | Explicit properties configuration, with environment overrides |
| `analytics/api.py` | HTTP routes, validation, CORS, login, session and CSRF behavior |
| `analytics/sessions.py` | Revocable PostgreSQL-backed sessions, idle expiry, hashed cookie tokens |
| `analytics/models.py` | Event schema and report-filter validation |
| `analytics/consumer.py` | Kafka polling, fail-without-ack behavior, commit ordering |
| `analytics/projector.py` | Atomic, idempotent OLAP writes |
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
| KAFKA_BOOTSTRAP_SERVERS | Kafka address reachable from the consumer |
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
python -m analytics.consumer
```

For Docker, copy `.env.analytics.example` to `.env.analytics`, replace all credential placeholders and the BCrypt hash, and run from the repository root:

```bash
docker compose --env-file .env.analytics --profile analytics up -d --build
```

Compose uses the env file to populate container settings; Python itself does not read that file. Both `analytics` (HTTP) and `analytics-consumer` (Kafka ingestion) use only the OLAP database. The example trading URL uses local Compose PostgreSQL; explicitly set the shared-server URLs when deploying centrally. Bindings default to localhost; use a tunnel or TLS reverse proxy for remote access.

Enable `BROKERAGE_ANALYTICS_ENABLED=true` only on the designated Java deployment. Every relay for one shared OLTP must use the same Kafka cluster, not separate teammate-local brokers.

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

One provisioned analyst account is supported initially; individual staff accounts/SSO and login rate limiting at the deployment boundary are further production work.

| GET endpoint | Response |
| --- | --- |
| `/admin/analytics/options` | instruments, segments (account type), currencies, datasets with first/last dates |
| `/admin/analytics/dashboard` | summary, previous, activity, instruments, clients, ingestion, filter |
| `/admin/analytics/trades` | rows, page, pageSize (25), total |
| `/actuator/health` | public database reachability check; not proof of Kafka consumer freshness |

Dashboard/trades filters: inclusive UTC `from` and `to` ISO dates (maximum 367 days), optional numeric `instrumentId`, optional `segment` from options, `currency` (default USD), `dataset` (LIVE default or LEGACY). Dashboard defaults to the last 30 days; trades requires dates and accepts zero-based `page`. Invalid parameters return 400.

`summary`/`previous` contain `trades`, `value`, `clients`, `instruments`. `activity` contains `day`, `trades`, `value`; pandas adds zero days inside nonempty ranges. `instruments` contains `id`, `name`, `trades`, `value`. `clients` contains `day`, `clients` and remains sparse. `ingestion` contains `last_event_at`, `last_ingested_at`, `processed_events`. Monetary values remain JSON numbers. Each trade row contains `id`, `instrument`, `quantity`, `price`, `value`, `currency`, `executed_at`, `buy_order_id`, `sell_order_id` and sorts newest first.

A fill counts once per transaction. Active clients are distinct across buyer/seller participation; a selected account-type segment counts only clients in that segment, while trade value includes a matching trade once. Currencies are never summed together; UNKNOWN cannot be interpreted as a valid monetary reporting total. Preceding comparisons use the adjacent equal-length period; a zero baseline has no meaningful percentage growth. No names, emails, or passwords are exposed. The latest ingestion timestamp is the latest received event, not proof that Kafka is caught up.

## Tests

```bash
# Trading backend, repository root:
mvn test
# Python analytics, analytics-service directory:
python -m pytest -q
```

Unit/API tests need no Kafka or database. Real PostgreSQL tests require TEST_POSTGRES_URL pointing to a disposable database whose name is exactly `admin_dashboard_test`, plus TEST_POSTGRES_USER/PASSWORD. These tests DROP analytics schemas; never target shared data. Both postgresql:// and legacy jdbc:postgresql:// test URLs work. CI provisions its own PostgreSQL and runs migration, replay, rollback, reporting, session-expiry and real API tests. A consumer unit test verifies DB-before-Kafka-offset ordering. A full deployed Java settlement-to-Kafka-to-Python smoke test remains a deployment check.

## Remaining BRS work

This backend supports BR-16/17 analysis of completed fills. Full legal order-lifecycle audit, current-quote execution, client ownership enforcement, acceptance reservations, Java financial decimal migration, settlement concurrency, client session revocation, status push updates, and BR-18 differentiation remain separate trading work. No frontend, arbitrary natural-language chart generator, or administrative balance/trade editing is included.
