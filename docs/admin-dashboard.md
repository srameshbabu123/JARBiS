# Internal analytics backend

This branch combines development's market-data implementation with the OLAP feature branch and adds a **backend-only** Java 17 / Spring Boot analytics service. It contains no dashboard pages, styles, chart code, or other frontend assets. The separately developed frontend consumes the API below.

## Run

1. Copy `.env.analytics.example` to `.env.analytics`. Set database passwords and a random JWT secret of at least 32 bytes. Existing database volumes require their existing passwords.
2. Generate the internal account's BCrypt hash interactively:

   ```bash
   docker run --rm -it httpd:2.4-alpine htpasswd -nBC 12 admin
   ```

   Set `ANALYTICS_ADMIN_PASSWORD_HASH` to the `$2y$...` part after `admin:`. Keep single quotes around the hash in the env file.
3. Set `ANALYTICS_ALLOWED_ORIGINS` to explicit frontend origins, comma-separated, such as `http://localhost:4200`. Leave empty for same-origin access. Wildcards are rejected.
4. Run:

   ```bash
   docker compose --env-file .env.analytics --profile analytics up -d --build
   ```

The API listens on `http://localhost:8082`; there is no web UI at this address. The Compose port binds to loopback. Use an SSH tunnel or TLS reverse proxy for remote access. Set `ANALYTICS_SECURE_COOKIE=true` behind HTTPS. Use a same-site frontend/API deployment for the strict session cookie; CORS alone does not enable cross-site cookies.

The example points trading at local Compose PostgreSQL. Set `BROKERAGE_DB_URL` explicitly for shared OLTP and `ANALYTICS_DB_URL` for shared OLAP. Container PostgreSQL ports are 5432; the existing host ports are 8090/8091. The analytics service has no OLTP datasource.

## Frontend authentication contract

All requests below must retain cookies (`credentials: 'include'` in fetch or `withCredentials: true` in Angular HttpClient).

1. `GET /session/csrf` (public): returns `{ "parameterName": "_csrf", "headerName": "X-CSRF-TOKEN", "token": "..." }` and initializes the session.
2. `POST /login`: send `application/x-www-form-urlencoded` fields `username` and `password`, plus the returned CSRF header/token. Success is **204**, invalid credentials **401**, missing/invalid CSRF **403**. There is no HTML response or redirect.
3. Fetch `/session/csrf` again after successful login because authentication rotates the CSRF token.
4. `GET /session/me`: returns `{ "username": "admin", "roles": ["ROLE_ANALYST"] }`. Anonymous callers receive **401**.
5. `POST /logout`: include the current CSRF header/token. Returns **204** and invalidates the session.

Reporting requires ROLE_ANALYST. Client JWTs do not grant internal access. Sessions expire after 30 minutes of inactivity. There is one configured analyst account initially; individual staff identities/SSO are future work. The backend keeps CSRF protection enabled, and credentialed CORS only accepts configured origins. A frontend should route users to its own sign-in page after a 401; 403 means insufficient role or a rejected CSRF/origin check.

## Reporting API

| Method and path | Result |
| --- | --- |
| GET `/admin/analytics/options` | Available instruments, account-type segments, currencies, and datasets with first/last dates |
| GET `/admin/analytics/dashboard` | Summary, preceding-period summary, daily activity, top instruments, daily clients, ingestion timestamps, applied filter |
| GET `/admin/analytics/trades` | Paginated completed fills |

Dashboard/trade query parameters:

| Parameter | Meaning |
| --- | --- |
| `from`, `to` | Inclusive ISO dates, interpreted in UTC; maximum 367 days. Dashboard defaults to the last 30 days; trades requires both. |
| `instrumentId` | Optional numeric asset ID |
| `segment` | Optional account type from `/options`; this is the available client-segmentation proxy |
| `currency` | Settlement currency, default USD; currencies are never summed together |
| `dataset` | LIVE (default) or LEGACY (existing/sample records) |
| `page` | Trades only; zero-based, default 0, page size 25 |

