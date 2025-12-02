package in.horizonos.pulseserver.dao.query;

public class AlertsQuery {
  // Alerts table queries
  public static final String GET_ALERT_DETAILS = "SELECT \n" +
      "    A.id AS alert_id,\n" +
      "    A.name,\n" +
      "    A.description,\n" +
      "    A.scope,\n" +
      "    A.dimension_filter,\n" +
      "    A.condition_expression,\n" +
      "    A.evaluation_period,\n" +
      "    A.evaluation_interval,\n" +
      "    A.severity_id,\n" +
      "    A.notification_channel_id,\n" +
      "    NC.notification_webhook_url AS notification_webhook_url,\n" +
      "    A.created_by,\n" +
      "    A.updated_by,\n" +
      "    A.created_at AS alert_created_at,\n" +
      "    A.updated_at AS alert_updated_at,\n" +
      "    A.is_active,\n" +
      "    A.last_snoozed_at,\n" +
      "    A.snoozed_from,\n" +
      "    A.snoozed_until\n" +
      "FROM \n" +
      "    Alerts A\n" +
      "LEFT JOIN  \n" +
      "    Notification_Channels NC ON A.notification_channel_id = NC.notification_channel_id \n" +
      "WHERE A.id = ? AND A.is_active = TRUE;";

  public static final String GET_ALERT_SCOPES = "SELECT \n" +
      "    id,\n" +
      "    alert_id,\n" +
      "    name,\n" +
      "    conditions,\n" +
      "    state,\n" +
      "    created_at,\n" +
      "    updated_at\n" +
      "FROM Alert_Scope \n" +
      "WHERE alert_id = ? \n" +
      "ORDER BY name;";

  public static final String GET_ALL_ALERT_SCOPES = "SELECT \n" +
      "    id,\n" +
      "    alert_id,\n" +
      "    name,\n" +
      "    conditions,\n" +
      "    state,\n" +
      "    created_at,\n" +
      "    updated_at\n" +
      "FROM Alert_Scope \n" +
      "WHERE alert_id IN (SELECT id FROM Alerts WHERE is_active = TRUE) \n" +
      "ORDER BY alert_id, name;";

  public static final String GET_ALERT_SCOPES_FOR_IDS = "SELECT \n" +
      "    id,\n" +
      "    alert_id,\n" +
      "    name,\n" +
      "    conditions,\n" +
      "    state,\n" +
      "    created_at,\n" +
      "    updated_at\n" +
      "FROM Alert_Scope \n" +
      "WHERE alert_id IN (%s) \n" +
      "ORDER BY alert_id, name;";

  public static final String GET_ALERTS = "WITH FilteredAlerts AS (\n" +
      "    SELECT \n" +
      "        A.id AS alert_id, \n" +
      "        A.name, \n" +
      "        A.description, \n" +
      "        A.scope, \n" +
      "        A.dimension_filter, \n" +
      "        A.condition_expression, \n" +
      "        A.evaluation_period, \n" +
      "        A.evaluation_interval, \n" +
      "        A.severity_id, \n" +
      "        A.notification_channel_id, \n" +
      "        A.created_by, \n" +
      "        A.updated_by, \n" +
      "        A.created_at AS alert_created_at, \n" +
      "        A.updated_at AS alert_updated_at, \n" +
      "        A.is_active, \n" +
      "        A.last_snoozed_at, \n" +
      "        A.snoozed_from, \n" +
      "        A.snoozed_until\n" +
      "    FROM \n" +
      "        Alerts A\n" +
      "    WHERE \n" +
      "        A.is_active = TRUE \n" +
      "        AND ( ? = '' OR A.name LIKE CONCAT('%', ?, '%')) \n" +
      "        AND ( ? = '' OR A.scope = ?) \n" +
      "        AND ( ? = '' OR A.created_by = ?) \n" +
      "        AND ( ? = '' OR A.updated_by = ?) \n" +
      "),\n" +
      "TotalAlertCount AS (\n" +
      "    SELECT COUNT(*) AS total_count FROM FilteredAlerts \n" +
      "), \n" +
      "AlertFilterWithLimitAndOffset AS (\n" +
      "    SELECT * FROM FilteredAlerts ORDER BY alert_created_at DESC LIMIT ? OFFSET ? \n" +
      ")\n" +
      "SELECT \n" +
      "    FA.alert_id, \n" +
      "    FA.name, \n" +
      "    FA.description, \n" +
      "    FA.scope, \n" +
      "    FA.dimension_filter, \n" +
      "    FA.condition_expression, \n" +
      "    FA.evaluation_period, \n" +
      "    FA.evaluation_interval, \n" +
      "    FA.severity_id, \n" +
      "    FA.notification_channel_id, \n" +
      "    FA.created_by, \n" +
      "    FA.updated_by, \n" +
      "    FA.alert_created_at, \n" +
      "    FA.alert_updated_at, \n" +
      "    FA.is_active, \n" +
      "    FA.last_snoozed_at, \n" +
      "    FA.snoozed_from, \n" +
      "    FA.snoozed_until, \n" +
      "    NC.notification_webhook_url, \n" +
      "    (SELECT total_count FROM TotalAlertCount) AS total_count \n" +
      "FROM \n" +
      "    AlertFilterWithLimitAndOffset FA\n" +
      "LEFT JOIN \n" +
      "    Notification_Channels NC ON FA.notification_channel_id = NC.notification_channel_id \n";

