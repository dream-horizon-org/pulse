package org.dreamhorizon.pulseserver.dao.tenant;

public class TenantQueries {

  private static final String TENANT_COLUMNS =
      "tenant_id, name, description, tier_id, is_active, created_at, updated_at, gcp_tenant_id, domain_name";

  public static final String INSERT_TENANT =
      "INSERT INTO tenants (tenant_id, name, description, gcp_tenant_id, domain_name, tier_id, is_active) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?)";

  public static final String GET_TENANT_BY_ID =
      "SELECT " + TENANT_COLUMNS + " FROM tenants WHERE tenant_id = ?";

  public static final String GET_ALL_ACTIVE_TENANTS =
      "SELECT " + TENANT_COLUMNS + " FROM tenants WHERE is_active = TRUE";

  public static final String GET_ALL_TENANTS =
      "SELECT " + TENANT_COLUMNS + " FROM tenants";

  public static final String UPDATE_TENANT =
      "UPDATE tenants SET name = ?, description = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ?";

  public static final String UPDATE_TENANT_TIER =
      "UPDATE tenants SET tier_id = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ?";

  public static final String DEACTIVATE_TENANT =
      "UPDATE tenants SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ?";

  public static final String ACTIVATE_TENANT =
      "UPDATE tenants SET is_active = TRUE, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ?";

  public static final String DELETE_TENANT =
      "DELETE FROM tenants WHERE tenant_id = ?";

  public static final String CHECK_TENANT_EXISTS =
      "SELECT COUNT(*) as count FROM tenants WHERE tenant_id = ?";

  public static final String GET_TENANT_BY_GCP_TENANT_ID =
      "SELECT " + TENANT_COLUMNS + " FROM tenants WHERE gcp_tenant_id = ?";

  public static final String GET_TENANT_BY_DOMAIN_NAME =
      "SELECT " + TENANT_COLUMNS + " FROM tenants WHERE domain_name = ?";

  public static final String GET_TENANTS_BY_TIER_ID =
      "SELECT " + TENANT_COLUMNS + " FROM tenants WHERE tier_id = ? AND is_active = TRUE";
}
