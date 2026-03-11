-- V7: Make gcp_tenant_id and domain_name nullable
-- These fields are only needed for Firebase multi-tenancy, which we're moving away from
-- New onboarded users won't have these values

ALTER TABLE tenants 
  MODIFY COLUMN gcp_tenant_id VARCHAR(32) NULL,
  MODIFY COLUMN domain_name VARCHAR(32) NULL;

-- Add comment explaining these are deprecated
ALTER TABLE tenants 
  MODIFY COLUMN gcp_tenant_id VARCHAR(32) NULL COMMENT 'Deprecated - Firebase tenant ID, will be removed in future version',
  MODIFY COLUMN domain_name VARCHAR(32) NULL COMMENT 'Deprecated - Domain name for Firebase tenants, will be removed in future version';
