-- V2: Create audit_log table for order events

CREATE TABLE IF NOT EXISTS audit_log (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID            NOT NULL,
    event_type      VARCHAR(50)     NOT NULL,
    event_data      TEXT,
    previous_status VARCHAR(20),
    new_status      VARCHAR(20),
    actor_id        VARCHAR(100),
    timestamp       TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_order_id ON audit_log(order_id);
CREATE INDEX idx_audit_log_timestamp ON audit_log(timestamp);
CREATE INDEX idx_audit_log_event_type ON audit_log(event_type);
CREATE INDEX idx_audit_log_actor_id ON audit_log(actor_id);