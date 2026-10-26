ALTER TABLE operation_job ADD COLUMN command_text VARCHAR(512);
ALTER TABLE operation_job ADD COLUMN result_text VARCHAR(16384);
