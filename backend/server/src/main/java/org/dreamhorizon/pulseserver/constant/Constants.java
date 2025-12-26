package org.dreamhorizon.pulseserver.constant;

public final class Constants {
  //Alerts
  public static final String ALERT_EVALUATE_AND_TRIGGER_ALERT =
      "/v1/alert/evaluateAndTriggerAlert";

  public static final String MYSQL_WRITER_HOST = "mysql_writer_host";
  public static final String MYSQL_READER_HOST = "mysql_reader_host";
  public static final String MYSQL_DATABASE = "mysql_database";
  public static final String MYSQL_USER = "mysql_user";
  public static final String MYSQL_PASSWORD = "mysql_password";
  public static final String MYSQL_WRITER_MAX_POOL_SIZE = "mysql_writer_max_pool_size";
  public static final String MYSQL_READER_MAX_POOL_SIZE = "mysql_reader_max_pool_size";

  public static final String HTTP_CONNECT_TIMEOUT = "http_connect_timeout";
  public static final String HTTP_READ_TIMEOUT = "http_read_timeout";
  public static final String HTTP_WRITE_TIMEOUT = "http_write_timeout";
  public static final String HTTP_CLIENT_KEEP_ALIVE = "http_client_keep_alive";
  public static final String HTTP_CLIENT_KEEP_ALIVE_TIMEOUT = "http_client_keep_alive_timeout";
  public static final String HTTP_CLIENT_IDLE_TIMEOUT = "http_client_idle_timeout";
  public static final String HTTP_CLIENT_CONNECTION_POOL_MAX_SIZE =
      "http_client_connection_pool_max_size";
  public static final String SHUTDOWN_STATUS = "__shutdown__";

  public static final String QUERY_COMPLETED_STATUS = "Query completed";

  public static final String EVENT_BUS_RESPONSE_UPDATE_ALERT_EVALUATION_LOGS_CHANNEL =
      "athena.query.response.updateAlertEvaluationLogs";
  public static final String EVENT_BUS_RESPONSE_UPDATE_ALERT_STATE_CHANNEL = "athena.query.response.updateAlertState";

  public static final String RESULT_SET_KEY = "resultSet";
  public static final String STATUS_KEY = "status";
  public static final String ERROR_KEY = "error";
  public static final String ALERT_EVALUATION_QUERY_TIME = "timeTaken";
  public static final String ALERT_EVALUATION_START_TIME = "evaluationStartTime";
  public static final String ALERT_EVALUATION_END_TIME = "evaluationEndTime";
}
