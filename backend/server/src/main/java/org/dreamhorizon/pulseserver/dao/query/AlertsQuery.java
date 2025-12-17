package org.dreamhorizon.pulseserver.dao.query;

public class AlertsQuery {
  public static final String GET_ALERT_DETAILS = """
      SELECT\s
          A.id AS alert_id,
          A.name,
          A.description,
          A.scope,
          A.dimension_filter,
          A.condition_expression,
          A.evaluation_period,
          A.evaluation_interval,
          A.severity_id,
          A.notification_channel_id,
          NC.notification_webhook_url AS notification_webhook_url,
          A.created_by,
          A.updated_by,
          A.created_at AS alert_created_at,
          A.updated_at AS alert_updated_at,
          A.is_active,
          A.last_snoozed_at,
          A.snoozed_from,
          A.snoozed_until
      FROM\s
          Alerts A
      LEFT JOIN \s
          Notification_Channels NC ON A.notification_channel_id = NC.notification_channel_id\s
      WHERE A.id = ? AND A.is_active = TRUE;""";

  public static final String GET_ALERT_SCOPES = """
      SELECT\s
          id,
          alert_id,
          name,
          conditions,
          state,
          created_at,
          updated_at
      FROM Alert_Scope\s
      WHERE alert_id = ? AND is_active = TRUE\s
      ORDER BY name;""";

  public static final String GET_ALL_ALERT_SCOPES = """
      SELECT\s
          id,
          alert_id,
          name,
          conditions,
          state,
          created_at,
          updated_at
      FROM Alert_Scope\s
      WHERE alert_id IN (SELECT id FROM Alerts WHERE is_active = TRUE) AND is_active = TRUE\s
      ORDER BY alert_id, name;""";

  public static final String GET_ALERT_SCOPES_FOR_IDS = """
      SELECT\s
          id,
          alert_id,
          name,
          conditions,
          state,
          created_at,
          updated_at
      FROM Alert_Scope\s
      WHERE alert_id IN (%s) AND is_active = TRUE\s
      ORDER BY alert_id, name;""";

  public static final String GET_ALERTS = """
      WITH FilteredAlerts AS (
          SELECT\s
              A.id AS alert_id,\s
              A.name,\s
              A.description,\s
              A.scope,\s
              A.dimension_filter,\s
              A.condition_expression,\s
              A.evaluation_period,\s
              A.evaluation_interval,\s
              A.severity_id,\s
              A.notification_channel_id,\s
              A.created_by,\s
              A.updated_by,\s
              A.created_at AS alert_created_at,\s
              A.updated_at AS alert_updated_at,\s
              A.is_active,\s
              A.last_snoozed_at,\s
              A.snoozed_from,\s
              A.snoozed_until
          FROM\s
              Alerts A
          WHERE\s
              A.is_active = TRUE\s
              AND ( ? = '' OR A.name LIKE CONCAT('%', ?, '%'))\s
              AND ( ? = '' OR A.scope = ?)\s
              AND ( ? = '' OR A.created_by = ?)\s
              AND ( ? = '' OR A.updated_by = ?)\s
      ),
      TotalAlertCount AS (
          SELECT COUNT(*) AS total_count FROM FilteredAlerts\s
      ),\s
      AlertFilterWithLimitAndOffset AS (
          SELECT * FROM FilteredAlerts ORDER BY alert_created_at DESC LIMIT ? OFFSET ?\s
      )
      SELECT\s
          FA.alert_id,\s
          FA.name,\s
          FA.description,\s
          FA.scope,\s
          FA.dimension_filter,\s
          FA.condition_expression,\s
          FA.evaluation_period,\s
          FA.evaluation_interval,\s
          FA.severity_id,\s
          FA.notification_channel_id,\s
          FA.created_by,\s
          FA.updated_by,\s
          FA.alert_created_at,\s
          FA.alert_updated_at,\s
          FA.is_active,\s
          FA.last_snoozed_at,\s
          FA.snoozed_from,\s
          FA.snoozed_until,\s
          NC.notification_webhook_url,\s
          (SELECT total_count FROM TotalAlertCount) AS total_count\s
      FROM\s
          AlertFilterWithLimitAndOffset FA
      LEFT JOIN\s
          Notification_Channels NC ON FA.notification_channel_id = NC.notification_channel_id\s
      """;

  public static final String GET_ALL_ALERTS = """
      SELECT\s
          A.id AS alert_id,
          A.name,
          A.description,
          A.scope,
          A.dimension_filter,
          A.condition_expression,
          A.evaluation_period,
          A.evaluation_interval,
          A.severity_id,
          A.notification_channel_id,
          NC.notification_webhook_url AS notification_webhook_url,
          A.created_by,
          A.updated_by,
          A.created_at AS alert_created_at,
          A.updated_at AS alert_updated_at,
          A.is_active,
          A.last_snoozed_at,
          A.snoozed_from,
          A.snoozed_until
      FROM\s
          Alerts A
      LEFT JOIN \s
          Notification_Channels NC ON A.notification_channel_id = NC.notification_channel_id\s
      WHERE A.is_active = TRUE;""";

  public static final String CREATE_ALERT = "INSERT INTO Alerts("
      + "name, "
      + "description, "
      + "scope, "
      + "dimension_filter, "
      + "condition_expression, "
      + "severity_id, "
      + "notification_channel_id, "
      + "evaluation_period, "
      + "evaluation_interval, "
      + "created_by) "
      + "VALUES (?,?,?,?,?,?,?,?,?,?);";

