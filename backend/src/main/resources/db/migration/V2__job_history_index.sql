CREATE INDEX job_server_history ON operation_job(server_id, created_at DESC, id DESC);
