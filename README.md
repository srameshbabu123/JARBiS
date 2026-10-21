# JARBiS

## Team setup

This project uses one shared development database hosted on the Linux server.
Team members do not need to set a password or datasource variable manually.

### 1. Start the application

From the repository root:

```bash
docker-compose up -d --build
docker-compose ps
```

The local backend is available at:

```text
http://localhost:8081
```

The backend container automatically connects to the shared OLTP database:

```text
Server: 10.18.67.77
Port: 8090
Database: jarbis_db
```

Your machine must be able to reach the Linux server. Test it with:

```bash
timeout 5 bash -c '</dev/tcp/10.18.67.77/8090' \
	&& echo "OLTP reachable" \
	|| echo "OLTP blocked"
```

If the result is `OLTP blocked`, ask the server administrator to allow your
VM or computer to access TCP port `8090`. Do not change the datasource URL to
`postgres:5432`; that points to the local PostgreSQL container.

### 2. Access the shared OLTP database directly

Use pgAdmin with:

```text
Host: 10.18.67.77
Port: 8090
Database: jarbis_db
Username: jarbis_user
Password: jarbis_password
```

Or use the PostgreSQL client:

```bash
PGPASSWORD='jarbis_password' psql \
	-h 10.18.67.77 \
	-p 8090 \
	-U jarbis_user \
	-d jarbis_db
```

### 3. Access the shared OLAP database directly

Use pgAdmin with:

```text
Host: 10.18.67.77
Port: 8091
Database: jarbis_olap
Username: jarbis_olap_user
Password: jarbis_olap_password
Schema: analytics
```

The main OLAP objects are:

```text
analytics.order_facts
analytics.transaction_facts
analytics.transaction_details
analytics.daily_order_metrics
analytics.daily_transaction_metrics
```

### Important database rule

Running `docker-compose up` starts a local PostgreSQL container, but the
backend is configured to use the shared Linux database at `10.18.67.77:8090`.
The local PostgreSQL container is not the shared master.

This command accesses the local database on your own machine:

```bash
docker-compose exec postgres psql -U jarbis_user -d jarbis_db
```

Use pgAdmin or the remote `psql` command above when you need to view or edit
the shared master database. Multiple team members can connect at the same
time, but everyone is using the same development data.

Do not run `docker-compose down -v` on the shared Linux server. It deletes
Docker database volumes.

## Run the container stack

Each developer runs their own OLTP, OLAP, Redis, and Kafka containers. Shared
schema and sample-data changes travel through Git as versioned SQL migrations.
Docker applies pending migrations on startup, including existing volumes.

Copy `.env.analytics.example` to `.env.analytics` if you do not already have
local settings, and replace the secret placeholders. Existing database volumes
need their original database passwords; changing an environment variable does
not change a PostgreSQL role's password.

```bash
git pull origin feature/admin-dashboard-ui
docker compose --env-file .env.analytics --profile analytics up -d --build
docker compose --env-file .env.analytics --profile analytics ps -a
```

Use Docker Compose v2 (the `docker compose` command). The `oltp-migrate`,
`olap-migrate`, and `kafka-init` jobs normally finish with exit code 0.

| Service | Local address |
| --- | --- |
| Backend | http://localhost:8081 |
| Analytics API | http://localhost:8082 |
| Admin UI | http://localhost:8083 |
| OLTP / jarbis_db / jarbis_user | localhost:8090 |
| OLAP / jarbis_olap / jarbis_olap_user | localhost:8091 |

Inside Docker, the backend uses `postgres:5432/jarbis_db`; analytics uses
`postgres-olap:5432/jarbis_olap`. The Compose URLs are explicitly local, so old
remote URL settings in an environment file cannot redirect the application.
Fauxnance quotes write to the local OLTP `market_data_snapshots` table and Redis.
Market quotes are not copied to OLAP.

See [the local database workflow](docs/local-databases.md) for sample logins,
how to add data, migration rules, verification, and existing-volume behavior.
Do not import the root `database.sql`; it is an obsolete historical schema.

## Shared development data

The initial local fixtures include Alice and Bob, investment accounts with
USD 10,000 each, AAPL/MSFT/NVDA assets, and example holdings. Development logins:
`alice@example.test` or `bob@example.test`; use your local fixture credentials.
These public fixtures are for local development only.

