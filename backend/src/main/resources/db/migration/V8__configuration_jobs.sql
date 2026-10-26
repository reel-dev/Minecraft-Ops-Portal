ALTER TABLE operation_job ADD COLUMN configuration_json VARCHAR(4000);
ALTER TABLE operation_job ADD COLUMN configuration_revision VARCHAR(64);
