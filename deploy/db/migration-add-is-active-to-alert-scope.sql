-- Migration script to add is_active column to Alert_Scope table
-- This script should be run on existing databases that don't have the is_active column

USE pulse_db;

-- Add is_active column with default value TRUE
ALTER TABLE Alert_Scope 
ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

-- Update all existing rows to be active (in case default wasn't applied)
UPDATE Alert_Scope 
SET is_active = TRUE 
WHERE is_active IS NULL;

