-- Connector-app performance indexes
-- Schema: connector_schema
-- Version: V2
-- Description: Add performance indexes for common query patterns

-- Request States Table Indexes
-- Composite index for status filtering with time ordering (used in dashboard queries)
CREATE INDEX IF NOT EXISTS idx_connector_status_updated ON connector_request_states(status, updated_at DESC);

-- Index for time-based queries (finding recent requests)
CREATE INDEX IF NOT EXISTS idx_connector_submitted ON connector_request_states(submitted_at DESC);

-- Composite index for finding incomplete dual-processing requests
CREATE INDEX IF NOT EXISTS idx_connector_completion_status ON connector_request_states(price_completed, stock_completed) 
    WHERE status = 'ACCEPTED';

-- Index on created_at for repository ordering (used in findAll())
CREATE INDEX IF NOT EXISTS idx_connector_created ON connector_request_states(created_at DESC);

-- State Events Table Indexes
-- Composite index for event filtering by type and time
CREATE INDEX IF NOT EXISTS idx_connector_events_type_time ON connector_state_events(event_type, timestamp DESC);

-- Index for finding events by status
CREATE INDEX IF NOT EXISTS idx_connector_events_status ON connector_state_events(status);

-- Price Queue Table Indexes
-- Index on correlation_id for queue correlation lookups
CREATE INDEX IF NOT EXISTS idx_connector_price_queue_correlation ON connector_price_queue(correlation_id);

-- Composite index for finding queued items by time and correlation
CREATE INDEX IF NOT EXISTS idx_connector_price_queue_time_corr ON connector_price_queue(queued_at, correlation_id);

-- Stock Queue Table Indexes
-- Index on correlation_id for queue correlation lookups
CREATE INDEX IF NOT EXISTS idx_connector_stock_queue_correlation ON connector_stock_queue(correlation_id);

-- Composite index for finding queued items by time and correlation
CREATE INDEX IF NOT EXISTS idx_connector_stock_queue_time_corr ON connector_stock_queue(queued_at, correlation_id);
