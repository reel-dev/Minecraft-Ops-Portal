CREATE TABLE IF NOT EXISTS agent_operation (
    id UUID PRIMARY KEY,
    operation_type VARCHAR(16) NOT NULL,
    state VARCHAR(16) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
