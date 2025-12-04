package org.dreamhorizon.pulseserver.dao.query;

public class AlertsQueryV4 {
    public static final String GET_ALERT_DETAILS = "SELECT "
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

    public static final String UPDATE_ALERT_STATE = "UPDATE Alerts SET updated_at = CURRENT_TIMESTAMP WHERE id = ?;";

    public static final String GET_ALERT_SCOPES = "SELECT "
            + "id, "
            + "alert_id, "
            + "name, "
            + "conditions, "
            + "state, "
            + "created_at, "
            + "updated_at "
            + "FROM Alert_Scope "
            + "WHERE alert_id = ?;";

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
            + "WHERE id = ?;";
}

