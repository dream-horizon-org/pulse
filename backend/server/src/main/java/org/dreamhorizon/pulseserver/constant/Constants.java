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

  public static final int QUERY_TIMEOUT_SECONDS = 3;
  public static final int RESULT_FETCH_DELAY_MS = 200;
  public static final int MAX_RESULT_FETCH_RETRIES = 5;
  public static final int RESULT_FETCH_RETRY_DELAY_MS = 300;
  public static final int QUERY_POLL_INTERVAL_MS = 200;
  public static final int MAX_QUERY_RESULTS = 1000;
  public static final int MIN_QUERY_RESULTS = 1;
  public static final int ATHENA_WAIT_COMPLETION_DELAY_MS = 2000;

  // Query Engine Configuration
  public static final String QUERY_ENGINE_ENV_VAR = "CONFIG_SERVICE_APPLICATION_QUERY_ENGINE";
  public static final String DEFAULT_QUERY_ENGINE = "athena";
  public static final String ATHENA_ENGINE = "athena";
  public static final String GCP_ENGINE = "gcp";
}
