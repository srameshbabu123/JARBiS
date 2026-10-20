# Quick startup: shared local databases

This guide applies to `feature/admin-dashboard-ui`. Every teammate runs their
own local databases. Git shares the schema and baseline data; it does not copy
one teammate's live database into everyone else's.

## 1. Get the branch

From the repository root:

```bash
git checkout feature/admin-dashboard-ui
git pull origin feature/admin-dashboard-ui
```

For a new clone:

```bash
git clone https://github.com/srameshbabu123/JARBiS.git
cd JARBiS
git checkout feature/admin-dashboard-ui
```

Prerequisites: Docker, Docker Compose, and optionally Python 3 for verification.
The commands below use `docker-compose`. If your installation uses
`docker compose`, replace the command accordingly. The Python verifier detects both.

## 2. Configure your local environment

If you do not already have `.env.analytics`, create it:

```bash
cp .env.analytics.example .env.analytics
```

Keep existing working settings when updating. Set these values in that file:

| Setting | What to configure |
| --- | --- |
| `BROKERAGE_DB_PASSWORD` | Your local OLTP database password |
| `ANALYTICS_DB_PASSWORD` | Your local OLAP database password |
| `JWT_SECRET` | A random secret of at least 32 bytes |
| `BROKERAGE_ANALYTICS_ENABLED` | `true` so trades and audit events reach OLAP |
| `BROKERAGE_MARKET_DATA_API_KEY` | Your Fauxnance API key |
| `BROKERAGE_MARKET_DATA_POLLING_ENABLED` | `true`, or `false` to test without live quotes |
| `ANALYTICS_ADMIN_USER` | Your admin dashboard username |
| `ANALYTICS_ADMIN_PASSWORD_HASH` | BCrypt hash of your chosen dashboard password |

Do not commit or share your environment file changes. Do not paste an API key
or password into a migration. Existing PostgreSQL volumes require their original
database passwords; editing the environment file does not change database roles.

To generate a JWT secret locally, run `openssl rand -hex 32` and save the result
in your environment file.

If you need a dashboard password hash, first fill in the database passwords and
JWT secret, then run:

```bash
docker-compose --env-file .env.analytics build analytics
docker-compose --env-file .env.analytics run --rm --no-deps analytics python -c "import bcrypt,getpass; print(bcrypt.hashpw(getpass.getpass('Choose dashboard password: ').encode(),bcrypt.gensalt()).decode())"
```

Copy the resulting hash into `ANALYTICS_ADMIN_PASSWORD_HASH`, surrounded by
single quotes so Compose does not expand its dollar signs. Use the password
you chose to log in, not the hash.

Operations access is optional. Either configure a distinct operations username
and its BCrypt password hash, or leave BOTH settings empty:

```env
ANALYTICS_OPERATIONS_USER=
ANALYTICS_OPERATIONS_PASSWORD_HASH=
```

## 3. Start the stack

```bash
docker-compose --env-file .env.analytics --profile analytics up -d --build
docker-compose --env-file .env.analytics --profile analytics ps -a
```

Docker waits for PostgreSQL, applies pending migrations, and then starts the
backend. Hibernate validates the schema against the Java entities.
The `oltp-migrate`, `olap-migrate`, and `kafka-init` jobs normally exit with code 0;
they are successful startup jobs, not failed services.

| Service | Address from your computer |
| --- | --- |
| Backend | http://localhost:8081 |
| Admin dashboard | http://localhost:8083 |
| Analytics API | http://localhost:8082 |
| OLTP PostgreSQL | localhost:8090 / database `jarbis_db` / user `jarbis_user` |
| OLAP PostgreSQL | localhost:8091 / database `jarbis_olap` / user `jarbis_olap_user` |

These localhost addresses assume Docker runs on the same computer.
For EC2, use your existing SSH tunnel or approved server access.

Inside Docker, OLTP uses `postgres:5432` and OLAP uses `postgres-olap:5432`.
The stack does not connect to a central master database.

## 4. Verify before using the sample accounts

```bash
python3 scripts/verify-shared-trades.py
```

The verifier needs no extra Python packages. Run it on the host, where Docker
is available. It waits up to 90 seconds for outbox delivery.

The untouched baseline contains **14 clients, 14 accounts, 38 holdings rows,
100 completed trades, 200 filled orders, 2 pending orders, 1 cancelled order,
and 404 order-history events**. The verifier compares the actual OLTP and OLAP
trade records and checks cash, inventory, and acquisition costs.

To inspect migration history:

```bash
docker-compose --env-file .env.analytics exec -T postgres psql -U jarbis_user -d jarbis_db -c "SELECT version, description, success FROM oltp_migrations.flyway_schema_history ORDER BY installed_rank;"
```

## 5. Understand what stays synchronized

- SQL migrations give everyone the same committed starting schema and data.
- OLTP outboxes deliver completed trades and order histories to that teammate's OLAP.
- Fauxnance writes through the backend to that teammate's OLTP, then Redis.
- Market quotes are not copied to OLAP.
- Local orders, trades, and live prices can differ between teammates.

An existing open order can fill a seeded pending order after startup. The strict
baseline verifier may then fail even though trading is correct. Check the new
transaction and its OLAP counterpart; do not reset statuses or erase data just
to make the baseline test pass.

## 6. Share new data with the team

1. Pull the latest branch and coordinate the next unused version number.
2. Add a NEW file under `db/oltp`, currently `V5__description.sql`.
3. For schema changes, update the Java entity and add matching SQL.
4. For shared trades, include the matched orders, funds/inventory movements,
   settlement outbox records, and audit events. See V3/V4 as worked examples.
   A direct insert into `transactions` alone does not populate OLAP.
5. Test locally, extend reconciliation checks when expanding the baseline,
   then commit and push.
6. Teammates pull and run the startup command again. Existing volumes receive
   the pending migration once.

For OLAP-only schema or shared-data changes, add the next versioned SQL file
under `analytics-service/migrations`.

Never edit or delete an already-applied migration. Correct it with a new one.
SQL history checksums reject altered files. App-created data is not automatically
exported to SQL or Git. Coordinate explicit fixture IDs to avoid collisions and
advance identity sequences when inserting fixed positive IDs, as V4 does.

## 7. Daily updates and troubleshooting

```bash
git pull origin feature/admin-dashboard-ui
docker-compose --env-file .env.analytics --profile analytics up -d --build
docker-compose --env-file .env.analytics --profile analytics logs --tail=100 oltp-migrate olap-migrate jarbis-backend analytics
```

When deploying a schema change to a running stack, stop the old application
first with `docker-compose --env-file .env.analytics --profile analytics down`,
then run `up` again. This preserves volumes.

- Missing `python`: use `python3`.
- Docker command fails: check `docker-compose version` or `docker compose version`.
- Migration fails: inspect that migration job's logs; do not erase history.
- OLAP is empty: check analytics is enabled and inspect backend relay logs.
- Baseline assertion fails after trading: inspect the changed records; local
  activity legitimately changes the baseline.

**Do not use `down -v` for updates. It deletes the database and Kafka volumes.**

More detail: [local database workflow](local-databases.md).
