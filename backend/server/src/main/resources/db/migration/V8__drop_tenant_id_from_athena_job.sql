-- V8: Drop tenant_id from athena_job table
-- tenant_id is no longer needed — each project has its own Athena table
-- (otel_data_{project_id}) and all lookups use project_id.

-- Drop foreign key first
ALTER TABLE athena_job DROP FOREIGN KEY fk_athena_job_tenant;

-- Drop indexes that reference tenant_id
DROP INDEX idx_athena_job_tenant ON athena_job;
DROP INDEX idx_athena_job_tenant_project ON athena_job;

-- Drop the column
ALTER TABLE athena_job DROP COLUMN tenant_id;
