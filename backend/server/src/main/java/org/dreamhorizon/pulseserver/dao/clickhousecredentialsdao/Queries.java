package org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao;

public class Queries {

  public static final String INSERT_CREDENTIALS =
      "INSERT INTO clickhouse_tenant_credentials "
          + "(tenant_id, clickhouse_username, clickhouse_password_encrypted, encryption_salt, password_digest, is_active) "
          + "VALUES (?, ?, ?, ?, ?, ?) "
          + "ON DUPLICATE KEY UPDATE "
          + "clickhouse_password_encrypted = VALUES(clickhouse_password_encrypted), "
          + "encryption_salt = VALUES(encryption_salt), "
          + "password_digest = VALUES(password_digest), "
          + "updated_at = CURRENT_TIMESTAMP";

  public static final String GET_CREDENTIALS_BY_TENANT =
      "SELECT id, tenant_id, clickhouse_username, clickhouse_password_encrypted, "
          + "encryption_salt, password_digest, is_active, created_at, updated_at "
          + "FROM clickhouse_tenant_credentials WHERE tenant_id = ? AND is_active = TRUE";

  public static final String GET_CREDENTIALS_BY_TENANT_INCLUDING_INACTIVE =
      "SELECT id, tenant_id, clickhouse_username, clickhouse_password_encrypted, "
          + "encryption_salt, password_digest, is_active, created_at, updated_at "
          + "FROM clickhouse_tenant_credentials WHERE tenant_id = ?";

  public static final String GET_ALL_ACTIVE_CREDENTIALS =
      "SELECT id, tenant_id, clickhouse_username, clickhouse_password_encrypted, "
          + "encryption_salt, password_digest, is_active, created_at, updated_at "
          + "FROM clickhouse_tenant_credentials WHERE is_active = TRUE";

  public static final String UPDATE_CREDENTIALS =
      "UPDATE clickhouse_tenant_credentials SET "
          + "clickhouse_password_encrypted = ?, "
          + "encryption_salt = ?, "
          + "password_digest = ?, "
          + "updated_at = CURRENT_TIMESTAMP "
          + "WHERE tenant_id = ?";

  public static final String DEACTIVATE_CREDENTIALS =
      "UPDATE clickhouse_tenant_credentials SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP "
          + "WHERE tenant_id = ?";

  public static final String REACTIVATE_CREDENTIALS =
      "UPDATE clickhouse_tenant_credentials SET is_active = TRUE, updated_at = CURRENT_TIMESTAMP "
          + "WHERE tenant_id = ?";

  public static final String DELETE_CREDENTIALS =
      "DELETE FROM clickhouse_tenant_credentials WHERE tenant_id = ?";

  public static final String INSERT_TENANT =
      "INSERT INTO tenants (tenant_id, name, description, gcp_tenant_id, domain_name, is_active) "
          + "VALUES (?, ?, ?, ?, ?, ?)";

  public static final String GET_TENANT_BY_ID =
      "SELECT tenant_id, name, description, is_active, created_at, updated_at, gcp_tenant_id, domain_name "
          + "FROM tenants WHERE tenant_id = ?";

  public static final String GET_ALL_ACTIVE_TENANTS =
      "SELECT tenant_id, name, description, is_active, created_at, updated_at, gcp_tenant_id, domain_name "
          + "FROM tenants WHERE is_active = TRUE";

  public static final String UPDATE_TENANT =
      "UPDATE tenants SET name = ?, description = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ?";

  public static final String DEACTIVATE_TENANT =
      "UPDATE tenants SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ?";


  public static final String INSERT_AUDIT =
      "INSERT INTO clickhouse_credential_audit (tenant_id, action, performed_by, details) "
          + "VALUES (?, ?, ?, ?)";

  public static final String GET_AUDIT_BY_TENANT =
      "SELECT id, tenant_id, action, performed_by, details, created_at "
          + "FROM clickhouse_credential_audit WHERE tenant_id = ? ORDER BY created_at DESC";

  public static final String GET_RECENT_AUDITS =
      "SELECT id, tenant_id, action, performed_by, details, created_at "
          + "FROM clickhouse_credential_audit ORDER BY created_at DESC LIMIT ?";
}
