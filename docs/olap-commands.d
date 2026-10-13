# JARBiS OLAP Database Commands
# Run these commands from the repository root.

# Source-of-truth rule
# OLTP is the source of truth. Debezium should capture OLTP changes and an
# ingest consumer should populate OLAP. Do not independently invent matching
# facts in both databases in production.

# Load aligned development data into OLTP first
# This creates users, accounts, assets, orders, and transactions whose IDs
# match the sample OLAP facts. Run this before testing a CDC pipeline.
docker-compose exec -T postgres psql \
  -v ON_ERROR_STOP=1 \
  -U jarbis_user \
  -d jarbis_db \
  < docker/postgres/seed/001_aligned_dummy_data.sql

# Important: the current OLTP Transaction entity has no execution timestamp.
# Add an executed_at column before CDC if OLAP needs the true trade time, or
# derive the timestamp in the CDC consumer from an event/ingestion timestamp.

# 1. Open a PostgreSQL shell in the OLAP database
# Use this for interactive commands such as \dt and \d.
docker-compose exec postgres-olap psql \
  -U jarbis_olap_user \
  -d jarbis_olap

# 2. List all schemas
# \dn is a psql command. It must be run inside psql.
\dn

# 3. List all tables and views
\dt *.*
\dv *.*

# 4. List only tables in the analytics schema
\dt analytics.*

# 5. List only views in the analytics schema
\dv analytics.*

# 6. List materialized views in the analytics schema
\dm analytics.*

# 7. Describe the order facts table
\d analytics.order_facts

# 8. Describe the transaction facts table
\d analytics.transaction_facts

# 9. Describe the daily order metrics view
\d analytics.daily_order_metrics

# 10. Describe the daily transaction metrics view
\d analytics.daily_transaction_metrics

# 11. Describe the user, account, and asset dimensions
\d analytics.dim_users
\d analytics.dim_accounts
\d analytics.dim_assets

# 12. View a transaction with buyer and seller details resolved
SELECT *
FROM analytics.transaction_details
ORDER BY transaction_id;

# 13. View every order fact
SELECT *
FROM analytics.order_facts
ORDER BY order_timestamp;

# 14. View every transaction fact
SELECT *
FROM analytics.transaction_facts
ORDER BY transaction_timestamp;

# 15. View daily order metrics
SELECT *
FROM analytics.daily_order_metrics
ORDER BY event_date, asset_id, side;

# 16. View daily transaction metrics
SELECT *
FROM analytics.daily_transaction_metrics
ORDER BY event_date, ticker, status;

# 17. Count rows in each main OLAP table
SELECT 'order_facts' AS table_name, COUNT(*) AS row_count
FROM analytics.order_facts
UNION ALL
SELECT 'transaction_facts', COUNT(*)
FROM analytics.transaction_facts
UNION ALL
SELECT 'daily_order_metrics', COUNT(*)
FROM analytics.daily_order_metrics
UNION ALL
SELECT 'daily_transaction_metrics', COUNT(*)
FROM analytics.daily_transaction_metrics;

# 18. Summarize orders by ticker
SELECT
  ticker,
  COUNT(*) AS order_count,
  SUM(quantity) AS total_quantity,
  ROUND(SUM(quantity * price), 2) AS order_value
FROM analytics.order_facts
GROUP BY ticker
ORDER BY order_value DESC;

# 19. Summarize completed transactions by ticker
SELECT
  ticker,
  COUNT(*) AS transaction_count,
  SUM(quantity) AS total_quantity,
  ROUND(SUM(quantity * execution_price), 2) AS traded_value
FROM analytics.transaction_facts
WHERE status = 'COMPLETED'
GROUP BY ticker
ORDER BY traded_value DESC;

# 20. Summarize transactions by status
SELECT
  status,
  COUNT(*) AS transaction_count,
  SUM(quantity) AS total_quantity,
  ROUND(SUM(quantity * execution_price), 2) AS value
FROM analytics.transaction_facts
GROUP BY status
ORDER BY status;

# 21. Filter transactions by date range
SELECT *
FROM analytics.transaction_facts
WHERE transaction_timestamp >= DATE '2026-01-01'
  AND transaction_timestamp < DATE '2027-01-01'
ORDER BY transaction_timestamp;

# 22. Refresh both materialized views after loading new facts
REFRESH MATERIALIZED VIEW analytics.daily_order_metrics;
REFRESH MATERIALIZED VIEW analytics.daily_transaction_metrics;

# 23. Run one command without opening an interactive psql shell
# This example lists all analytics tables.
docker-compose exec -T postgres-olap psql \
  -U jarbis_olap_user \
  -d jarbis_olap \
  -c "\\dt analytics.*"

# 24. Connect from a database GUI such as DBeaver or pgAdmin
# Host: localhost
# Port: 5433
# Database: jarbis_olap
# User: jarbis_olap_user
# Password: jarbis_olap_password
# Schema: analytics
