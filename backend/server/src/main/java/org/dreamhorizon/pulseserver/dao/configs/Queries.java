package org.dreamhorizon.pulseserver.dao.configs;

public class Queries {
  public static final String INSERT_CONFIG = 
      "INSERT INTO pulse_sdk_configs (config_json, is_active, created_by) VALUES (?, ?, ?); SELECT LAST_INSERT_ID();";
}
