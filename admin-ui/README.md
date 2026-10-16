# Velocity admin workspace

A separate internal frontend connected to the Python analytics/operations API. Buildless ES modules, CSS and SVG charts; no external fonts or browser libraries. The client-facing Angular application is separate.

## Start

Configure `.env.analytics` in the repository root using the example. Set existing OLAP credentials and separate BCrypt hashes for analyst/operations users. Keep the file untracked.

```bash
docker compose --env-file .env.analytics --profile analytics up -d --build analytics admin-ui
```

Open http://localhost:8083. For EC2, forward the port from your computer with your normal SSH identity/network settings:

```bash
ssh -L 8083:localhost:8083 ec2-user@YOUR_SERVER
```

nginx proxies API requests on the same origin. Database passwords stay in backend configuration. Configure secure cookies and a trusted reverse proxy for HTTPS deployment. The UI and API query OLAP only. Java captures and copies trading data separately; no trading backend restart is needed to browse existing OLAP history or load demos.

## One trading history

The dashboard combines **all OLAP records**, including demo fixtures, by default. There is no dataset selector. Source tags LIVE/LEGACY remain in storage for provenance, collision detection and backward compatibility, not as user-facing categories. No rows are rewritten or relabelled. Optional explicit source filters remain available to older API clients; requests without a dataset use ALL.

Period, instrument, currency and the two independent client filters apply across the combined history. Monetary currencies are not added together. The default period remains the last 30 days; older trades require another date range, at most 367 inclusive days. The client explains invalid ranges before requesting data.

## Analyst workflow

Sign in, choose period/instrument/currency, portfolio size and activity level, then Apply. Switch quantity/trade/notional charts, inspect daily data or export CSV. SQL aggregates in OLAP; pandas fills missing dates. Reports do not run against the live trading database.

`value` means SUM(quantity), each matched fill counted once. It is labelled “Total quantity traded”, never money. `notional` is SUM(quantity × execution_price) in the chosen currency. Comparisons use an adjacent equal-length period; zero baselines do not generate fictional percentage growth.

Portfolio bands: UNDER_10K (<10000), BETWEEN_10K_100K (10000–100000 inclusive), OVER_100K (>100000). They use holdings at purchase cost across the client's accounts in the selected currency, excluding cash. This is not market valuation or FX conversion. Latest available snapshot on/before the period end is used across source tags. Missing snapshots are excluded, not treated as zero. Java captures cost snapshots hourly, retrying failures each minute (`brokerage.analytics.portfolio-delay-ms`, minimum one minute). History begins at deployment; the demo loader supplies synthetic historical snapshots.

Activity at period end (UTC), across currencies/instruments and all OLAP sources:

- ACTIVE: traded since the first of that calendar month.
- OCCASIONAL: last trade in the inclusive 90-day window, but not this month.
- DORMANT: no recorded trade in that window.

Dormant clients may contribute zero trades in recent periods. Both filters must match the same participant; opposite parties cannot satisfy different filters. Client counts include matching clients only. Prior-period cohorts are reevaluated at that period's end.

## David's operations workflow

Operations users land on Trade Operations. Search completed trades with client/order/trade IDs, dates and currency. Select a trade for execution details and both client/order sides. Choose the affected order to inspect human-readable pricing/cash/holding facts, actor/time, coverage warnings and full evidence. Open a dispute with a frozen evidence snapshot. Switch record type for latest lifecycle-state searches; exact IDs bypass date filters.

Manage cases with notes, provisioned operations-user assignment, review, resolution, closure and reopening with a reason. Optimistic versions prevent stale overwrites. Missing older lifecycle/quote evidence is labelled, never fabricated. Access and case actions use the existing backend audit records. This does not reverse trades or issue refunds.

## Add demo data

```bash
docker compose --env-file .env.analytics --profile analytics exec analytics python -m analytics.demo --allow-demo-data
```

Rebuild analytics first after pulling the loader. One batch per UTC day adds 216 completed trades, 54 clients, six DEMO instruments, USD/EUR/INR, every size/activity combination, 54 historical portfolio snapshots, 1,314 synthetic lifecycle events (including pending/failure attempts), and eight disputes across all statuses. These records appear in normal reports alongside existing OLAP records, as requested. Use 30 days for recent activity or 180 days for dormant history. The command prints example trade/order IDs.

Synthetic identities/evidence retain DEMO attribution; source ingestion counters are not changed. Reruns on the same day do not duplicate or overwrite records. Loads are atomic, use explicit column lists and fail on reserved-ID collisions. They never change OLTP, send Kafka events, run automatically or delete protected evidence.

## Verification and requirements

```bash
node --check admin-ui/app.js
node --test admin-ui/tests/*.test.mjs
cd analytics-service
python -m pytest -v
```

Database tests require the disposable admin_dashboard_test database. CI includes Java, PostgreSQL, mixed-source reporting, segment, demo rollback/replay and frontend checks. Browser visual QA and deployed end-to-end testing remain manual checks.

BR-16/17: isolated period/instrument/client analysis and business insights. BR-14/15: expose captured durable evidence and its gaps. BR-18 candidate: documented dispute workflow. Original source/valuation/retention/risk gaps in docs/admin-brs-coverage.md remain; the frontend does not establish full compliance.
