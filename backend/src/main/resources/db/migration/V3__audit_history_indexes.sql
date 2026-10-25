CREATE INDEX audit_server_history ON audit_event(server_id, occurred_at DESC, id DESC);
CREATE INDEX audit_server_job_history ON audit_event(server_id, job_id, occurred_at DESC, id DESC);
