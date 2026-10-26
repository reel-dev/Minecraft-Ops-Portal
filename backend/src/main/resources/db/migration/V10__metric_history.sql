CREATE TABLE metric_sample (
    server_id VARCHAR(64) NOT NULL REFERENCES managed_server(id),
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    cpu_percent DOUBLE PRECISION NOT NULL,
    memory_used BIGINT NOT NULL,
    memory_limit BIGINT NOT NULL,
    network_received BIGINT NOT NULL,
    network_sent BIGINT NOT NULL,
    disk_read BIGINT NOT NULL,
    disk_written BIGINT NOT NULL,
    pids INTEGER NOT NULL,
    PRIMARY KEY(server_id, observed_at)
);
