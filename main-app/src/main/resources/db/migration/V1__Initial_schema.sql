-- Main-app initial schema (PostgreSQL)
-- Schema: main_schema
-- Tables: main_request_states, main_state_events, main_batch_queue

CREATE TABLE IF NOT EXISTS main_request_states (
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

CREATE INDEX IF NOT EXISTS idx_main_correlation ON main_request_states(correlation_id);
CREATE INDEX IF NOT EXISTS idx_main_status ON main_request_states(status);
CREATE INDEX IF NOT EXISTS idx_main_updated ON main_request_states(updated_at);

CREATE TABLE IF NOT EXISTS main_state_events (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES main_request_states(order_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_main_events_order ON main_state_events(order_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_main_events_type ON main_state_events(event_type);

CREATE TABLE IF NOT EXISTS main_batch_queue (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL UNIQUE,
    queued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES main_request_states(order_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_main_queue_time ON main_batch_queue(queued_at);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_main_request_states_updated_at BEFORE UPDATE
    ON main_request_states FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
