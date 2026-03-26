package org.dreamhorizon.pulseserver.dao.notification;

public final class NotificationQueries {

  private NotificationQueries() {}

  // Channel queries
  public static final String GET_CHANNEL_BY_ID =
      """
        SELECT * FROM notification_channels
        WHERE id = ?
        """;

  public static final String GET_CHANNELS_BY_PROJECT =
      """
        SELECT * FROM notification_channels
        WHERE project_id = ?
        ORDER BY created_at DESC
        """;

  public static final String GET_CHANNELS_ACCESSIBLE_BY_PROJECT =
      """
        SELECT * FROM notification_channels
        WHERE (project_id = ? OR project_id IS NULL) AND is_active = TRUE
        ORDER BY created_at DESC
        """;

  public static final String GET_CHANNELS_ACCESSIBLE_BY_PROJECT_AND_TYPE =
      """
        SELECT * FROM notification_channels
        WHERE (project_id = ? OR project_id IS NULL) AND channel_type = ? AND is_active = TRUE
        ORDER BY created_at DESC
        """;

  public static final String GET_SHARED_CHANNELS =
      """
        SELECT * FROM notification_channels
        WHERE project_id IS NULL AND is_active = TRUE
        ORDER BY created_at DESC
        """;

  public static final String GET_ACTIVE_CHANNELS_BY_TYPE =
      """
        SELECT * FROM notification_channels
        WHERE project_id = ? AND channel_type = ? AND is_active = TRUE
        ORDER BY created_at ASC LIMIT 1
        """;

  public static final String GET_ACTIVE_CHANNEL_BY_PROJECT_AND_TYPE =
      """
        SELECT * FROM notification_channels
        WHERE project_id = ? AND channel_type = ? AND is_active = TRUE
        LIMIT 1
        """;

  public static final String INSERT_CHANNEL =
      """
        INSERT INTO notification_channels
        (project_id, channel_type, name, config, is_active)
        VALUES (?, ?, ?, ?, ?)
        """;

  public static final String UPDATE_CHANNEL =
      """
        UPDATE notification_channels
        SET name = ?, config = ?, is_active = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """;

  public static final String DELETE_CHANNEL =
      """
        DELETE FROM notification_channels WHERE id = ?
        """;

  // Template queries (global -- no project_id)
  public static final String GET_TEMPLATE_BY_ID =
      """
        SELECT * FROM notification_templates
        WHERE id = ?
        """;

  public static final String GET_ALL_TEMPLATES =
      """
        SELECT * FROM notification_templates
        ORDER BY event_name, version DESC
        """;

  public static final String GET_TEMPLATES_BY_CHANNEL_TYPE =
      """
        SELECT * FROM notification_templates
        WHERE channel_type = ?
        ORDER BY event_name, version DESC
        """;

  public static final String GET_TEMPLATE_BY_EVENT_NAME_AND_CHANNEL =
      """
        SELECT * FROM notification_templates
        WHERE event_name = ? AND (channel_type = ? OR channel_type IS NULL)
        AND is_active = TRUE
        ORDER BY channel_type DESC, version DESC
        LIMIT 1
        """;

  public static final String GET_LATEST_TEMPLATE_VERSION =
      """
        SELECT MAX(version) FROM notification_templates
        WHERE event_name = ? AND (channel_type = ? OR (? IS NULL AND channel_type IS NULL))
        """;

  public static final String INSERT_TEMPLATE =
      """
        INSERT INTO notification_templates
        (event_name, channel_type, version, body, is_active)
        VALUES (?, ?, ?, ?, ?)
        """;

  public static final String UPDATE_TEMPLATE =
      """
        UPDATE notification_templates
        SET event_name = ?, channel_type = ?, body = ?, is_active = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """;

  public static final String DELETE_TEMPLATE =
      """
        DELETE FROM notification_templates WHERE id = ?
        """;

  // Channel event mapping queries
  public static final String GET_MAPPING_BY_ID =
      """
        SELECT * FROM channel_event_mapping
        WHERE id = ?
        """;

  public static final String GET_MAPPINGS_BY_PROJECT =
      """
        SELECT * FROM channel_event_mapping
        WHERE project_id = ?
        ORDER BY created_at DESC
        """;

  public static final String GET_ACTIVE_MAPPING_WITH_CHANNEL_BY_ID =
      """
        SELECT m.*, c.channel_type, c.config, c.name AS channel_name, c.is_active AS channel_active
        FROM channel_event_mapping m
        JOIN notification_channels c ON m.channel_id = c.id
        WHERE m.id = ? AND m.is_active = TRUE AND c.is_active = TRUE
        """;

  public static final String GET_ACTIVE_MAPPINGS_BY_PROJECT_AND_EVENT =
      """
        SELECT m.*, c.channel_type, c.config, c.name AS channel_name, c.is_active AS channel_active
        FROM channel_event_mapping m
        JOIN notification_channels c ON m.channel_id = c.id
        WHERE m.project_id = ? AND m.event_name = ? AND m.is_active = TRUE AND c.is_active = TRUE
        ORDER BY c.channel_type, m.id
        """;

  public static final String INSERT_MAPPING =
      """
        INSERT INTO channel_event_mapping
        (project_id, channel_id, event_name, recipient, recipient_name, is_active)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

  public static final String UPDATE_MAPPING =
      """
        UPDATE channel_event_mapping
        SET recipient = ?, recipient_name = ?, is_active = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """;

  public static final String DELETE_MAPPING =
      """
        DELETE FROM channel_event_mapping WHERE id = ?
        """;

  // Log queries
  public static final String GET_LOGS_BY_PROJECT =
      """
        SELECT * FROM notification_logs
        WHERE project_id = ?
        ORDER BY created_at DESC
        LIMIT ? OFFSET ?
        """;

  public static final String GET_LOGS_BY_IDEMPOTENCY_KEY =
      """
        SELECT * FROM notification_logs
        WHERE project_id = ? AND idempotency_key LIKE CONCAT(?, ':%')
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
        (project_id, idempotency_key, channel_type, channel_id, template_id,
         recipient, subject, status, attempt_count)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

  public static final String INSERT_LOG_IF_NOT_EXISTS =
      """
        INSERT IGNORE INTO notification_logs
        (project_id, idempotency_key, channel_type, channel_id, template_id,
         recipient, subject, status, attempt_count)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
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
