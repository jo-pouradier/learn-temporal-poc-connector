-- V0: Create schema for main-app
-- This migration runs before all other migrations to ensure the schema exists

CREATE SCHEMA IF NOT EXISTS main_schema;

-- Grant permissions to temporal user
GRANT ALL PRIVILEGES ON SCHEMA main_schema TO temporal;

-- Set default privileges for future tables and sequences
ALTER DEFAULT PRIVILEGES IN SCHEMA main_schema GRANT ALL ON TABLES TO temporal;
ALTER DEFAULT PRIVILEGES IN SCHEMA main_schema GRANT ALL ON SEQUENCES TO temporal;
