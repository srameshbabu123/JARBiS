CREATE SCHEMA IF NOT EXISTS analytics;

CREATE TABLE IF NOT EXISTS analytics.dim_users
(
    user_id BIGINT PRIMARY KEY,
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS analytics.dim_accounts
(
    account_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    currency VARCHAR(10) NOT NULL
);

CREATE TABLE IF NOT EXISTS analytics.dim_assets
(
    asset_id BIGINT PRIMARY KEY,
    ticker VARCHAR(12) NOT NULL,
    asset_name VARCHAR(200) NOT NULL,
    asset_type VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS analytics.order_facts
(
    order_id BIGINT PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    ticker VARCHAR(12) NOT NULL,
    account_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    side VARCHAR(4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    quantity NUMERIC(18, 8) NOT NULL,
    price NUMERIC(18, 8) NOT NULL,
    order_timestamp TIMESTAMPTZ NOT NULL,
    event_date DATE NOT NULL
);

CREATE INDEX IF NOT EXISTS order_facts_event_date_idx
    ON analytics.order_facts (event_date);

CREATE INDEX IF NOT EXISTS order_facts_asset_date_idx
    ON analytics.order_facts (asset_id, event_date);

CREATE INDEX IF NOT EXISTS order_facts_ticker_date_idx
    ON analytics.order_facts (ticker, event_date);

CREATE TABLE IF NOT EXISTS analytics.transaction_facts
(
    transaction_id BIGINT PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    ticker VARCHAR(12) NOT NULL,
    buy_order_id BIGINT NOT NULL,
    sell_order_id BIGINT NOT NULL,
    buyer_account_id BIGINT NOT NULL,
    seller_account_id BIGINT NOT NULL,
    buyer_user_id BIGINT NOT NULL,
    seller_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    quantity NUMERIC(18, 8) NOT NULL,
    execution_price NUMERIC(18, 8) NOT NULL,
    transaction_timestamp TIMESTAMPTZ NOT NULL,
    event_date DATE NOT NULL
);

CREATE INDEX IF NOT EXISTS transaction_facts_event_date_idx
    ON analytics.transaction_facts (event_date);

CREATE INDEX IF NOT EXISTS transaction_facts_ticker_date_idx
    ON analytics.transaction_facts (ticker, event_date);

CREATE MATERIALIZED VIEW IF NOT EXISTS analytics.daily_order_metrics AS
SELECT
    event_date,
    asset_id,
    side,
    COUNT(*) AS order_count,
    SUM(quantity) AS total_quantity,
    SUM(quantity * price) AS traded_value
FROM analytics.order_facts
GROUP BY event_date, asset_id, side
WITH NO DATA;

CREATE MATERIALIZED VIEW IF NOT EXISTS analytics.daily_transaction_metrics AS
SELECT
    event_date,
    ticker,
    status,
    COUNT(*) AS transaction_count,
    SUM(quantity) AS total_quantity,
    SUM(quantity * execution_price) AS traded_value
FROM analytics.transaction_facts
GROUP BY event_date, ticker, status
WITH NO DATA;

CREATE OR REPLACE VIEW analytics.transaction_details AS
SELECT
    transaction_facts.transaction_id,
    transaction_facts.ticker,
    assets.asset_name,
    assets.asset_type,
    transaction_facts.status,
    transaction_facts.quantity,
    transaction_facts.execution_price,
    transaction_facts.quantity * transaction_facts.execution_price AS traded_value,
    transaction_facts.transaction_timestamp,
    transaction_facts.buyer_user_id,
    buyer.full_name AS buyer_name,
    buyer.email AS buyer_email,
    transaction_facts.buyer_account_id,
    buyer_account.account_type AS buyer_account_type,
    transaction_facts.seller_user_id,
    seller.full_name AS seller_name,
    seller.email AS seller_email,
    transaction_facts.seller_account_id,
    seller_account.account_type AS seller_account_type
FROM analytics.transaction_facts
JOIN analytics.dim_assets assets
    ON assets.asset_id = transaction_facts.asset_id
JOIN analytics.dim_users buyer
    ON buyer.user_id = transaction_facts.buyer_user_id
JOIN analytics.dim_accounts buyer_account
    ON buyer_account.account_id = transaction_facts.buyer_account_id
JOIN analytics.dim_users seller
    ON seller.user_id = transaction_facts.seller_user_id
JOIN analytics.dim_accounts seller_account
    ON seller_account.account_id = transaction_facts.seller_account_id;