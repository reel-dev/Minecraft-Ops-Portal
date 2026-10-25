CREATE TABLE managed_server (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    reported_minecraft_version VARCHAR(64),
    reported_loader VARCHAR(32),
    verified_at TIMESTAMP WITH TIME ZONE
);
INSERT INTO managed_server (id, display_name, reported_minecraft_version, reported_loader)
VALUES ('main', 'Elbracht SMP', '26.3', 'FABRIC');

CREATE TABLE operation_job (
    id UUID PRIMARY KEY,
    server_id VARCHAR(64) NOT NULL REFERENCES managed_server(id),
    actor VARCHAR(200) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    operation_type VARCHAR(16) NOT NULL,
    reason VARCHAR(240) NOT NULL,
    state VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    error_code VARCHAR(64),
    CONSTRAINT unique_operation_request UNIQUE(server_id, actor, idempotency_key)
);
CREATE INDEX job_server_state ON operation_job(server_id, state);

CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    actor VARCHAR(200) NOT NULL,
    server_id VARCHAR(64) NOT NULL,
    job_id UUID NOT NULL REFERENCES operation_job(id),
    action VARCHAR(32) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Portalrollen sind serverbezogen und unabhängig von Minecraft-OP.
CREATE TABLE server_grant (
    subject VARCHAR(200) NOT NULL,
    server_id VARCHAR(64) NOT NULL REFERENCES managed_server(id),
    portal_role VARCHAR(16) NOT NULL CHECK (portal_role IN ('VIEWER','OPERATOR','ADMIN')),
    PRIMARY KEY (subject, server_id)
);
