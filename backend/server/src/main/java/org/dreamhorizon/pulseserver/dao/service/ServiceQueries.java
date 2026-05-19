package org.dreamhorizon.pulseserver.dao.service;

public class ServiceQueries {

  private static final String COLUMNS =
      "id, service_name, service_group, display_name, owner_email, owner_slack_id, "
          + "goalert_service_id, description, is_active, created_at, updated_at";

  public static final String GET_BY_SERVICE_NAME =
      "SELECT " + COLUMNS + " FROM services WHERE service_name = ? AND is_active = TRUE";

  public static final String GET_ALL_ACTIVE =
      "SELECT " + COLUMNS + " FROM services WHERE is_active = TRUE ORDER BY service_name";

  public static final String INSERT =
      "INSERT INTO services (service_name, service_group, display_name, owner_email, "
          + "owner_slack_id, goalert_service_id, description) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?)";

  public static final String UPDATE =
      "UPDATE services SET service_group = ?, display_name = ?, owner_email = ?, "
          + "owner_slack_id = ?, goalert_service_id = ?, description = ?, "
          + "updated_at = CURRENT_TIMESTAMP WHERE service_name = ? AND is_active = TRUE";

  public static final String SOFT_DELETE =
      "UPDATE services SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP "
          + "WHERE service_name = ? AND is_active = TRUE";
}
