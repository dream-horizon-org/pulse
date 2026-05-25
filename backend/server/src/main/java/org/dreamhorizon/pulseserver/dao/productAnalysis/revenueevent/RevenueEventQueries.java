package org.dreamhorizon.pulseserver.dao.productAnalysis.revenueevent;

public final class RevenueEventQueries {

  private RevenueEventQueries() {}

  public static final String INSERT =
    """
      INSERT INTO revenue_events (id, project_id, event_name, value_attribute, currency,
          currency_attribute, conversion_window_hours, configured_by)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      """;

  public static final String UPDATE =
    """
      UPDATE revenue_events SET event_name = ?, value_attribute = ?, currency = ?,
          currency_attribute = ?, conversion_window_hours = ?, configured_by = ?,
          updated_at = CURRENT_TIMESTAMP
      WHERE project_id = ? AND id = ?
      """;

  public static final String DELETE =
    "DELETE FROM revenue_events WHERE project_id = ? AND id = ?";

  public static final String SELECT_BY_PROJECT =
    """
      SELECT id, project_id, event_name, value_attribute, currency, currency_attribute,
          conversion_window_hours, configured_by, configured_at, updated_at
      FROM revenue_events
      WHERE project_id = ?
      ORDER BY configured_at DESC
      """;

  public static final String SELECT_BY_PROJECT_AND_ID =
    """
      SELECT id, project_id, event_name, value_attribute, currency, currency_attribute,
          conversion_window_hours, configured_by, configured_at, updated_at
      FROM revenue_events
      WHERE project_id = ? AND id = ?
      """;

  public static final String SELECT_BY_PROJECT_AND_EVENT_NAME =
    """
      SELECT id, project_id, event_name, value_attribute, currency, currency_attribute,
          conversion_window_hours, configured_by, configured_at, updated_at
      FROM revenue_events
      WHERE project_id = ? AND event_name = ?
      """;
}
