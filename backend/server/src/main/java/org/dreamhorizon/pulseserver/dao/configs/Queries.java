package org.dreamhorizon.pulseserver.dao.configs;

public class Queries {
  public static final String INSERT_CONFIG =
      "INSERT INTO pulse_sdk_configs (config_json, is_active, created_by, description) VALUES (?, ?, ?, ?);";
  public static final String GET_CONFIG_BY_VERSION =
      "SELECT config_json, version, description FROM pulse_sdk_configs WHERE version = ?";
  public static final String GET_LATEST_VERSION =
      "SELECT version FROM pulse_sdk_configs WHERE is_active = 1 LIMIT 1";
  public static final String GET_ALL_CONFIG_DETAILS =
      "SELECT version, description, created_by, created_at, is_active FROM pulse_sdk_configs";
  public static final String DEACTIVATE_ACTIVE_CONFIG =
      "UPDATE pulse_sdk_configs SET is_active = FALSE WHERE is_active = TRUE";
}
