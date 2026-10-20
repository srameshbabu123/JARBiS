# JARBiS

## Team setup: local databases from Git

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
`alice@example.test` or `bob@example.test`, password `LocalDev123!`.
These public fixtures are for local development only.

A teammate adds a new file such as `db/oltp/V3__add_shared_assets.sql`, commits
it, and everyone pulls and runs the startup command above. Never edit an
already-applied versioned file. Migrations retain balances, trades, and other
local changes unless a new migration explicitly changes them. App-created data
is local until intentionally represented in a new committed migration.

## Database inspection

```bash
docker compose --env-file .env.analytics exec postgres psql -U jarbis_user -d jarbis_db -c "SELECT id, name, asset_type FROM assets ORDER BY id;"
docker compose --env-file .env.analytics exec postgres psql -U jarbis_user -d jarbis_db -c "SELECT version, description, success FROM oltp_migrations.flyway_schema_history ORDER BY installed_rank;"
docker compose --env-file .env.analytics --profile analytics logs --tail=100 oltp-migrate olap-migrate jarbis-backend analytics
```

Keep Docker volumes when updating or rebuilding. `docker compose down` preserves
them; `down -v` deletes database and Kafka data and is not part of this workflow.
This change does not download the former central database's records.

## Branching

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
