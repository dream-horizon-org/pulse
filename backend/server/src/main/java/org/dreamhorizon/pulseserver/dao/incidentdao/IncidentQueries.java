package org.dreamhorizon.pulseserver.dao.incidentdao;

public class IncidentQueries {

  public static final String INSERT_INCIDENT =
      "INSERT INTO incidents (title, description, severity, reporter_name, reporter_email, org_identifier, status) "
          + "VALUES (?, ?, ?, ?, ?, ?, 'OPEN')";

  public static final String GET_INCIDENT_BY_ID =
      "SELECT id, title, description, severity, reporter_name, reporter_email, org_identifier, status, created_at, updated_at "
          + "FROM incidents WHERE id = ?";
}
