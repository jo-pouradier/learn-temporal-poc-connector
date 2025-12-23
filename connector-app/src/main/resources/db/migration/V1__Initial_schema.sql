-- Connector-app initial schema (PostgreSQL)
-- Schema: connector_schema
-- Tables: connector_request_states, connector_state_events, connector_price_queue, connector_stock_queue

CREATE TABLE IF NOT EXISTS connector_request_states (
    order_id VARCHAR(255) PRIMARY KEY NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,

    completed_at TIMESTAMP,
    error TEXT,
    price_completed BOOLEAN DEFAULT FALSE,
    stock_completed BOOLEAN DEFAULT FALSE,
    original_request_json JSONB,
    price_callback_json JSONB,
    stock_callback_json JSONB,
    final_response_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_connector_correlation ON connector_request_states(correlation_id);
CREATE INDEX IF NOT EXISTS idx_connector_status ON connector_request_states(status);
CREATE INDEX IF NOT EXISTS idx_connector_updated ON connector_request_states(updated_at);

CREATE TABLE IF NOT EXISTS connector_state_events (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES connector_request_states(order_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_connector_events_order ON connector_state_events(order_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_connector_events_type ON connector_state_events(event_type);

CREATE TABLE IF NOT EXISTS connector_price_queue (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    queued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(order_id)
);

CREATE INDEX IF NOT EXISTS idx_connector_price_queue_time ON connector_price_queue(queued_at);
CREATE INDEX IF NOT EXISTS idx_connector_price_queue_order ON connector_price_queue(order_id);

CREATE TABLE IF NOT EXISTS connector_stock_queue (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    stock INTEGER NOT NULL,
    queued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(order_id)
);

CREATE INDEX IF NOT EXISTS idx_connector_stock_queue_time ON connector_stock_queue(queued_at);
CREATE INDEX IF NOT EXISTS idx_connector_stock_queue_order ON connector_stock_queue(order_id);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_connector_request_states_updated_at BEFORE UPDATE
    ON connector_request_states FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
