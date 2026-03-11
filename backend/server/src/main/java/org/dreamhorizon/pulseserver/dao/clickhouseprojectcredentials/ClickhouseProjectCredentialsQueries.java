package org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials;

/**
 * SQL queries for ClickHouse project credentials operations.
 */
public class ClickhouseProjectCredentialsQueries {
    
    public static final String INSERT_CREDENTIALS =
        "INSERT INTO clickhouse_project_credentials " +
        "(project_id, clickhouse_username, clickhouse_password_encrypted, " +
        "encryption_salt, password_digest, is_active) " +
        "VALUES (?, ?, ?, ?, ?, ?)";
    
    public static final String GET_CREDENTIALS_BY_PROJECT_ID =
        "SELECT id, project_id, clickhouse_username, clickhouse_password_encrypted, " +
        "encryption_salt, password_digest, is_active, created_at, updated_at " +
        "FROM clickhouse_project_credentials " +
        "WHERE project_id = ? AND is_active = TRUE";
    
    public static final String DEACTIVATE_CREDENTIALS =
        "UPDATE clickhouse_project_credentials SET is_active = FALSE " +
        "WHERE project_id = ?";
    
    public static final String UPDATE_CREDENTIALS =
        "UPDATE clickhouse_project_credentials " +
        "SET clickhouse_password_encrypted = ?, encryption_salt = ?, password_digest = ? " +
        "WHERE project_id = ?";

    // Audit queries
    public static final String INSERT_AUDIT =
        "INSERT INTO clickhouse_project_credential_audit (project_id, action, performed_by, details) " +
        "VALUES (?, ?, ?, ?)";

    public static final String GET_AUDIT_BY_PROJECT =
        "SELECT id, project_id, action, performed_by, details, created_at " +
        "FROM clickhouse_project_credential_audit WHERE project_id = ? ORDER BY created_at DESC";

    public static final String GET_RECENT_AUDITS =
        "SELECT id, project_id, action, performed_by, details, created_at " +
        "FROM clickhouse_project_credential_audit ORDER BY created_at DESC LIMIT ?";
}

