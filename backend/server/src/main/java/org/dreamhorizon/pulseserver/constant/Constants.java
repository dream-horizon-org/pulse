package org.dreamhorizon.pulseserver.constant;

public final class Constants {

  //PUB SUB Prefix
  public static final String NAME = "name";
  public static final String PARAMETERS = "parameters";
  // GCP JOB STATUS
  public static final String JOB_STATE_UNKNOWN = "JOB_STATE_UNKNOWN";
  public static final String JOB_STATE_STOPPED = "JOB_STATE_STOPPED";
  public static final String JOB_STATE_RUNNING = "JOB_STATE_RUNNING";
  public static final String JOB_STATE_DONE = "JOB_STATE_DONE";
  public static final String JOB_STATE_FAILED = "JOB_STATE_FAILED";
  public static final String JOB_STATE_CANCELLED = "JOB_STATE_CANCELLED";
  public static final String JOB_STATE_UPDATED = "JOB_STATE_UPDATED";
  public static final String JOB_STATE_DRAINING = "JOB_STATE_DRAINING";
  public static final String JOB_STATE_DRAINED = "JOB_STATE_DRAINED";
  public static final String JOB_STATE_PENDING = "JOB_STATE_PENDING";
  public static final String JOB_STATE_CANCELLING = "JOB_STATE_CANCELLING";
  public static final String JOB_STATE_QUEUED = "JOB_STATE_QUEUED";
  public static final String JOB_STATE_RESOURCE_CLEANING_UP = "JOB_STATE_RESOURCE_CLEANING_UP";
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
}
