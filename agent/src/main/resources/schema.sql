CREATE TABLE IF NOT EXISTS agent_operation (
    id UUID PRIMARY KEY,
    operation_type VARCHAR(16) NOT NULL,
    state VARCHAR(16) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
ALTER TABLE agent_operation ADD COLUMN IF NOT EXISTS target_name VARCHAR(16);
ALTER TABLE agent_operation ADD COLUMN IF NOT EXISTS command_text VARCHAR(512);
ALTER TABLE agent_operation ADD COLUMN IF NOT EXISTS result_text VARCHAR(16384);
ALTER TABLE agent_operation ADD COLUMN IF NOT EXISTS snapshot_id VARCHAR(64);
ALTER TABLE agent_operation ADD COLUMN IF NOT EXISTS configuration_payload VARCHAR(4200);
ALTER TABLE agent_operation ADD COLUMN IF NOT EXISTS restore_plan_id UUID;
ALTER TABLE agent_operation ADD COLUMN IF NOT EXISTS mod_plan_id UUID;
CREATE TABLE IF NOT EXISTS backup_snapshot (snapshot_id VARCHAR(64) PRIMARY KEY,created_at VARCHAR(40) NOT NULL,integrity VARCHAR(32) NOT NULL,restore_verified_at VARCHAR(40));
ALTER TABLE backup_snapshot ADD COLUMN IF NOT EXISTS scope VARCHAR(64) NOT NULL DEFAULT 'LOCAL_ENCRYPTED_TEST_WORLD';
ALTER TABLE backup_snapshot ADD COLUMN IF NOT EXISTS boot_verified_at VARCHAR(40);
CREATE TABLE IF NOT EXISTS backup_retention_run(operation_id UUID PRIMARY KEY,started_at VARCHAR(40) NOT NULL,finished_at VARCHAR(40),state VARCHAR(16) NOT NULL,forgotten_count INTEGER NOT NULL);
ALTER TABLE backup_retention_run ADD COLUMN IF NOT EXISTS source_snapshot VARCHAR(64);
ALTER TABLE backup_retention_run ADD COLUMN IF NOT EXISTS inventory_revision VARCHAR(64);
ALTER TABLE backup_retention_run ADD COLUMN IF NOT EXISTS candidate_ids VARCHAR(400000);
ALTER TABLE backup_retention_run ADD COLUMN IF NOT EXISTS forgotten_ids VARCHAR(400000);
CREATE TABLE IF NOT EXISTS restore_plan(id UUID PRIMARY KEY,snapshot_id VARCHAR(64) NOT NULL,created_at VARCHAR(40) NOT NULL,expires_at VARCHAR(40) NOT NULL,target_identity VARCHAR(64) NOT NULL,stage_digest VARCHAR(64) NOT NULL,start_after BOOLEAN NOT NULL,state VARCHAR(32) NOT NULL,operation_id UUID,pre_backup VARCHAR(64),swap_json VARCHAR(400000));
ALTER TABLE restore_plan ADD COLUMN IF NOT EXISTS stage_cleaned BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE IF NOT EXISTS mod_plan(id UUID PRIMARY KEY,created_at VARCHAR(40) NOT NULL,expires_at VARCHAR(40) NOT NULL,target_identity VARCHAR(64) NOT NULL,mod_revision VARCHAR(64) NOT NULL,stage_digest VARCHAR(64) NOT NULL,manifest_json VARCHAR(262144) NOT NULL,changes_json VARCHAR(16384) NOT NULL,java_version VARCHAR(64) NOT NULL,state VARCHAR(32) NOT NULL,operation_id UUID,pre_backup VARCHAR(64),swap_json VARCHAR(400000),stage_cleaned BOOLEAN NOT NULL DEFAULT FALSE);
ALTER TABLE mod_plan ADD COLUMN IF NOT EXISTS import_verified BOOLEAN NOT NULL DEFAULT FALSE;
