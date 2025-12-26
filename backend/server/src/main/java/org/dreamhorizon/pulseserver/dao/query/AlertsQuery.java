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
          alerts A
      LEFT JOIN \s
          notification_channels NC ON A.notification_channel_id = NC.notification_channel_id\s
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
      FROM alert_scope\s
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
      FROM alert_scope\s
      WHERE alert_id IN (SELECT id FROM alerts WHERE is_active = TRUE) AND is_active = TRUE\s
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
      FROM alert_scope\s
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
              alerts A
          WHERE\s
              A.is_active = TRUE\s
              AND ( ? = '' OR A.name LIKE CONCAT('%', ?, '%'))\s
              AND ( ? = '' OR A.scope = ?)\s
              AND ( ? = '' OR A.created_by = ?)\s
              AND ( ? = '' OR A.updated_by = ?)\s
              AND ( ? = '' OR (
                  CASE
                      WHEN ? = 'FIRING' THEN EXISTS (
                          SELECT 1 FROM alert_scope AS2
                          WHERE AS2.alert_id = A.id AND AS2.is_active = TRUE AND AS2.state = 'FIRING'
                      ) AND (A.snoozed_from IS NULL OR A.snoozed_until IS NULL OR NOW() < A.snoozed_from OR NOW() > A.snoozed_until)
                      WHEN ? = 'NO_DATA' THEN EXISTS (
                          SELECT 1 FROM alert_scope AS2
                          WHERE AS2.alert_id = A.id AND AS2.is_active = TRUE AND AS2.state = 'NO_DATA'
                      ) AND NOT EXISTS (
                          SELECT 1 FROM alert_scope AS2
                          WHERE AS2.alert_id = A.id AND AS2.is_active = TRUE AND AS2.state = 'FIRING'
                      ) AND (A.snoozed_from IS NULL OR A.snoozed_until IS NULL OR NOW() < A.snoozed_from OR NOW() > A.snoozed_until)
                      WHEN ? = 'NORMAL' THEN (
                          NOT EXISTS (
                              SELECT 1 FROM alert_scope AS2
                              WHERE AS2.alert_id = A.id AND AS2.is_active = TRUE AND AS2.state != 'NORMAL'
                          ) OR NOT EXISTS (
                              SELECT 1 FROM alert_scope AS2
                              WHERE AS2.alert_id = A.id AND AS2.is_active = TRUE
                          )
                      ) AND (A.snoozed_from IS NULL OR A.snoozed_until IS NULL OR NOW() < A.snoozed_from OR NOW() > A.snoozed_until)
                      WHEN ? = 'SNOOZED' THEN (
                          A.snoozed_from IS NOT NULL AND A.snoozed_until IS NOT NULL 
                          AND NOW() >= A.snoozed_from AND NOW() <= A.snoozed_until
                      )
                      ELSE TRUE
                  END
              ))
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
          notification_channels NC ON FA.notification_channel_id = NC.notification_channel_id\s
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
          alerts A
      LEFT JOIN \s
          notification_channels NC ON A.notification_channel_id = NC.notification_channel_id\s
      WHERE A.is_active = TRUE;""";

  public static final String CREATE_ALERT = "INSERT INTO alerts("
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

  public static final String CREATE_ALERT_SCOPE = "INSERT INTO alert_scope("
      + "alert_id, "
      + "name, "
      + "conditions, "
      + "state, "
      + "is_active) "
      + "VALUES (?,?,?,?,TRUE);";

  public static final String DELETE_ALERT = "UPDATE alerts SET is_active = FALSE WHERE id = ?;";

  public static final String UPDATE_ALERT_STATE = "UPDATE alert_scope SET state = ? WHERE alert_id = ? AND name = ?;";

  public static final String UPDATE_ALERT = "UPDATE alerts SET "
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
      "UPDATE alert_scope SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP WHERE alert_id = ?;";

  public static final String SNOOZE_ALERT = "UPDATE alerts "
      + "set last_snoozed_at = ?, snoozed_from = ?, snoozed_until = ?, updated_by = ? WHERE id = ?;";

  public static final String DELETE_SNOOZE = "UPDATE alerts "
      + "set snoozed_from = null, snoozed_until = null, updated_by = ? WHERE id = ?;";

  public static final String GET_CURRENT_STATE_OF_ALERT = "SELECT current_state FROM alerts WHERE id = ?;";

  public static final String GET_ALERT_FILTERS =
      "SELECT DISTINCT A.name as name, A.scope as scope, A.created_by as created_by, A.updated_by as updated_by,"
          + " S.state AS current_state FROM alerts A"
          + " LEFT JOIN alert_scope S ON A.id = S.alert_id AND S.is_active = TRUE"
          + " WHERE A.is_active = TRUE;";

  public static final String GET_SEVERITIES = "SELECT * FROM severity;";
  public static final String CREATE_SEVERITY = "INSERT INTO severity(name, description) VALUES (?,?);";

  public static final String GET_NOTIFICATION_CHANNELS = "SELECT * FROM notification_channels;";
  public static final String CREATE_NOTIFICATION_CHANNEL =
      "INSERT INTO notification_channels(name, notification_webhook_url) VALUES (?,?);";

  public static final String CREATE_TAG = "INSERT INTO tags(name) VALUES (?);";
  public static final String GET_TAGS_FOR_ALERT =
      "SELECT tags.name, AT.alert_id FROM tags LEFT JOIN alert_tags as AT ON tags.tag_id = AT.tag_id AND AT.alert_id = ?;";
  public static final String GET_ALL_TAGS = "SELECT * FROM tags;";

  public static final String CREATE_ALERT_TAG_MAPPING = "INSERT INTO alert_tag_mapping(alert_id, tag_id) VALUES (?,?);";
  public static final String DELETE_ALERT_TAG_MAPPING = "DELETE FROM alert_tag_mapping WHERE alert_id = ? AND tag_id = ?;";

  public static final String GET_METRICS_BY_SCOPE = "SELECT id, name, label FROM alert_metrics WHERE scope = ? ORDER BY id;";

  public static final String GET_ALL_ALERT_SCOPE_TYPES = "SELECT id, name, label FROM scope_types ORDER BY id;";

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
      + "FROM alerts "
      + "WHERE id = ? AND is_active = TRUE;";


  public static final String UPDATE_SCOPE_STATE = "UPDATE alert_scope SET state = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?;";

  public static final String CREATE_EVALUATION_HISTORY = "INSERT INTO alert_evaluation_history("
      + "scope_id, "
      + "evaluation_result, "
      + "state) "
      + "VALUES (?,?,?);";

  public static final String GET_NOTIFICATION_WEBHOOK_URL = "SELECT notification_webhook_url "
      + "FROM notification_channels "
      + "WHERE notification_channel_id = ?;";

  public static final String GET_SCOPE_STATE = "SELECT state "
      + "FROM alert_scope "
      + "WHERE id = ? AND is_active = TRUE;";

  public static final String GET_EVALUATION_HISTORY_BY_ALERT = "SELECT "
      + "eh.evaluation_id, "
      + "eh.scope_id, "
      + "eh.evaluation_result, "
      + "eh.state, "
      + "eh.evaluated_at, "
      + "as_scope.name as scope_name "
      + "FROM alert_evaluation_history eh "
      + "INNER JOIN alert_scope as_scope ON eh.scope_id = as_scope.id "
      + "WHERE as_scope.alert_id = ? AND as_scope.is_active = TRUE "
      + "ORDER BY as_scope.id, eh.evaluated_at DESC LIMIT 200;";
}
