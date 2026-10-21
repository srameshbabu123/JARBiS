# Local databases shared through Git

## Startup and updates

Use Docker Compose v2 and your existing `.env.analytics` credentials. New clones
can copy `.env.analytics.example` and fill in its placeholders.

```bash
git pull origin feature/admin-dashboard-ui
docker compose --env-file .env.analytics --profile analytics up -d --build
docker compose --env-file .env.analytics --profile analytics ps -a
```

The OLTP startup sequence is PostgreSQL healthy → Flyway migrations complete →
backend validates the JPA mappings and starts. Flyway mounts `db/oltp` directly,
so a new SQL file does not need a backend image rebuild. The exited migration
job runs again on the next `up`, including when the volume already exists.
`docker compose restart` alone does not run migrations: use `up` after pulling.

The `olap-migrate` job uses the existing checksummed Python SQL runner before
the backend starts, even without the analytics profile. The profile adds the
analytics API and admin UI. OLAP SQL is also bind-mounted. Analytics delivery
is enabled by default; explicitly setting `BROKERAGE_ANALYTICS_ENABLED=false`
disables outbox delivery, leaving source events pending for later replay.

The normal `up --build` command handles application code changes as well as SQL.
If changing schema while a stack is already running, first run `docker compose
--env-file .env.analytics --profile analytics down` (without `-v`) to stop old
application code. Then run the startup command. Volumes are preserved.

## Add shared data or change schema

1. Add a new OLTP file under `db/oltp`, using the next unused version number:
   `V5__add_shared_assets.sql`, for example. Schema and data changes can both
   use versioned SQL. Coordinate version numbers when merging branches.
2. For an entity change, update the Java entity AND add the matching SQL migration.
   Docker uses `ddl-auto=validate`; Hibernate no longer silently changes tables.
3. Test with `up`, commit the migration and related code, and ask teammates to pull
   and run the same startup command.

Example shared-data addition:

```sql
-- Example contents for a NEW migration; do not modify V1 or V2.
INSERT INTO public.assets(id, name, asset_type)
VALUES (-2004, 'GOOGL', 'STOCK');
```

Qualify application tables with `public.` because Flyway stores its bookkeeping
in `oltp_migrations`, a separate schema. The original V2 fixtures have negative IDs. The V3 trading scenario reserves
positive IDs beginning with 900 so the analytics investigation routes can
open them. Its migration advances identity sequences to prevent collisions. Keep IDs and fixture
emails unique, and use explicit foreign keys when adding dependent rows.

For OLAP schema/shared-data additions, add the next `V*.sql` file under
`analytics-service/migrations`; the existing Python runner owns that history.
OLTP market quotes remain in PostgreSQL/Redis and do not need an OLAP migration.

Never modify or delete an already-applied migration. Both runners validate
checksums and fail if history was changed. Fix mistakes with a NEW migration.
Applied data files run once, so restarting does not reset balances or holdings.
Do not put credentials or a production data dump in committed SQL.

## Existing databases and the former server

V1 creates missing tables/indexes without dropping existing compatible JPA
tables. Flyway's history is separate, so an existing Hibernate-created local
volume can adopt the initial migration. V2 adds new development fixture users,
accounts, and holdings; if their reserved IDs or emails conflict, migration
fails rather than replacing existing records. Existing asset symbols are reused.
Incompatible older schemas may require an explicit corrective migration; startup
validation does not silently rewrite them.

Docker now uses its local database services regardless of old remote URL values
in `.env.analytics`. Credentials still come from that file. No process here
connects to, dumps, deletes, or copies records from `10.18.67.77`. Local databases
receive the committed baseline, not the former server's full contents.

For pgAdmin on the same computer, connect to `localhost:8090` (`jarbis_db`,
`jarbis_user`) or `localhost:8091` (`jarbis_olap`, `jarbis_olap_user`) with your
configured passwords. The internal container port is 5432, not 8090/8091.

## Verify

```bash
docker compose --env-file .env.analytics logs --tail=100 oltp-migrate jarbis-backend
docker compose --env-file .env.analytics exec postgres psql -U jarbis_user -d jarbis_db -c "SELECT version, description, success FROM oltp_migrations.flyway_schema_history ORDER BY installed_rank;"
docker compose --env-file .env.analytics exec postgres psql -U jarbis_user -d jarbis_db -c "SELECT id, full_name, email FROM users ORDER BY id;"
docker compose --env-file .env.analytics exec jarbis-backend printenv SPRING_DATASOURCE_URL
```

