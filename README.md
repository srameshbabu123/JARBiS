We have decided on GitFlow Based branching strategy because we want to ensure we have development branch that acts as a buffer zone for our shared workspace. We want to ensure main has the cleanest working code that is ready to deploy, so rather than merging into main and having code that doesn't work, we want to use development as the zone to ensure that when our features come together, it works and main is only merged when it has the version that is ready to be deployed. On top of this strategy, we are planning to have our own member's development, so that we can have our own Jenkins pipeline to test our features. 

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

## Which database contains my assets?

Compose creates and uses jarbis_db with user jarbis_user. The backend's
SPRING_DATASOURCE_URL overrides the localhost/jarbis setting in
application.properties. These are separate databases: an asset inserted into
a local jarbis database is not automatically copied into jarbis_db.

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
5432, database jarbis_db, user jarbis_user and the password configured in
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
