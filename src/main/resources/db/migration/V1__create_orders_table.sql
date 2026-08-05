-- V1: Create orders and order_items tables

CREATE TABLE IF NOT EXISTS orders (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number    VARCHAR(50)     NOT NULL UNIQUE,
    customer_id     VARCHAR(100)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    total_amount    NUMERIC(12,2)   NOT NULL DEFAULT 0.00,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_orders_customer_id ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);

CREATE TABLE IF NOT EXISTS order_items (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID            NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id      VARCHAR(100)    NOT NULL,
    product_name    VARCHAR(200)    NOT NULL,
    quantity        INTEGER         NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(12,2)   NOT NULL,
    total_price     NUMERIC(12,2)   NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items(order_id);

-- Seed an order number sequence for reference
-- (actual generation is in Java code)