  public static final String CREATE_ALERT_SCOPE = "INSERT INTO Alert_Scope("
      + "alert_id, "
      + "name, "
      + "conditions, "
      + "state, "
      + "is_active) "
      + "VALUES (?,?,?,?,TRUE);";

  public static final String DELETE_ALERT = "UPDATE Alerts SET is_active = FALSE WHERE id = ?;";

  public static final String UPDATE_ALERT_STATE = "UPDATE Alert_Scope SET state = ? WHERE alert_id = ? AND name = ?;";

  public static final String UPDATE_ALERT = "UPDATE Alerts SET "
      + "name = ?, "
      + "description = ?, "
      + "scope = ?, "
      + "dimension_filter = ?, "
      + "condition_expression = ?, "
      + "severity_id = ?, "
      + "notification_channel_id = ?, "
      + "evaluation_period = ?, "
      + "evaluation_interval = ?, "
      + "updated_by = ?, "
      + "updated_at = CURRENT_TIMESTAMP "
      + "WHERE id = ?;";

  public static final String DELETE_ALERT_SCOPES =
      "UPDATE Alert_Scope SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE alert_id = ?;";

  public static final String SNOOZE_ALERT = "UPDATE Alerts "
      + "set last_snoozed_at = ?, snoozed_from = ?, snoozed_until = ?, updated_by = ? WHERE id = ?;";

  public static final String DELETE_SNOOZE = "UPDATE Alerts "
      + "set snoozed_from = null, snoozed_until = null, updated_by = ? WHERE id = ?;";

  public static final String GET_CURRENT_STATE_OF_ALERT = "SELECT current_state FROM Alerts WHERE id = ?;";

  public static final String GET_ALERT_FILTERS =
      "SELECT DISTINCT A.name as name, A.scope as scope, A.created_by as created_by, A.updated_by as updated_by,"
          + " S.state AS current_state FROM Alerts A"
          + "LEFT JOIN Alert_Scope S ON A.id = S.alert_id AND S.is_active = TRUE "
          + "WHERE A.is_active = TRUE;";

  public static final String GET_SEVERITIES = "SELECT * FROM Severity;";
  public static final String CREATE_SEVERITY = "INSERT INTO Severity(name, description) VALUES (?,?);";

  public static final String GET_NOTIFICATION_CHANNELS = "SELECT * FROM Notification_Channels;";
  public static final String CREATE_NOTIFICATION_CHANNEL =
      "INSERT INTO Notification_Channels(name, notification_webhook_url) VALUES (?,?);";

  public static final String CREATE_TAG = "INSERT INTO Tags(name) VALUES (?);";
  public static final String GET_TAGS_FOR_ALERT =
      "SELECT Tags.name, AT.alert_id FROM Tags LEFT JOIN Alert_Tags as AT ON Tags.tag_id = AT.tag_id AND AT.alert_id = ?;";
  public static final String GET_ALL_TAGS = "SELECT * FROM Tags;";

  public static final String CREATE_ALERT_TAG_MAPPING = "INSERT INTO Alert_Tag_Mapping(alert_id, tag_id) VALUES (?,?);";
  public static final String DELETE_ALERT_TAG_MAPPING = "DELETE FROM Alert_Tag_Mapping WHERE alert_id = ? AND tag_id = ?;";

  public static final String GET_METRICS_BY_SCOPE = "SELECT id, name, label FROM Alert_Metrics WHERE scope = ? ORDER BY id;";

  public static final String GET_ALL_ALERT_SCOPE_TYPES = "SELECT id, name, label FROM Scope_Types ORDER BY id;";

  public static final String GET_ALERT_DETAILS_FOR_EVALUATION = "SELECT "
      + "id, "
      + "name, "
      + "description, "
      + "scope, "
      + "dimension_filter, "
      + "condition_expression, "
      + "severity_id, "
      + "notification_channel_id, "
      + "evaluation_period, "
      + "evaluation_interval, "
      + "created_by, "
      + "updated_by, "
      + "created_at, "
      + "updated_at, "
      + "is_active, "
      + "snoozed_from, "
      + "snoozed_until "
      + "FROM Alerts "
      + "WHERE id = ? AND is_active = TRUE;";


  public static final String UPDATE_SCOPE_STATE = "UPDATE Alert_Scope SET state = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?;";

  public static final String CREATE_EVALUATION_HISTORY = "INSERT INTO Alert_Evaluation_History("
      + "scope_id, "
      + "evaluation_result, "
      + "state) "
      + "VALUES (?,?,?);";

  public static final String GET_NOTIFICATION_WEBHOOK_URL = "SELECT notification_webhook_url "
      + "FROM Notification_Channels "
      + "WHERE notification_channel_id = ?;";

  public static final String GET_SCOPE_STATE = "SELECT state "
      + "FROM Alert_Scope "
      + "WHERE id = ? AND is_active = TRUE;";

  public static final String GET_EVALUATION_HISTORY_BY_ALERT = "SELECT "
      + "eh.evaluation_id, "
      + "eh.scope_id, "
      + "eh.evaluation_result, "
      + "eh.state, "
      + "eh.evaluated_at, "
      + "as_scope.name as scope_name "
      + "FROM Alert_Evaluation_History eh "
      + "INNER JOIN Alert_Scope as_scope ON eh.scope_id = as_scope.id "
      + "WHERE as_scope.alert_id = ? AND as_scope.is_active = TRUE "
      + "ORDER BY as_scope.id, eh.evaluated_at DESC LIMIT 200;";
}
