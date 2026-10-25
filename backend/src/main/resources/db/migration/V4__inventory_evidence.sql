ALTER TABLE managed_server ADD COLUMN inventory_revision BIGINT NOT NULL DEFAULT 0;
CREATE TABLE inventory_evidence (
    server_id VARCHAR(64) NOT NULL REFERENCES managed_server(id),
    field_key VARCHAR(64) NOT NULL,
    evidence_value VARCHAR(2000) NOT NULL,
    source VARCHAR(240) NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    recorded_by VARCHAR(200) NOT NULL,
    revision BIGINT NOT NULL,
    PRIMARY KEY (server_id, field_key)
);
ALTER TABLE audit_event ALTER COLUMN job_id DROP NOT NULL;
ALTER TABLE audit_event ADD COLUMN subject_key VARCHAR(64);
ALTER TABLE audit_event ADD COLUMN revision BIGINT;
