package org.dreamhorizon.pulseserver.dao.tenantdao;

public class TenantQueries {

  public static final String INSERT_TENANT =
      "INSERT INTO tenants (tenant_id, name, description, gcp_tenant_id, domain_name, is_active) "
          + "VALUES (?, ?, ?, ?, ?, ?)";

  public static final String GET_TENANT_BY_ID =
      "SELECT tenant_id, name, description, is_active, created_at, updated_at, gcp_tenant_id, domain_name "
          + "FROM tenants WHERE tenant_id = ?";

  public static final String GET_ALL_ACTIVE_TENANTS =
      "SELECT tenant_id, name, description, is_active, created_at, updated_at, gcp_tenant_id, domain_name "
          + "FROM tenants WHERE is_active = TRUE";

  public static final String GET_ALL_TENANTS =
      "SELECT tenant_id, name, description, is_active, created_at, updated_at, gcp_tenant_id, domain_name "
          + "FROM tenants";

  public static final String UPDATE_TENANT =
      "UPDATE tenants SET name = ?, description = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ?";

  public static final String DEACTIVATE_TENANT =
      "UPDATE tenants SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ?";

  public static final String ACTIVATE_TENANT =
      "UPDATE tenants SET is_active = TRUE, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ?";

  public static final String DELETE_TENANT =
      "DELETE FROM tenants WHERE tenant_id = ?";

  public static final String CHECK_TENANT_EXISTS =
      "SELECT COUNT(*) as count FROM tenants WHERE tenant_id = ?";

  public static final String GET_TENANT_BY_GCP_TENANT_ID =
      "SELECT tenant_id, name, description, is_active, created_at, updated_at, gcp_tenant_id, domain_name "
          + "FROM tenants WHERE gcp_tenant_id = ?";

  public static final String GET_TENANT_BY_DOMAIN_NAME =
      "SELECT tenant_id, name, description, is_active, created_at, updated_at, gcp_tenant_id, domain_name "
          + "FROM tenants WHERE domain_name = ?";
}
