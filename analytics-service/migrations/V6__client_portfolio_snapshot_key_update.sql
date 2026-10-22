-- Normalize snapshot uniqueness by removing dataset dimension from portfolio snapshots.
ALTER TABLE analytics.client_portfolio_snapshots
    DROP CONSTRAINT IF EXISTS client_portfolio_snapshots_pkey;

DROP INDEX IF EXISTS analytics.client_portfolio_asof_idx;

ALTER TABLE analytics.client_portfolio_snapshots
    DROP COLUMN IF EXISTS data_source;

ALTER TABLE analytics.client_portfolio_snapshots
    ADD CONSTRAINT client_portfolio_snapshots_pkey PRIMARY KEY (user_id, currency, captured_at);

CREATE INDEX IF NOT EXISTS client_portfolio_asof_idx
    ON analytics.client_portfolio_snapshots(user_id, currency, captured_at DESC);
