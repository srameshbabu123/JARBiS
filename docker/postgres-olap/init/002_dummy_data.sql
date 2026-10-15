-- Repeatable analytics seed data for local development.
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

ALTER TABLE analytics.order_facts
    ADD COLUMN IF NOT EXISTS ticker VARCHAR(12);

INSERT INTO analytics.dim_users (user_id, full_name, email)
VALUES
(100000001, 'Alice Johnson', 'alice.johnson@example.com'),
(100000002, 'Bob Smith', 'bob.smith@example.com'),
(100000003, 'Charlie Brown', 'charlie.brown@example.com'),
(100000004, 'Diana Williams', 'diana.williams@example.com'),
(100000005, 'Ethan Davis', 'ethan.davis@example.com'),
(100000006, 'Fiona Miller', 'fiona.miller@example.com')
ON CONFLICT (user_id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    email = EXCLUDED.email;

INSERT INTO analytics.dim_accounts (account_id, user_id, account_type, currency)
VALUES
(101, 100000001, 'INVESTMENT', 'USD'),
(102, 100000002, 'SAVINGS', 'USD'),
(103, 100000003, 'CHECKING', 'EUR'),
(104, 100000004, 'CRYPTO', 'USD'),
(105, 100000005, 'INVESTMENT', 'INR')
ON CONFLICT (account_id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    account_type = EXCLUDED.account_type,
    currency = EXCLUDED.currency;

INSERT INTO analytics.dim_assets (asset_id, ticker, asset_name, asset_type)
VALUES
(1, 'AAPL', 'Apple Inc.', 'STOCK'),
(2, 'MSFT', 'Microsoft Corporation', 'STOCK'),
(3, 'NVDA', 'NVIDIA Corporation', 'STOCK'),
(4, 'TSLA', 'Tesla Inc.', 'STOCK'),
(5, 'AMZN', 'Amazon.com Inc.', 'STOCK'),
(6, 'BTC', 'Bitcoin', 'CRYPTO'),
(7, 'ETH', 'Ethereum', 'CRYPTO')
ON CONFLICT (asset_id) DO UPDATE SET
    ticker = EXCLUDED.ticker,
    asset_name = EXCLUDED.asset_name,
    asset_type = EXCLUDED.asset_type;

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

INSERT INTO analytics.order_facts
(order_id, asset_id, ticker, account_id, user_id, side, status, quantity, price, order_timestamp, event_date)
VALUES
(900001, 1, 'AAPL', 101, 100000001, 'BUY',  'COMPLETED',  25.00000000,   178.25000000, '2024-01-15 09:30:00+00', '2024-01-15'),
(900002, 1, 'AAPL', 102, 100000002, 'SELL', 'COMPLETED',  10.00000000,   182.60000000, '2024-03-22 14:15:00+00', '2024-03-22'),
(900003, 2, 'MSFT', 103, 100000003, 'BUY',  'COMPLETED',  40.00000000,   405.75000000, '2024-06-10 10:05:00+00', '2024-06-10'),
(900004, 3, 'NVDA', 104, 100000004, 'BUY',  'COMPLETED',  12.00000000,   121.40000000, '2024-09-18 15:45:00+00', '2024-09-18'),
(900005, 4, 'TSLA', 105, 100000005, 'SELL', 'CANCELLED',   8.00000000,   248.90000000, '2024-12-02 16:20:00+00', '2024-12-02'),
(900006, 5, 'AMZN', 101, 100000001, 'BUY',  'COMPLETED',  18.00000000,   151.30000000, '2025-01-08 11:10:00+00', '2025-01-08'),
(900007, 6, 'BTC',  102, 100000002, 'BUY',  'COMPLETED',   0.25000000, 42500.00000000, '2025-02-14 08:30:00+00', '2025-02-14'),
(900008, 7, 'ETH',  103, 100000003, 'SELL', 'COMPLETED',   2.50000000,  2380.00000000, '2025-04-03 12:00:00+00', '2025-04-03'),
(900009, 1, 'AAPL', 104, 100000004, 'BUY',  'PENDING',     60.00000000,   191.75000000, '2025-05-21 13:40:00+00', '2025-05-21'),
(900010, 2, 'MSFT', 105, 100000005, 'SELL', 'COMPLETED',  15.00000000,   452.10000000, '2025-07-11 09:15:00+00', '2025-07-11'),
(900011, 3, 'NVDA', 101, 100000001, 'BUY',  'COMPLETED',  75.00000000,   142.80000000, '2025-08-29 14:50:00+00', '2025-08-29'),
(900012, 4, 'TSLA', 102, 100000002, 'BUY',  'REJECTED',   100.00000000,   315.60000000, '2025-10-06 17:05:00+00', '2025-10-06'),
(900013, 5, 'AMZN', 103, 100000003, 'SELL', 'COMPLETED',  22.00000000,   218.45000000, '2025-11-19 10:25:00+00', '2025-11-19'),
(900014, 6, 'BTC',  104, 100000004, 'BUY',  'COMPLETED',   0.75000000, 58300.00000000, '2025-12-31 23:30:00+00', '2025-12-31'),
(900015, 7, 'ETH',  105, 100000005, 'BUY',  'PENDING',      8.00000000,  3320.00000000, '2026-01-12 09:00:00+00', '2026-01-12'),
(900016, 1, 'AAPL', 101, 100000001, 'SELL', 'COMPLETED',  35.00000000,   224.90000000, '2026-02-20 15:35:00+00', '2026-02-20'),
(900017, 2, 'MSFT', 102, 100000002, 'BUY',  'COMPLETED',  55.00000000,   418.60000000, '2026-03-17 10:45:00+00', '2026-03-17'),
(900018, 3, 'NVDA', 103, 100000003, 'SELL', 'COMPLETED',  20.00000000,   168.25000000, '2026-04-28 13:20:00+00', '2026-04-28'),
(900019, 4, 'TSLA', 104, 100000004, 'BUY',  'COMPLETED',  7.00000000,    278.35000000, '2026-05-30 16:10:00+00', '2026-05-30'),
(900020, 5, 'AMZN', 105, 100000005, 'BUY',  'COMPLETED',  125.00000000,  205.75000000, '2026-06-15 11:55:00+00', '2026-06-15'),
(900021, 6, 'BTC',  101, 100000001, 'SELL', 'COMPLETED',   0.10000000, 68400.00000000, '2026-07-09 07:40:00+00', '2026-07-09'),
(900022, 7, 'ETH',  102, 100000002, 'BUY',  'COMPLETED',   3.75000000,  3850.00000000, '2026-08-23 18:25:00+00', '2026-08-23'),
(900023, 1, 'AAPL', 103, 100000003, 'BUY',  'CANCELLED',  200.00000000,  231.10000000, '2026-09-05 12:30:00+00', '2026-09-05'),
(900024, 2, 'MSFT', 104, 100000004, 'SELL', 'COMPLETED',  30.00000000,   475.50000000, '2026-09-18 14:05:00+00', '2026-09-18'),
(900025, 3, 'NVDA', 105, 100000005, 'BUY',  'PENDING',     90.00000000,   179.95000000, '2026-10-01 09:35:00+00', '2026-10-01'),
(900026, 4, 'TSLA', 101, 100000001, 'SELL', 'COMPLETED',  11.00000000,   302.70000000, '2026-10-03 15:15:00+00', '2026-10-03'),
(900027, 5, 'AMZN', 102, 100000002, 'BUY',  'COMPLETED',  42.00000000,   218.60000000, '2026-10-07 10:50:00+00', '2026-10-07'),
(900028, 6, 'BTC',  103, 100000003, 'BUY',  'PENDING',      0.05000000, 70125.00000000, '2026-10-09 20:10:00+00', '2026-10-09'),
(900029, 7, 'ETH',  104, 100000004, 'SELL', 'CANCELLED',   1.25000000,  4100.00000000, '2026-10-10 06:45:00+00', '2026-10-10'),
(900030, 1, 'AAPL', 105, 100000005, 'BUY',  'COMPLETED',  15.00000000,   233.45000000, '2026-10-12 13:00:00+00', '2026-10-12')
ON CONFLICT (order_id) DO UPDATE SET
    asset_id = EXCLUDED.asset_id,
    ticker = EXCLUDED.ticker,
    account_id = EXCLUDED.account_id,
    user_id = EXCLUDED.user_id,
    side = EXCLUDED.side,
    status = EXCLUDED.status,
    quantity = EXCLUDED.quantity,
    price = EXCLUDED.price,
    order_timestamp = EXCLUDED.order_timestamp,
    event_date = EXCLUDED.event_date;

REFRESH MATERIALIZED VIEW analytics.daily_order_metrics;

INSERT INTO analytics.transaction_facts
(transaction_id, asset_id, ticker, buy_order_id, sell_order_id,
 buyer_account_id, seller_account_id, buyer_user_id, seller_user_id,
 status, quantity, execution_price, transaction_timestamp, event_date)
VALUES
(910001, 1, 'AAPL', 900001, 900002, 101, 102, 100000001, 100000002, 'COMPLETED', 10.00000000, 180.25000000, '2024-03-22 14:16:00+00', '2024-03-22'),
(910002, 2, 'MSFT', 900003, 900010, 103, 105, 100000003, 100000005, 'COMPLETED', 15.00000000, 430.50000000, '2024-07-11 09:16:00+00', '2024-07-11'),
(910003, 3, 'NVDA', 900004, 900018, 104, 103, 100000004, 100000003, 'COMPLETED', 12.00000000, 145.75000000, '2024-09-18 15:46:00+00', '2024-09-18'),
(910004, 4, 'TSLA', 900019, 900005, 104, 105, 100000004, 100000005, 'FAILED',     8.00000000, 260.00000000, '2024-12-02 16:21:00+00', '2024-12-02'),
(910005, 5, 'AMZN', 900006, 900013, 101, 103, 100000001, 100000003, 'COMPLETED', 18.00000000, 185.90000000, '2025-01-08 11:11:00+00', '2025-01-08'),
(910006, 6, 'BTC',  900007, 900021, 102, 101, 100000002, 100000001, 'COMPLETED',  0.10000000, 52000.00000000, '2025-02-14 08:31:00+00', '2025-02-14'),
(910007, 7, 'ETH',  900015, 900008, 105, 103, 100000005, 100000003, 'PENDING',   2.50000000,  3500.00000000, '2025-04-03 12:01:00+00', '2025-04-03'),
(910008, 1, 'AAPL', 900009, 900016, 104, 101, 100000004, 100000001, 'COMPLETED', 35.00000000, 205.25000000, '2025-05-21 13:41:00+00', '2025-05-21'),
(910009, 2, 'MSFT', 900017, 900024, 102, 104, 100000002, 100000004, 'COMPLETED', 30.00000000, 447.80000000, '2026-03-17 10:46:00+00', '2026-03-17'),
(910010, 3, 'NVDA', 900025, 900018, 105, 103, 100000005, 100000003, 'PENDING',   20.00000000, 175.00000000, '2026-04-28 13:21:00+00', '2026-04-28'),
(910011, 4, 'TSLA', 900019, 900026, 104, 101, 100000004, 100000001, 'COMPLETED',  7.00000000, 290.60000000, '2026-05-30 16:11:00+00', '2026-05-30'),
(910012, 5, 'AMZN', 900020, 900027, 105, 102, 100000005, 100000002, 'COMPLETED', 42.00000000, 212.20000000, '2026-06-15 11:56:00+00', '2026-06-15'),
(910013, 6, 'BTC',  900028, 900021, 103, 101, 100000003, 100000001, 'COMPLETED',  0.05000000, 69500.00000000, '2026-07-09 07:41:00+00', '2026-07-09'),
(910014, 7, 'ETH',  900022, 900029, 102, 104, 100000002, 100000004, 'FAILED',     1.25000000, 4000.00000000, '2026-08-23 18:26:00+00', '2026-08-23'),
(910015, 1, 'AAPL', 900030, 900016, 105, 101, 100000005, 100000001, 'COMPLETED', 15.00000000, 232.90000000, '2026-10-12 13:01:00+00', '2026-10-12')
ON CONFLICT (transaction_id) DO UPDATE SET
    asset_id = EXCLUDED.asset_id,
    ticker = EXCLUDED.ticker,
    buy_order_id = EXCLUDED.buy_order_id,
    sell_order_id = EXCLUDED.sell_order_id,
    buyer_account_id = EXCLUDED.buyer_account_id,
    seller_account_id = EXCLUDED.seller_account_id,
    buyer_user_id = EXCLUDED.buyer_user_id,
    seller_user_id = EXCLUDED.seller_user_id,
    status = EXCLUDED.status,
    quantity = EXCLUDED.quantity,
    execution_price = EXCLUDED.execution_price,
    transaction_timestamp = EXCLUDED.transaction_timestamp,
    event_date = EXCLUDED.event_date;

REFRESH MATERIALIZED VIEW analytics.daily_transaction_metrics;