Successful migration jobs exit with code 0. `oltp-migrate` failure blocks a new
backend start. Inspect its logs and fix the new migration; do not erase volumes
or run Flyway repair to hide a changed historical file.

The GitHub `Local database startup` workflow tests a fresh Docker startup,
Hibernate validation, development login, repeat startup without fixture resets,
new SQL on a persistent volume, checksum rejection, and the OLAP migration chain.

Everyone receives the same committed starting data. Local trades, app inserts,
and independently polled live prices can differ; they are not exported to Git.


## Linked trading scenario (V3)

Four additional users (`cara@example.test`, `dan@example.test`,
`emma@example.test`, `finn@example.test`) use the local fixture credentials.
Each starts with USD 25,000 and opening inventory of 20 shares each of GOOGL,
AMZN, and SPY (average costs 170, 180, and 540). These are opening positions,
not additional unreported executions. Six dated settlements then move money
and shares between these accounts:

| Trade | Buyer | Seller | Symbol | Quantity | Execution price |
| --- | --- | --- | --- | --- | --- |
| 9007001 | Cara | Dan | GOOGL | 5 | 180 |
| 9007002 | Emma | Finn | AMZN | 3 | 190 |
| 9007003 | Dan | Emma | SPY | 8 | 550 |
| 9007004 | Finn | Cara | GOOGL | 4 | 182 |
| 9007005 | Cara | Emma | AMZN | 2 | 192 |
| 9007006 | Emma | Dan | SPY | 6 | 552 |

Each has a matched BUY/SELL pair and follows the engine's resting-order price
rule. Completed orders have zero remaining quantity. A GOOGL buy at 150 and
sell at 220 remain pending because they do not cross. A cancelled AMZN buy
has no execution. Initial cash totals USD 100,000 and share totals are 80 per
symbol; the settlements conserve both and update acquisition averages.
Alice and Bob's existing data is not modified.

The migration records 6 settlement events and 28 order-history events in the
OLTP outboxes. The normal Java relays deliver these same events to OLAP;
there is no separate invented OLAP dataset. Delivery is asynchronous (normally
seconds), retried after failure, and duplicate-safe across restarts. Keep
`BROKERAGE_ANALYTICS_ENABLED=true` to populate OLAP. The additional symbols
are also included in Fauxnance polling, but fixture execution prices remain
fixed historical examples rather than current quotes.

Before changing the sample accounts through the app, verify the complete
scenario and its projection:

```bash
python scripts/verify-shared-trades.py
```

The verifier checks trade IDs, instruments, sides, counterparties, prices,
quantities, timestamps, cash, inventory, cost basis, names, and audit history
across both databases. It is also run by Docker CI before and after repeated
startup. Newly added fixtures run once; local changes are not reset on `up`.


## Expanded scenario (V4): 100 completed trades

V4 adds 94 settlements without changing V1–V3 or the earlier clients' balances.
Eight additional clients (Grace, Hari, Iris, Jon, Kai, Lena, Maya, and Noah)
receive their own USD investment accounts and opening inventories. Their emails
are lowercase first names at `example.test`; they inherit Cara's local fixture credentials. Each begins with USD 100,000 and 200 shares each of GOOGL,
AMZN, and SPY, at the same opening costs defined in V3.

The 94 dated trades span October 1–19, 2026. Sizes range from 1–10 shares;
counterparties follow a repeatable rotation and are always different clients.
Every settlement checks funding and seller inventory, updates balances and
weighted acquisition costs, and creates two filled orders plus four audit events.
The resting sell limit remains the execution price. Prices are illustrative
historical fixture values, not current market quotes.

Combined baseline after V4:

| Record | Count |
| --- | --- |
| Clients / accounts | 14 / 14 |
| Holdings rows | 38 |
| Completed trades | 100 |
| Completed BUY/SELL orders | 200 |
| Pending / cancelled orders | 2 / 1 |
| Order-history events | 404 |
| OLAP trade participants | 200 |

The eight new accounts conserve their USD 800,000 opening cash total and
1,600 shares of each symbol. Trades 9009001–9009094 reference orders
9010001–9010188; event IDs are stable and identity sequences are advanced above
reserved IDs. All 100 trades use the normal outbox delivery path, so OLAP is
not populated from a second independent trade generator.

Run the same Compose update command and `python scripts/verify-shared-trades.py`.
Initial delivery of the larger audit history may take around a minute; the
verifier waits for delivery and checks all 100 trades and all 404 audit events.
The three earlier unfilled/cancelled orders remain without executions. The
verifier checks the untouched shared baseline; trading through those sample
accounts afterward will naturally change their balances and holdings.