  public static final String GET_ALL_ALERTS = "SELECT \n" +
      "    A.id AS alert_id,\n" +
      "    A.name,\n" +
      "    A.description,\n" +
      "    A.scope,\n" +
      "    A.dimension_filter,\n" +
      "    A.condition_expression,\n" +
      "    A.evaluation_period,\n" +
      "    A.evaluation_interval,\n" +
      "    A.severity_id,\n" +
      "    A.notification_channel_id,\n" +
      "    NC.notification_webhook_url AS notification_webhook_url,\n" +
      "    A.created_by,\n" +
      "    A.updated_by,\n" +
      "    A.created_at AS alert_created_at,\n" +
      "    A.updated_at AS alert_updated_at,\n" +
      "    A.is_active,\n" +
      "    A.last_snoozed_at,\n" +
      "    A.snoozed_from,\n" +
      "    A.snoozed_until\n" +
      "FROM \n" +
      "    Alerts A\n" +
      "LEFT JOIN  \n" +
      "    Notification_Channels NC ON A.notification_channel_id = NC.notification_channel_id \n" +
      "WHERE A.is_active = TRUE;";

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
      + "state) "
      + "VALUES (?,?,?,?);";

  public static final String DELETE_ALERT = "UPDATE Alerts SET is_active = FALSE WHERE id = ?;";
  public static final String UPDATE_ALERT_STATE = "UPDATE Alert_Scope SET state = ? WHERE alert_id = ? AND name = ?;";
  public static final String UPDATE_LAST_EVALUATED_AT = "UPDATE Alerts SET last_evaluated_at = CURRENT_TIMESTAMP WHERE id = ?;";

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

  public static final String DELETE_ALERT_SCOPES = "DELETE FROM Alert_Scope WHERE alert_id = ?;";

  public static final String OLD_UPDATE_ALERT = "UPDATE Alerts SET "
      + "use_case_id = ?, "
      + "name = ?, "
      + "description = ?, "
      + "threshold = ?, "
      + "evaluation_period = ?, "
      + "severity_id = ?, "
      + "notification_channel_id = ?, "
      + "updated_by = ?, "
      + "conditions = ?, "
      + "updated_at = CURRENT_TIMESTAMP, "
      + "service_name = ?, "
      + "roster_name = ?, "
      + "metric = ?, "
      + "min_total_interactions = ?, "
      + "min_success_interactions = ?, "
      + "min_error_interactions = ?, "
      + "metric_operator = ?, "
      + "evaluation_interval = ? "
      + "WHERE alert_id = ?;";

  public static final String SNOOZE_ALERT = "UPDATE Alerts "
      + "set last_snoozed_at = ?, snoozed_from = ?, snoozed_until = ?, updated_by = ? WHERE alert_id = ?;";

  public static final String DELETE_SNOOZE = "UPDATE Alerts "
      + "set snoozed_from = null, snoozed_until = null, updated_by = ? WHERE alert_id = ?;";

  public static final String GET_CURRENT_STATE_OF_ALERT = "SELECT current_state FROM Alerts WHERE alert_id = ?;";
  public static final String GET_ALERT_FILTERS =
      "SELECT DISTINCT name, created_by, updated_by, current_state, job_id FROM Alerts WHERE is_active = TRUE;";
  // Alert condition table queries
  public static final String CREATE_ALERT_CONDITION =
      "INSERT INTO Alert_Conditions(alert_id, parameter, operator, value) VALUES (?,?,?,?);";
  public static final String DELETE_ALERT_CONDITION_BY_ALERT_ID = "DELETE FROM Alert_Conditions WHERE alert_id = ?;";

  // Alert state change history
  public static final String CREATE_ALERT_STATE_HISTORY =
      "INSERT INTO Alert_State_History(alert_id, previous_state, new_state) VALUES (?,?,?);";
  public static final String GET_ALERT_STATE_HISTORY = "SELECT * FROM Alert_State_History WHERE alert_id = ? ORDER BY changed_at DESC;";

  // Alert evaluation history
  public static final String CREATE_ALERT_EVALUATION_HISTORY =
      "INSERT INTO Alert_Evaluation_History(alert_id, reading, success_interaction_count, error_interaction_count, total_interaction_count,"
          + " evaluation_time, threshold, min_success_interactions, min_error_interactions, min_total_interactions,"
          + " current_state) VALUES (?,?,?,?,?,?,?,?,?,?,?);";
  public static final String GET_ALERT_EVALUATION_HISTORY =
      "SELECT * FROM Alert_Evaluation_History WHERE alert_id = ? ORDER BY evaluated_at DESC LIMIT 200;";

  // Alert severity table queries
  public static final String GET_SEVERITIES = "SELECT * FROM Severity;";
  public static final String CREATE_SEVERITY = "INSERT INTO Severity(name, description) VALUES (?,?);";

  // Alert notification channel table queries
  public static final String GET_NOTIFICATION_CHANNELS = "SELECT * FROM Notification_Channels;";
  public static final String CREATE_NOTIFICATION_CHANNEL =
      "INSERT INTO Notification_Channels(name, notification_webhook_url) VALUES (?,?);";

  // Alert tag queries
  public static final String CREATE_TAG = "INSERT INTO Tags(name) VALUES (?);";
  public static final String GET_TAGS_FOR_ALERT =
      "SELECT Tags.name, AT.alert_id FROM Tags LEFT JOIN Alert_Tags as AT ON Tags.tag_id = AT.tag_id AND AT.alert_id = ?;";
  public static final String GET_ALL_TAGS = "SELECT * FROM Tags;";

  // Alert tag mapping queries
  public static final String CREATE_ALERT_TAG_MAPPING = "INSERT INTO Alert_Tag_Mapping(alert_id, tag_id) VALUES (?,?);";
  public static final String DELETE_ALERT_TAG_MAPPING = "DELETE FROM Alert_Tag_Mapping WHERE alert_id = ? AND tag_id = ?;";
}