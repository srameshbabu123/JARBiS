CREATE TABLE IF NOT EXISTS analytics.internal_sessions (
    token_hash TEXT PRIMARY KEY,
    csrf_token TEXT NOT NULL,
    username TEXT,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS internal_sessions_expiry_idx ON analytics.internal_sessions(expires_at);
