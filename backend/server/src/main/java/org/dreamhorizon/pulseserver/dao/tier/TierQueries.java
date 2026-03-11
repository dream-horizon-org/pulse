package org.dreamhorizon.pulseserver.dao.tier;

public class TierQueries {

  public static final String INSERT_TIER =
      "INSERT INTO tiers (name, display_name, is_custom_limits_allowed, usage_limit_defaults, is_active) "
          + "VALUES (?, ?, ?, ?, ?)";

  public static final String GET_TIER_BY_ID =
      "SELECT tier_id, name, display_name, is_custom_limits_allowed, usage_limit_defaults, is_active, created_at "
          + "FROM tiers WHERE tier_id = ?";

  public static final String GET_TIER_BY_NAME =
      "SELECT tier_id, name, display_name, is_custom_limits_allowed, usage_limit_defaults, is_active, created_at "
          + "FROM tiers WHERE name = ?";

  public static final String GET_ALL_ACTIVE_TIERS =
      "SELECT tier_id, name, display_name, is_custom_limits_allowed, usage_limit_defaults, is_active, created_at "
          + "FROM tiers WHERE is_active = TRUE";

  public static final String GET_ALL_TIERS =
      "SELECT tier_id, name, display_name, is_custom_limits_allowed, usage_limit_defaults, is_active, created_at "
          + "FROM tiers";

  public static final String UPDATE_TIER =
      "UPDATE tiers SET name = ?, display_name = ?, is_custom_limits_allowed = ?, usage_limit_defaults = ? "
          + "WHERE tier_id = ?";

  public static final String UPDATE_TIER_DEFAULTS =
      "UPDATE tiers SET usage_limit_defaults = ? WHERE tier_id = ?";

  public static final String DEACTIVATE_TIER =
      "UPDATE tiers SET is_active = FALSE WHERE tier_id = ?";

  public static final String ACTIVATE_TIER =
      "UPDATE tiers SET is_active = TRUE WHERE tier_id = ?";

  public static final String DELETE_TIER =
      "DELETE FROM tiers WHERE tier_id = ?";

  public static final String CHECK_TIER_EXISTS =
      "SELECT COUNT(*) as count FROM tiers WHERE tier_id = ?";

  public static final String CHECK_TIER_NAME_EXISTS =
      "SELECT COUNT(*) as count FROM tiers WHERE name = ?";
}