Example:

```text
GET /admin/analytics/dashboard?from=2026-10-01&to=2026-10-15&currency=USD&dataset=LIVE
```

Dashboard response shape (illustrative values):

```json
{
  "summary": {"trades": 2, "value": 80.00, "clients": 3, "instruments": 1},
  "previous": {"trades": 0, "value": 0, "clients": 0, "instruments": 0},
  "activity": [{"day": "2026-10-01", "trades": 2, "value": 80.00}],
  "instruments": [{"id": 8, "name": "AAPL", "trades": 2, "value": 80.00}],
  "clients": [{"day": "2026-10-01", "clients": 3}],
  "ingestion": {"last_event_at": null, "last_ingested_at": null, "processed_events": 0},
  "filter": {"from": "2026-10-01", "to": "2026-10-15", "instrumentId": null, "segment": null, "currency": "USD", "dataset": "LIVE"}
}
```

Days without activity are omitted; the frontend may fill gaps with zeros. Value is execution price times quantity, one count per completed fill. Active clients are distinct across both participants; for a segment filter only clients in that account type count. Trade values for a segment include each matching trade once. The preceding period has the same number of days immediately before `from`. If the previous value is zero, percentage growth has no baseline.

`/trades` returns `rows`, `page`, `pageSize`, and `total`. Each row includes `id`, `instrument`, `quantity`, `price`, `value`, `currency`, `executed_at`, `buy_order_id`, and `sell_order_id`. Rows sort by execution time then ID descending. No client names/emails/passwords are returned. Monetary values are JSON numbers. The latest ingestion timestamp is the latest received live event, not proof that the pipeline is currently caught up. UNKNOWN currency is not a valid cross-currency monetary total.

## Data pipeline and database changes

`TransactionService.completeTransaction` saves the execution timestamp and an immutable event payload in `analytics_outbox` in the settlement transaction. Rollback also rolls back the event. The relay publishes `trading.settled.v1`, waits for acknowledgement, then marks delivery. Crash recovery can redeliver; OLAP records event UUIDs and projects facts atomically, preventing duplicate fills. A second event ID for an already projected live transaction also does not duplicate the fact.

Enable `BROKERAGE_ANALYTICS_ENABLED=true` only on the designated deployment. All relays for one OLTP database must use the same Kafka cluster, not separate teammate-local brokers. Other stacks leave the relay disabled; events remain pending until it runs. Invalid events and database failures retry without silent skipping; a bad event blocks its partition and requires investigation.

Flyway adds migrations using `reporting_migrations` history, preserving existing volumes. New volumes initialize the schema without dummy data. Existing facts are labeled LEGACY. For sample-only development, load `002_dummy_data.sql` before first migration on a disposable OLAP database. Never load synthetic IDs into a database receiving live events; collisions are rejected rather than overwriting legacy facts. Old trades without execution timestamps are not given fabricated live backfill timestamps.

Queries use indexed fact tables directly, so materialized-view refresh is not required for this API. Existing views remain available. The development deployment uses one Kafka node. Database role separation (migration, projection, SELECT-only reporting) and staff SSO are further hardening work.

## Verification and scope

```bash
mvn test
mvn -f analytics-service/pom.xml verify
```

CI also runs PostgreSQL tests in its own disposable `admin_dashboard_test` database. Local integration tests run when `TEST_POSTGRES_URL` names that database and TEST_POSTGRES_USER/PASSWORD are configured. These tests drop/recreate analytics schemas; never point them at shared data.

This backend supports BR-16/17 analysis of new completed fills. Full legal order-lifecycle audit, current-quote pricing, client ownership enforcement, acceptance reservations, financial decimal migration, concurrency protection, client session revocation, status push updates, and BR-18 differentiation remain separate work. It provides no administrative balance/trade editing and no frontend implementation.
