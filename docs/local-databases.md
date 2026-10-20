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

With the analytics profile, `olap-migrate` uses the existing checksummed Python
SQL runner before the analytics API starts. Its SQL folder is also bind-mounted.
Without that profile, OLTP migration and backend startup still work; OLAP API
and its migration job are not started.

The normal `up --build` command handles application code changes as well as SQL.
If changing schema while a stack is already running, first run `docker compose
--env-file .env.analytics --profile analytics down` (without `-v`) to stop old
application code. Then run the startup command. Volumes are preserved.

## Add shared data or change schema

1. Add a new OLTP file under `db/oltp`, using the next unused version number:
   `V3__add_shared_assets.sql`, for example. Schema and data changes can both
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
in `oltp_migrations`, a separate schema. Negative IDs are reserved for shared
fixtures; normal identity-generated IDs stay positive. Keep IDs and fixture
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
