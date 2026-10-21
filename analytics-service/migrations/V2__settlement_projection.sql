ALTER TABLE analytics.transaction_facts ADD COLUMN IF NOT EXISTS currency VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE analytics.transaction_facts ADD COLUMN IF NOT EXISTS data_source VARCHAR(20) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE analytics.transaction_facts ADD COLUMN IF NOT EXISTS ingested_at TIMESTAMPTZ;
UPDATE analytics.transaction_facts t SET currency = a.currency
FROM analytics.dim_accounts a WHERE a.account_id = t.buyer_account_id AND t.currency = 'UNKNOWN';
CREATE INDEX IF NOT EXISTS transaction_reporting_filter_idx ON analytics.transaction_facts (data_source, currency, transaction_timestamp);
CREATE TABLE IF NOT EXISTS analytics.processed_events (
    event_id UUID PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS analytics.trade_participants (
    transaction_id BIGINT NOT NULL REFERENCES analytics.transaction_facts(transaction_id),
    user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    account_type VARCHAR(50) NOT NULL,
    side VARCHAR(4) NOT NULL,
    PRIMARY KEY (transaction_id, side)
);
INSERT INTO analytics.trade_participants
SELECT t.transaction_id, t.buyer_user_id, t.buyer_account_id, a.account_type, 'BUY'
FROM analytics.transaction_facts t JOIN analytics.dim_accounts a ON a.account_id=t.buyer_account_id
ON CONFLICT DO NOTHING;
INSERT INTO analytics.trade_participants
SELECT t.transaction_id, t.seller_user_id, t.seller_account_id, a.account_type, 'SELL'
FROM analytics.transaction_facts t JOIN analytics.dim_accounts a ON a.account_id=t.seller_account_id
ON CONFLICT DO NOTHING;
