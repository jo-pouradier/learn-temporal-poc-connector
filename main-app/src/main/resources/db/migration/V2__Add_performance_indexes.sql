-- Main-app performance indexes
-- Schema: main_schema
-- Version: V2
-- Description: Add performance indexes for common query patterns

-- Request States Table Indexes
-- Composite index for status filtering with time ordering (used in dashboard queries)
CREATE INDEX IF NOT EXISTS idx_main_status_updated ON main_request_states(status, updated_at DESC);

-- Index for time-based queries (finding recent requests)
CREATE INDEX IF NOT EXISTS idx_main_submitted ON main_request_states(submitted_at DESC);

-- Composite index for finding incomplete dual-processing requests
CREATE INDEX IF NOT EXISTS idx_main_completion_status ON main_request_states(price_completed, stock_completed) 
    WHERE status = 'ACCEPTED';

-- Index on created_at for repository ordering (used in findAll())
CREATE INDEX IF NOT EXISTS idx_main_created ON main_request_states(created_at DESC);

-- State Events Table Indexes
-- Composite index for event filtering by type and time
CREATE INDEX IF NOT EXISTS idx_main_events_type_time ON main_state_events(event_type, timestamp DESC);

-- Index for finding events by status
CREATE INDEX IF NOT EXISTS idx_main_events_status ON main_state_events(status);

-- Batch Queue Table Indexes
-- Index for finding queued items by order_id (already has UNIQUE constraint, but explicit index helps)
CREATE INDEX IF NOT EXISTS idx_main_queue_order ON main_batch_queue(order_id);
