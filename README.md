We have decided on GitFlow Based branching strategy because we want to ensure we have development branch that acts as a buffer zone for our shared workspace. We want to ensure main has the cleanest working code that is ready to deploy, so rather than merging into main and having code that doesn't work, we want to use development as the zone to ensure that when our features come together, it works and main is only merged when it has the version that is ready to be deployed. On top of this strategy, we are planning to have our own member's development, so that we can have our own Jenkins pipeline to test our features. 

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

From the repository root:

```bash
docker compose up -d --build
docker compose ps -a
docker compose logs --tail=100 kafka jarbis-backend
```

The API is available at http://localhost:8081 and its health endpoint is
http://localhost:8081/actuator/health. The backend listens on port 8080 inside
Docker. Kafka runs in single-node KRaft mode with persistent storage.
Container clients use kafka:19092; applications running on the host use
localhost:9092. The kafka-init service creates orders.submitted and exits
successfully before the backend starts. An exited kafka-init container with
exit code 0 is expected. This plaintext Kafka setup is for local development.

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

To inspect the exact database used by the container backend:

```bash
docker compose exec postgres psql -U jarbis_user -d jarbis_db -c "SELECT current_database();"
docker compose exec postgres psql -U jarbis_user -d jarbis_db -c "SELECT id, name, price, asset_type FROM assets ORDER BY id;"
```

Use the numeric assets.id from this result as assetId in POST /orders.
If the asset is missing, add it to this database using the current entity
schema (assets has id, name, price and asset_type). Do not import database.sql:
it describes an older schema with string asset_id and asset_name columns,
and Compose does not run it as an initialization script.

For a database GUI running on the same machine, use host localhost, port
8090, database jarbis_db, user jarbis_user and the password configured in
docker-compose.yml. If you run the backend outside Docker against this
database, override its datasource URL and credentials accordingly.

The existing postgres_data volume is retained when rebuilding the backend.
Do not use docker compose down -v if you want to retain database and Kafka
data. Changing POSTGRES_DB does not rename or migrate an existing database.

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
