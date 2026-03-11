package org.dreamhorizon.pulseserver.dao.incidentdao;

public class IncidentQueries {

  public static final String INSERT_INCIDENT =
      "INSERT INTO incidents (title, description, severity, reporter_name, reporter_email, org_identifier, status) "
          + "VALUES (?, ?, ?, ?, ?, ?, 'OPEN')";

  public static final String GET_INCIDENT_BY_ID =
      "SELECT id, title, description, severity, reporter_name, reporter_email, org_identifier, status, "
          + "created_at, updated_at, acknowledged_at, recovered_at, closed_at "
          + "FROM incidents WHERE id = ?";

  public static final String ACKNOWLEDGE_INCIDENT =
      "UPDATE incidents SET status = 'ACKNOWLEDGED', acknowledged_at = CURRENT_TIMESTAMP "
          + "WHERE id = ? AND status = 'OPEN'";

  public static final String RECOVER_INCIDENT =
      "UPDATE incidents SET status = 'RECOVERED', recovered_at = CURRENT_TIMESTAMP "
          + "WHERE id = ? AND status = 'ACKNOWLEDGED'";

  public static final String CLOSE_INCIDENT =
      "UPDATE incidents SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP "
          + "WHERE id = ? AND status = 'RECOVERED'";
}
