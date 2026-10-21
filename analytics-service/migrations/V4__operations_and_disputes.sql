ALTER TABLE analytics.internal_sessions ADD COLUMN IF NOT EXISTS role TEXT;
UPDATE analytics.internal_sessions SET role='ANALYST' WHERE username IS NOT NULL AND role IS NULL;

CREATE TABLE analytics.order_events (
    event_id UUID PRIMARY KEY,
    audit_sequence BIGINT NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    client_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    event_type TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    ingested_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX order_events_order_sequence_idx ON analytics.order_events(order_id,audit_sequence);
CREATE INDEX order_events_client_date_idx ON analytics.order_events(client_id,occurred_at);
CREATE SCHEMA IF NOT EXISTS operations;
CREATE TABLE operations.disputes (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    client_id BIGINT NOT NULL,
    category TEXT NOT NULL,
    reason TEXT NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('OPEN','IN_REVIEW','RESOLVED','CLOSED')),
    assigned_to TEXT,
    opened_by TEXT NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolution TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    evidence JSONB NOT NULL
);
CREATE INDEX disputes_status_date_idx ON operations.disputes(status,opened_at);
CREATE TABLE operations.dispute_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dispute_id UUID NOT NULL REFERENCES operations.disputes(id),
    actor TEXT NOT NULL,
    kind TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    details JSONB NOT NULL
);
CREATE TABLE operations.access_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor TEXT NOT NULL,
    action TEXT NOT NULL,
    subject TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE OR REPLACE FUNCTION operations.reject_evidence_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Evidence is append-only'; END $$;
CREATE TRIGGER order_evidence_immutable BEFORE UPDATE OR DELETE ON analytics.order_events
    FOR EACH ROW EXECUTE FUNCTION operations.reject_evidence_mutation();
CREATE TRIGGER dispute_events_immutable BEFORE UPDATE OR DELETE ON operations.dispute_events
    FOR EACH ROW EXECUTE FUNCTION operations.reject_evidence_mutation();
CREATE TRIGGER access_events_immutable BEFORE UPDATE OR DELETE ON operations.access_events
    FOR EACH ROW EXECUTE FUNCTION operations.reject_evidence_mutation();
CREATE OR REPLACE FUNCTION operations.protect_dispute_origin() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Disputes must be retained'; END IF;
  IF NEW.id IS DISTINCT FROM OLD.id OR NEW.request_id IS DISTINCT FROM OLD.request_id
     OR NEW.order_id IS DISTINCT FROM OLD.order_id OR NEW.client_id IS DISTINCT FROM OLD.client_id
     OR NEW.opened_by IS DISTINCT FROM OLD.opened_by OR NEW.opened_at IS DISTINCT FROM OLD.opened_at
     OR NEW.reason IS DISTINCT FROM OLD.reason OR NEW.category IS DISTINCT FROM OLD.category
     OR NEW.evidence IS DISTINCT FROM OLD.evidence THEN
    RAISE EXCEPTION 'Original dispute evidence cannot be changed';
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER dispute_origin_immutable BEFORE UPDATE OR DELETE ON operations.disputes
    FOR EACH ROW EXECUTE FUNCTION operations.protect_dispute_origin();
