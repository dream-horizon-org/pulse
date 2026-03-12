-- Update projects table to support API keys and enhanced project management
-- API keys are used for SDK authentication (format: pulse_{projectId}_sk_{random})

ALTER TABLE projects 
    ADD COLUMN api_key VARCHAR(255) UNIQUE COMMENT 'SDK authentication key' AFTER description;

-- Add index for fast API key lookups
ALTER TABLE projects 
    ADD INDEX idx_project_api_key (api_key);

-- Note: tier_id will be added to tenants table by colleague
-- ALTER TABLE tenants ADD COLUMN tier_id INT NOT NULL DEFAULT 1;
-- ALTER TABLE tenants ADD CONSTRAINT fk_tenant_tier FOREIGN KEY (tier_id) REFERENCES tiers(id);
