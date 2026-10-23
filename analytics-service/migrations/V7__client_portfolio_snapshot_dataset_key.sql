-- Normalize snapshot uniqueness to include the dataset dimension.
ALTER TABLE analytics.client_portfolio_snapshots
    ADD COLUMN IF NOT EXISTS data_source VARCHAR(20) NOT NULL DEFAULT 'LIVE';

UPDATE analytics.client_portfolio_snapshots
SET data_source = 'LIVE'
WHERE data_source IS NULL;

ALTER TABLE analytics.client_portfolio_snapshots
    DROP CONSTRAINT IF EXISTS client_portfolio_snapshots_data_source_check;

ALTER TABLE analytics.client_portfolio_snapshots
    ADD CONSTRAINT client_portfolio_snapshots_data_source_check
    CHECK (data_source IN ('LIVE', 'LEGACY'));

ALTER TABLE analytics.client_portfolio_snapshots
    DROP CONSTRAINT IF EXISTS client_portfolio_snapshots_pkey;

DROP INDEX IF EXISTS analytics.client_portfolio_asof_idx;

ALTER TABLE analytics.client_portfolio_snapshots
    ADD CONSTRAINT client_portfolio_snapshots_pkey
    PRIMARY KEY (user_id, currency, data_source, captured_at);

CREATE INDEX IF NOT EXISTS client_portfolio_asof_idx
    ON analytics.client_portfolio_snapshots(user_id, currency, data_source, captured_at DESC);