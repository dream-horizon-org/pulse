package org.dreamhorizon.pulseserver.dao.notification;

public final class NotificationQueries {

  private NotificationQueries() {}

  // Channel queries
  public static final String GET_CHANNEL_BY_ID =
      """
        SELECT * FROM project_notification_channels
        WHERE id = ?
        """;

  public static final String GET_CHANNELS_BY_PROJECT =
      """
        SELECT * FROM project_notification_channels
        WHERE project_id = ?
        ORDER BY created_at DESC
        """;

  public static final String GET_ACTIVE_CHANNELS_BY_TYPE =
      """
        SELECT * FROM project_notification_channels
        WHERE project_id = ? AND channel_type = ? AND is_active = TRUE
        ORDER BY created_at ASC LIMIT 1
        """;

  public static final String GET_ACTIVE_CHANNEL_BY_PROJECT_AND_TYPE =
      """
        SELECT * FROM project_notification_channels
        WHERE project_id = ? AND channel_type = ? AND is_active = TRUE
        LIMIT 1
        """;

  public static final String INSERT_CHANNEL =
      """
        INSERT INTO project_notification_channels
        (project_id, channel_type, name, config, is_active)
        VALUES (?, ?, ?, ?, ?)
        """;

  public static final String UPDATE_CHANNEL =
      """
        UPDATE project_notification_channels
        SET name = ?, config = ?, is_active = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """;

  public static final String DELETE_CHANNEL =
      """
        DELETE FROM project_notification_channels WHERE id = ?
        """;

  // Template queries
  public static final String GET_TEMPLATE_BY_ID =
      """
        SELECT * FROM project_notification_templates
        WHERE id = ?
        """;

  public static final String GET_TEMPLATES_BY_PROJECT =
      """
        SELECT * FROM project_notification_templates
        WHERE project_id = ?
        ORDER BY event_name, version DESC
        """;

  public static final String GET_TEMPLATE_BY_EVENT_NAME_AND_CHANNEL =
      """
        SELECT * FROM project_notification_templates
        WHERE project_id = ? AND event_name = ? AND (channel_type = ? OR channel_type IS NULL)
        AND is_active = TRUE
        ORDER BY channel_type DESC, version DESC
        LIMIT 1
        """;

  public static final String GET_LATEST_TEMPLATE_VERSION =
      """
        SELECT MAX(version) FROM project_notification_templates
        WHERE project_id = ? AND event_name = ? AND (channel_type = ? OR (? IS NULL AND channel_type IS NULL))
        """;

  public static final String INSERT_TEMPLATE =
      """
        INSERT INTO project_notification_templates
        (project_id, event_name, channel_type, version, body, is_active)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

  public static final String UPDATE_TEMPLATE =
      """
        UPDATE project_notification_templates
        SET event_name = ?, channel_type = ?, body = ?, is_active = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """;

  public static final String DELETE_TEMPLATE =
      """
        DELETE FROM project_notification_templates WHERE id = ?
        """;

  // Log queries
  public static final String GET_LOGS_BY_PROJECT =
      """
        SELECT * FROM notification_logs
        WHERE project_id = ?
        ORDER BY created_at DESC
        LIMIT ? OFFSET ?
        """;

  public static final String GET_LOGS_BY_BATCH =
      """
        SELECT * FROM notification_logs
        WHERE project_id = ? AND batch_id = ?
        ORDER BY created_at DESC
        """;

  public static final String GET_LOG_BY_IDEMPOTENCY =
      """
        SELECT * FROM notification_logs
        WHERE project_id = ? AND idempotency_key = ? AND channel_type = ? AND recipient = ?
        LIMIT 1
        """;

  public static final String INSERT_LOG =
      """
        INSERT INTO notification_logs
        (project_id, batch_id, idempotency_key, channel_type, channel_id, template_id,
         recipient, subject, status, attempt_count)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

  public static final String INSERT_LOG_IF_NOT_EXISTS =
      """
        INSERT IGNORE INTO notification_logs
        (project_id, batch_id, idempotency_key, channel_type, channel_id, template_id,
         recipient, subject, status, attempt_count)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

  public static final String UPDATE_LOG_STATUS =
      """
        UPDATE notification_logs
        SET status = ?, attempt_count = ?, last_attempt_at = CURRENT_TIMESTAMP,
            error_message = ?, error_code = ?, external_id = ?, provider_response = ?, latency_ms = ?,
            sent_at = CASE WHEN ? = 'SENT' THEN CURRENT_TIMESTAMP ELSE sent_at END
        WHERE id = ?
        """;

  public static final String UPDATE_LOG_BY_EXTERNAL_ID =
      """
        UPDATE notification_logs
        SET status = ?, error_message = ?, last_attempt_at = CURRENT_TIMESTAMP
        WHERE external_id = ?
        """;

  // Email suppression queries
  public static final String GET_SUPPRESSION_BY_EMAIL =
      """
        SELECT * FROM email_suppression_list
        WHERE project_id = ? AND email = ?
        LIMIT 1
        """;

  public static final String IS_EMAIL_SUPPRESSED =
      """
        SELECT 1 FROM email_suppression_list
        WHERE (project_id = ? OR project_id IS NULL) AND email = ?
        LIMIT 1
        """;

  public static final String INSERT_SUPPRESSION =
      """
        INSERT INTO email_suppression_list
        (project_id, email, reason, bounce_type, source_message_id)
        VALUES (?, ?, ?, ?, ?)
        """;

  public static final String INSERT_SUPPRESSION_ALL_PROJECTS =
      """
        INSERT IGNORE INTO email_suppression_list
        (project_id, email, reason, bounce_type, source_message_id)
        VALUES (NULL, ?, ?, ?, ?)
        """;

  public static final String DELETE_SUPPRESSION =
      """
        DELETE FROM email_suppression_list
        WHERE project_id = ? AND email = ?
        """;

  public static final String GET_SUPPRESSIONS_BY_PROJECT =
      """
        SELECT * FROM email_suppression_list
        WHERE project_id = ? OR project_id IS NULL
        ORDER BY suppressed_at DESC
        LIMIT ? OFFSET ?
        """;
}
