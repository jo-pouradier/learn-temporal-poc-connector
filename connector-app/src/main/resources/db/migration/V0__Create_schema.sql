-- V0: Create schema for connector-app
-- This migration runs before all other migrations to ensure the schema exists

CREATE SCHEMA IF NOT EXISTS connector_schema;

-- Grant permissions to temporal user
GRANT ALL PRIVILEGES ON SCHEMA connector_schema TO temporal;

-- Set default privileges for future tables and sequences
ALTER DEFAULT PRIVILEGES IN SCHEMA connector_schema GRANT ALL ON TABLES TO temporal;
ALTER DEFAULT PRIVILEGES IN SCHEMA connector_schema GRANT ALL ON SEQUENCES TO temporal;
