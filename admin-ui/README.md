# Velocity admin frontend

A separate internal frontend for the existing Python API. Buildless ES modules, CSS and accessible SVG charts; no external fonts, libraries, sample numbers or client trading access. Keeping this separate avoids changes to the client-facing Angular application.

## Run with the backend

From the repository root, configure `.env.analytics` using the example. Keep it untracked. Provision separate analyst and operations BCrypt hashes. Then:

```bash
docker compose --env-file .env.analytics --profile analytics up -d --build analytics admin-ui
```

Open **http://localhost:8083**. The Python API starts and applies migrations, including V5. For EC2, use an SSH tunnel from your computer:

```bash
ssh -L 8083:localhost:8083 ec2-user@YOUR_SERVER
```

Use the same identity-file/network settings as your existing SSH connection. Open localhost:8083 on your computer. For HTTPS deployment, set secure cookies and configure a trusted reverse proxy; default Compose bindings are loopback-only. The nginx proxy makes browser/API requests same-origin, forwards cookies and does not expose database credentials. Python's 8082 API remains available separately.

Restart/build the Java backend with `BROKERAGE_ANALYTICS_ENABLED=true` and its OLAP settings to populate live evidence and portfolio snapshots. An initial portfolio snapshot runs when scheduled tasks start, then hourly (`brokerage.analytics.portfolio-delay-ms`). If the schema or database is not ready, it retries in one minute. Start analytics/migrations before the Java backend when enabling snapshots for the first time. Java snapshots aggregate source holdings periodically; admin queries never query OLTP.

## User workflows

- Analyst: sign in, select period/instrument/currency/dataset and two independent client filters, apply, switch quantity/trades/notional chart, inspect daily data, export CSV. Dataset defaults to LIVE; select LEGACY explicitly for historical fixtures.
- Operations: all reporting plus order/client/status/date searches, exact order/trade lookup, full captured timeline and before/after evidence, coverage warnings, CSV evidence, open a case with frozen evidence, review, assign/unassign to a provisioned operations user, add notes, resolve, close or reopen with a reason. A stale version is rejected; reload the case before retrying.
- All data comes from the API. Authentication expiry returns to sign-in. Analysts cannot access operations endpoints even by direct requests.

## Metric and segmentation contract

`summary.value`, daily `value`, instrument `value` and trade `value` now mean **SUM(quantity)**, counting a matched fill once even if both clients match. The frontend labels this “Total quantity traded” and never formats it as money. `notional` separately retains SUM(quantity × execution_price), in the selected currency. This is an API semantic change from the previous monetary `value`.

`portfolioSize`: UNDER_10K (<10000), BETWEEN_10K_100K (10000 through 100000 inclusive), OVER_100K (>100000). Based on the client's holdings **at purchase cost**, across all their accounts in the selected currency, excluding cash. This is not current market valuation or FX conversion. The source lacks full market valuations; use a priced portfolio projection before claiming market-value bands. Missing historical snapshots are excluded, not assumed zero. Java snapshots are LIVE; LEGACY bands require separately sourced historical snapshots and never borrow a current LIVE client valuation. Latest snapshot on/before report end is used; freshness is displayed. History begins when the collector is deployed, so older periods may have no coverage. USD thresholds are dollar amounts only when USD is selected.

`activityLevel`, evaluated at the report end (UTC), across all currencies/instruments in the selected LIVE or LEGACY dataset:

- ACTIVE: at least one completed trade since the first of that calendar month.
- OCCASIONAL: last completed trade within the inclusive 90 calendar days ending on that date, but before the current month.
- DORMANT: no completed trade in that 90-day window.

Activity means recorded history, not proof of inactivity if ingestion/history is incomplete. Dormant clients often contribute zero trades to a recent-period report. Both filters and optional account-type filter must match the **same participant**. Active-client counts include only matching clients, not the opposite side. Previous-period comparisons reevaluate the same filter definitions at that period's end. No zero-baseline growth is invented.

## Verification

```bash
node --check admin-ui/app.js
node --test admin-ui/tests/*.test.mjs
cd analytics-service
python -m pytest -v
```

PostgreSQL tests require the disposable `admin_dashboard_test` database documented in the backend guide. Java tests also cover snapshot persistence and replay. CI runs all three suites. Browser visual QA is not available in the coding environment; manually verify login, responsive layout, keyboard navigation, chart switching, case workflow, session expiry and actual deployed Java-to-OLAP freshness.

## Requirements

BR-16/17: historical period/instrument/client segments, quantity/activity/instrument insights, isolated reporting. BR-14/15: operations screens expose existing durable evidence and gaps; a screen does not repair missing source quote/history. BR-18 candidate: dispute case management adds documented evidence, assignment and resolution. Existing gaps in `docs/admin-brs-coverage.md` still apply. No automated refunds, risk engine or compliance-certification claim.

## David's investigation workspace

Operations users land directly on Trade Operations. A searchable completed-trade list and adjacent details panel expose quantity, execution price, currency, time and both client/order sides. Choose the affected client's order to review human-readable lifecycle facts, actor/time, cash/holding changes and explicit coverage gaps, then open a dispute with the existing frozen-evidence API. Legacy fills can be browsed even without lifecycle events; unavailable stages are never fabricated. Switch record type to search latest order lifecycle states. Dates/dataset/currency scopes are stated alongside filters; dates over 367 days are explained before an API request. Initial operations dates use the latest available dataset window, with LEGACY selected only when LIVE is absent.

## Load more demo data

The opt-in loader adds synthetic records to **OLAP only**, in LEGACY. Nothing is sent to Kafka or the trading database. Start/update the analytics container first so migrations are applied, then run from the repository root:

```bash
docker compose --env-file .env.analytics --profile analytics up -d --build analytics admin-ui
docker compose --env-file .env.analytics --profile analytics exec analytics python -m analytics.demo --allow-demo-data
```

It adds one batch per UTC day: 216 completed trades, 54 synthetic clients, six DEMO instruments, USD/EUR/INR, all portfolio-size/activity combinations, 54 historical LEGACY portfolio snapshots, 1,314 explicitly synthetic lifecycle events (including pending and failed-attempt examples), and eight disputes covering all four statuses. Disputes are attributed to DEMO_SEED and assigned to the configured operations username when available. Viewed records generate normal access-audit entries. The loader prints example trade/order IDs.

Select **Legacy history**, USD (or EUR/INR), all instruments, and last 30 days for recent charts. Use the last 180 days/custom range to see dormant historical activity. David can use Completed trades or Order lifecycle history, inspect a DEMO record and open another dispute. Synthetic instrument names, reason text and evidence identify the data clearly. These fixtures illustrate the workflow, not real market quotes, real customers or proof that live ingestion works.

Rerunning on the same UTC day does not duplicate, rewrite or restore records you have edited. A different day appends another batch. The entire load commits or rolls back together; reserved-ID collisions fail rather than overwrite. It never deletes evidence, updates existing rows or changes live-ingestion markers. Use a demo environment where possible: LEGACY metrics will include these fixtures. No automatic startup seeding and no destructive cleanup are provided because evidence retention protections remain in force.
