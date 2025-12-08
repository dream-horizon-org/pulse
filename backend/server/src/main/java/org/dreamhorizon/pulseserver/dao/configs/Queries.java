package org.dreamhorizon.pulseserver.dao.configs;

public class Queries {
  public static final String INSERT_CONFIG =
      "INSERT INTO pulse_sdk_configs (config_json, is_active, created_by) VALUES (?, ?, ?);";
  public static final String GET_CONFIG_BY_VERSION =
      "SELECT config_json, version FROM pulse_sdk_configs WHERE version = ?";
  public static final String GET_LATEST_VERSION =
      "SELECT version FROM pulse_sdk_configs WHERE is_active = 1 LIMIT 1";

}
