-- Periodic Java snapshots; no OLTP access from reporting queries.
CREATE TABLE analytics.client_portfolio_snapshots (
    user_id BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    holdings_cost NUMERIC NOT NULL CHECK (holdings_cost >= 0),
    data_source VARCHAR(20) NOT NULL DEFAULT 'LIVE' CHECK (data_source IN ('LIVE','LEGACY')),
    PRIMARY KEY (user_id,currency,data_source,captured_at)
);
CREATE INDEX client_portfolio_asof_idx ON analytics.client_portfolio_snapshots(user_id,currency,data_source,captured_at DESC);
CREATE INDEX transaction_activity_idx ON analytics.transaction_facts(data_source,transaction_timestamp);
CREATE INDEX participant_client_idx ON analytics.trade_participants(user_id,transaction_id);
