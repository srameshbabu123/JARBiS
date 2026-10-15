# Internal analytics dashboard

This branch combines development's Redis market-data changes with the OLAP branch. The dashboard is a separate Java 17 / Spring Boot application at http://localhost:8082. Its browser assets are served by that application, so no frontend toolchain or CORS setup is required. It does not alter the separate Angular client branches.

## Start a local stack

1. Copy `.env.analytics.example` to `.env.analytics`. Replace the database-password and JWT-secret placeholders. For existing databases, use their existing passwords; changing environment variables does not change an initialized database password.
2. Generate a BCrypt password hash interactively (the command prompts for a password):

   ```bash
   docker run --rm -it httpd:2.4-alpine htpasswd -nBC 12 admin
   ```

   Copy only the `$2y$...` portion after `admin:` into `ANALYTICS_ADMIN_PASSWORD_HASH`. Keep single quotes around it in `.env.analytics`. The service refuses to start without a valid hash. No client registration grants internal access.
3. Start the stack:

   ```bash
   docker compose --env-file .env.analytics --profile analytics up -d --build
   docker compose --env-file .env.analytics --profile analytics logs --tail=100 analytics jarbis-backend
   ```

4. Open http://localhost:8082 and sign in with the configured internal username and password.

The example uses the local `postgres` service for trading. It does not seed accounts or assets. Use the existing registration/account/order flows to generate completed trades. The current matching engine still needs opposite-side orders. This feature does not change execution pricing or the matching rules.

New OLAP volumes initialize only the schema, not sample data. Existing volumes and records are retained; Flyway applies additive migrations using its own `reporting_migrations` history schema. Never delete a shared volume to apply these migrations. Existing facts are labeled LEGACY, not claimed as live events.

For an isolated sample-data demonstration, load the existing `002_dummy_data.sql` **before the analytics service first applies migrations**, on a disposable OLAP database. Those records then appear under `Legacy / sample data`. Selecting that dataset adjusts the date range to its last 30 days; apply the filters. Do not load fixtures into a database receiving live events: synthetic IDs may collide with real IDs. The consumer refuses to overwrite a legacy trade with a live trade sharing its ID.

## Shared server deployment

Deploy the updated trading backend and analytics service on the designated server, pointing them to the intended OLTP and OLAP databases. Set `BROKERAGE_DB_URL` explicitly to the intended shared OLTP host/port; the default now points to local Compose PostgreSQL. Set `ANALYTICS_DB_URL` to the actual shared OLAP host/port if it is not the Compose `postgres-olap` service. Internal container ports are 5432; host ports are 8090 / 8091.

Only the designated deployment should set `BROKERAGE_ANALYTICS_ENABLED=true`. Every instance sharing an OLTP database must publish through the same Kafka cluster, not separate teammate-local brokers. Ordinary teammate stacks leave the relay disabled. Pending events remain in the database until the designated relay runs.

The analytics HTTP port binds to loopback by default. For remote access, use an SSH tunnel or a TLS reverse proxy and set `ANALYTICS_SECURE_COOKIE=true` behind HTTPS. Sessions expire after 30 minutes of inactivity and logout invalidates the session. CSRF protection covers login/logout; the reporting API is read-only. One internal analyst account is configured for this initial implementation; use individually provisioned staff identities/SSO for a wider rollout.

Changed Compose configuration requires database passwords and the JWT secret through environment variables; it contains no embedded password values. For deployment, use managed secrets and a database role restricted to the analytics schema, with only the privileges required for migrations/projection. Separate migration, projection, and SELECT-only query credentials and process limits are a further hardening step. The API has no OLTP datasource or OLTP credentials.

## Included behavior

- Four cards: executed value, completed fills, distinct active clients, distinct instruments, each with a preceding equal-length-period comparison.
- Daily activity chart with value / fills switch, top instruments, daily unique client chart, and paginated completed-trade blotter.
- Inclusive UTC date filters (maximum 367 days), instrument, account-type segment, currency, and live / legacy dataset.
- A fill counts once per transaction, not once per participant order. Segment filtering selects trades involving that account type and counts only clients in the chosen segment. Account type is the currently available segment proxy; custom commercial/risk segments are not invented.
- Monetary totals remain in one selected settlement currency. No sum across currencies and no implied FX conversion. UNKNOWN is explicitly labeled.
- Empty, loading, failed-request, expired-session and legacy-data states. No third-party scripts or CDN dependencies. Strict CSP, role checks, HttpOnly session cookies, and no names/emails in API responses.
- Latest live ingestion timestamp shown as an event timestamp, not a claim of real-time market-data freshness.

Endpoints: `GET /admin/analytics/options`, `GET /admin/analytics/dashboard`, `GET /admin/analytics/trades`. Dashboard/trades accept `from`, `to`, `instrumentId`, `segment`, `currency`, `dataset`; trades also takes zero-based `page` (25 rows).

## Data delivery and recovery

`TransactionService.completeTransaction` writes execution time and an immutable JSON settlement payload to `analytics_outbox` in the same transaction as the completed trade. A failed settlement rolls back that event. Payloads contain identifiers, account-type snapshots, amounts and timestamps, not names/emails/passwords. The event reports UNKNOWN currency when the existing engine settles differing account currencies; this is not an FX conversion.

The opt-in relay locks one pending row with `FOR UPDATE SKIP LOCKED`, waits for Kafka acknowledgement, then marks it published. A crash between those actions can redeliver; the analytics consumer atomically records the UUID and projects the fill. A second UUID for an already projected live transaction also does not duplicate the fact. Completed facts and participant snapshots are not updated by replay.

The new topic is `trading.settled.v1`, separate from `orders.submitted`. One partition is sufficient initially; consumer group `jarbis-analytics-v1` is independent from settlement. Invalid events or a database outage are retried indefinitely instead of silently dropped. Monitor consumer errors/lag and fix the underlying event/schema issue; a bad event blocks its partition. The deployment uses the existing single-node development Kafka configuration and is not a production HA setup.

Reconciliation queries:

```sql
-- OLTP: pending publication and oldest outstanding event
SELECT count(*), min(created_at) FROM analytics_outbox WHERE published_at IS NULL;
-- OLAP: completed live fill count and latest ingestion
SELECT count(*), max(ingested_at) FROM analytics.transaction_facts WHERE data_source='LIVE';
```

Replay published events by republishing their original outbox payloads through an operational tool. Preserve event IDs. Do not delete processed-event records or reset IDs. For historical trades created before this change, there is no trustworthy execution timestamp in the old entity, so this implementation does not fabricate a live historical backfill.

The API queries indexed fact tables directly, so it does not rely on manually refreshed materialized views. The existing views are preserved for other users.

## Validation

```bash
mvn test
mvn -f analytics-service/pom.xml verify
node --check analytics-service/src/main/resources/static/dashboard.js
```

PostgreSQL integration tests additionally run when `TEST_POSTGRES_URL`, `TEST_POSTGRES_USER`, and `TEST_POSTGRES_PASSWORD` point to a disposable database. They drop and recreate the analytics/migration schemas. Never point test variables at shared data. CI provisions its own PostgreSQL instance.

## Remaining BRS work

This implements BR-16 / BR-17 reporting, isolated from trading, for newly completed transactions. It is not a certification of the whole platform. The earlier review's client ownership checks, acceptance/reservations, current-quote execution, database-level settlement concurrency, financial decimal conversion, immutable legal audit trail, order-status push updates, and client session revocation remain separate trading work. The blotter shows completed fills, not a full legal order lifecycle or rejected-order history. No controls edit balances, clients, or completed trades. An additional differentiated capability under BR-18 still needs a product decision.