V3 adds Cara, Dan, Emma, and Finn with funded opening portfolios, 6 completed
trades, 12 matched completed orders, 2 non-crossing pending orders, and 1
cancelled order. GOOGL/AMZN/SPY are added to polling. The same settlements and
order histories flow from OLTP outboxes into OLAP, with matching IDs, quantities,
prices, and counterparties. See the linked scenario table in the workflow guide.
`BROKERAGE_ANALYTICS_ENABLED` defaults to true; keep it enabled for delivery.

V4 expands this to **100 completed trades**, **200 filled orders**, and **14
clients with 14 accounts**, while retaining the 2 pending and 1 cancelled order.
Eight new funded clients support the additional 94 settlements. The verifier
checks all trades, portfolio movements, and 404 audit events against OLAP.

## OLAP database

The stack includes a second, isolated PostgreSQL instance for analytics. The
backend continues to use `postgres` and database `jarbis_db` on host port
`8090`. The OLAP instance is `postgres-olap` and uses database `jarbis_olap`
on host port `8091`.

The two instances have separate credentials, Docker volumes, and schemas:

| Purpose | Host | Port | Database | User |
| --- | --- | --- | --- | --- |
| OLTP | `localhost` | `8090` | `jarbis_db` | `jarbis_user` |
| OLAP | `localhost` | `8091` | `jarbis_olap` | `jarbis_olap_user` |

The backend is configured to use the shared development OLTP master at
`10.18.67.77:8090`. Team members can run `docker-compose up` without setting
datasource variables manually, provided their device can route to that
private IP. The local `postgres` service is still available for inspection,
but it is not the backend datasource dependency.

The OLAP initialization script creates the `analytics.order_facts` table and
the `analytics.daily_order_metrics` materialized view. It is intentionally
not populated by the backend yet. A Kafka consumer, scheduled ETL job, or CDC
pipeline should copy committed events from the OLTP database into the OLAP
fact table.

To inspect the OLAP database:

```bash
docker compose exec postgres-olap psql -U jarbis_olap_user -d jarbis_olap \
	-c "SELECT current_database(), current_schema();"
```

To refresh its aggregate view after loading facts:

```bash
docker compose exec postgres-olap psql -U jarbis_olap_user -d jarbis_olap \
	-c "REFRESH MATERIALIZED VIEW analytics.daily_order_metrics;"
```

Do not use `docker compose down -v` when you want to retain either database.
Remove only `jarbis_postgres_olap_data` when you intentionally want to
recreate the OLAP instance from its initialization script.

## Which database contains my assets?

Compose creates a local `jarbis_db` with user `jarbis_user`, while the backend
uses the shared `jarbis_db` at `10.18.67.77:8090`. These are separate database
instances: an asset inserted into the local database is not automatically
copied into the shared master.

## Database inspection

```bash
docker compose --env-file .env.analytics exec postgres psql -U jarbis_user -d jarbis_db -c "SELECT id, name, asset_type FROM assets ORDER BY id;"
docker compose --env-file .env.analytics exec postgres psql -U jarbis_user -d jarbis_db -c "SELECT version, description, success FROM oltp_migrations.flyway_schema_history ORDER BY installed_rank;"
docker compose --env-file .env.analytics --profile analytics logs --tail=100 oltp-migrate olap-migrate jarbis-backend analytics
```

Keep Docker volumes when updating or rebuilding. `docker compose down` preserves
them; `down -v` deletes database and Kafka data and is not part of this workflow.
This change does not download the former central database's records.

For a database GUI running on the same machine, use host localhost, port
8090, database jarbis_db, user jarbis_user and the password configured in
docker-compose.yml. If you run the backend outside Docker against this
database, override its datasource URL and credentials accordingly.

We use GitFlow: feature branches integrate into `development`, which acts as a
buffer before working changes merge to `main`.

## Order settlement

POST /orders creates a PENDING order and publishes its ID to Kafka.
Automatic settlement requires an opposite-side order for the same asset with
buy price greater than or equal to sell price. The buyer must have enough
balance and the seller must hold enough of the asset. Asset existence alone
does not cause an order to fill.

Existing pending orders whose Kafka publication failed are not automatically
republished by this configuration change. Once Kafka is healthy, a newly
submitted order for the same asset triggers loading that asset's open orders
into the matching book.
