package org.dreamhorizon.pulsespark;

public final class SparkConstants {

  private SparkConstants() {}

  /** ClickHouse table names and S3 dataset path segments. */
  public static final class Tables {
    private Tables() {}

    public static final String CH_FUNNEL_RESULTS             = "funnel_results";
    public static final String CH_JOURNEY_RESULTS            = "journey_results";
    public static final String CH_FUNNEL_SESSION_STATE       = "funnel_session_state";
    public static final String CH_FUNNEL_USER_STATE          = "funnel_user_state";
    public static final String CH_FUNNEL_DROPOFF_ATTRIBUTION = "funnel_dropoff_attribution";
    public static final String CH_EVENT_CATALOG_ENTRIES      = "event_catalog_entries";

    public static final String S3_OTEL_LOGS          = "otel_logs";
    public static final String S3_STACK_TRACE_EVENTS = "stack_trace_events";
    public static final String S3_OTEL_TRACES        = "otel_traces";
  }

  /**
   * PascalCase column names as written in the on-disk OTel parquet files
   * (pulse-s3-archiver schema). Used in {@code buildReadExprs} and {@code READ_SCHEMA}.
   */
  public static final class RawColumns {
    private RawColumns() {}

    public static final String EVENT_NAME          = "EventName";
    public static final String PROJECT_ID          = "ProjectId";
    public static final String SESSION_ID          = "SessionId";
    public static final String USER_ID             = "UserId";
    public static final String PULSE_TYPE          = "PulseType";
    public static final String PLATFORM            = "Platform";
    public static final String OS_VERSION          = "OsVersion";
    public static final String APP_VERSION         = "AppVersion";
    public static final String DEVICE_MODEL        = "DeviceModel";
    public static final String NETWORK_PROVIDER    = "NetworkProvider";
    public static final String SCREEN_NAME         = "ScreenName";
    public static final String SERVICE_NAME        = "ServiceName";
    public static final String APP_INSTALLATION_ID = "AppInstallationId";
    public static final String TIMESTAMP           = "Timestamp";
    public static final String LOG_ATTRIBUTES      = "LogAttributes";
    public static final String APP_BUILD_NAME      = "AppBuildName";
  }

  /**
   * Lowercase projected column names used throughout funnel / journey / event-catalog
   * logic after the parquet read projection renames PascalCase raw columns.
   */
  public static final class Columns {
    private Columns() {}

    public static final String EVENT_NAME        = "event_name";
    public static final String PROJECT_ID        = "project_id";
    public static final String SESSION_ID        = "session_id";
    public static final String USER_ID           = "user_id";
    public static final String TIMESTAMP         = "timestamp";
    public static final String PULSE_TYPE        = "pulse_type";
    public static final String OS_NAME           = "os_name";
    public static final String OS_VERSION        = "os_version";
    public static final String APP_BUILD_NAME    = "app_build_name";
    public static final String SCREEN_NAME       = "screen_name";
    public static final String PLATFORM          = "platform";
    public static final String DEVICE_MODEL      = "device_model";
    public static final String NETWORK_PROVIDER  = "network_provider";
    public static final String APP_VERSION       = "app_version";
    public static final String GEO_COUNTRY       = "geo_country";
    public static final String TRACE_ID          = "trace_id";
    public static final String LOG_ATTRIBUTES    = "log_attributes";
    public static final String SERVICE_NAME      = "service_name";

    // Legacy aliases kept for backward compat with downstream filter + hydration logic.
    public static final String DEVICE_MANUFACTURER    = "device_manufacturer";
    public static final String DEVICE_MODEL_IDENTIFIER = "device_model_identifier";
    public static final String NETWORK_CARRIER_ICC    = "network_carrier_icc";
  }

  /** Catalog filter-key strings shared by EventCatalogJob and FilterFieldMapper. */
  public static final class FilterKeys {
    private FilterKeys() {}

    public static final String EVENT          = "EVENT";
    public static final String APP_BUILD_NAME = "APP_BUILD_NAME";
    public static final String OS_VERSION     = "OS_VERSION";
    public static final String OS_NAME        = "OS_NAME";
  }

  /** Funnel / journey mode and step-order type strings. */
  public static final class Modes {
    private Modes() {}

    public static final String SESSIONS     = "SESSIONS";
    public static final String UNIQUE_USERS = "UNIQUE_USERS";
    public static final String ORDERED      = "ORDERED";
    public static final String UNORDERED    = "UNORDERED";
    public static final String START        = "START";
  }

  /** {@code pulse.type} signal values emitted by the mobile / web SDKs. */
  public static final class PulseTypes {
    private PulseTypes() {}

    public static final String CUSTOM_EVENT    = "custom_event";
    public static final String DEVICE_CRASH    = "device.crash";
    public static final String DEVICE_ANR      = "device.anr";
    public static final String NON_FATAL       = "non_fatal";
    public static final String APP_JANK_FROZEN = "app.jank.frozen";
    public static final String APP_JANK_SLOW   = "app.jank.slow";
    /** Regex matching {@code network.<status_code>} pulse type values. */
    public static final String NETWORK_PATTERN = "^network\\.(\\d+)$";
  }

  /** Spark session / reader configuration keys and common option values. */
  public static final class SparkConfig {
    private SparkConfig() {}

    public static final String PARQUET_VECTORIZED_READER = "spark.sql.parquet.enableVectorizedReader";
    public static final String SESSION_TIME_ZONE         = "spark.sql.session.timeZone";
    public static final String UTC                       = "UTC";
    public static final String PARQUET_FORMAT            = "parquet";
    public static final String PARQUET_RECURSIVE_LOOKUP  = "recursiveFileLookup";
    public static final String PARQUET_MERGE_SCHEMA      = "mergeSchema";
    public static final String S3A_PREFIX                = "s3a://";
  }

  /** OTel HTTP span attribute keys used in attribution, plus HTTP status thresholds. */
  public static final class HttpAttributes {
    private HttpAttributes() {}

    public static final String HTTP_STATUS_CODE     = "http.status_code";
    public static final String HTTP_STATUS_CODE_NEW = "http.response.status_code";
    public static final String HTTP_METHOD          = "http.method";
    public static final String HTTP_METHOD_NEW      = "http.request.method";
    public static final String NET_PEER_NAME        = "net.peer.name";
    public static final String SERVER_ADDRESS       = "server.address";

    public static final int HTTP_4XX_THRESHOLD = 400;
    public static final int HTTP_5XX_THRESHOLD = 500;
  }

  /** Analytics-job type and status strings stored in MySQL. */
  public static final class JobStatus {
    private JobStatus() {}

    public static final String TYPE_EVENTS_INCREMENTAL = "EVENTS_INCREMENTAL";
    public static final String RUNNING                 = "RUNNING";
    public static final String SUCCEEDED               = "SUCCEEDED";
    public static final String FAILED                  = "FAILED";
    public static final String AUTO                    = "AUTO";
  }

  /** MySQL column names used in result-set reads. */
  public static final class MysqlColumns {
    private MysqlColumns() {}

    // shared
    public static final String ID           = "id";
    public static final String PROJECT_ID   = "project_id";
    public static final String MODE         = "mode";
    public static final String DATE_RANGE   = "date_range";
    public static final String FILTERS_JSON = "filters_json";
    public static final String START_TIME   = "start_time";
    public static final String END_TIME     = "end_time";

    // funnel table
    public static final String STEPS_JSON      = "steps_json";
    public static final String WINDOW_SECONDS  = "window_seconds";
    public static final String FUNNEL_TYPE     = "funnel_type";
    public static final String STEP_ORDER_TYPE = "step_order_type";
    public static final String REVENUE_ATTRIBUTE   = "revenue_attribute";
    public static final String REVENUE_STEP_INDEX  = "revenue_step_index";
    public static final String CURRENCY            = "currency";

    // journey table
    public static final String ANCHOR_EVENT  = "anchor_event";
    public static final String DIRECTION     = "direction";
    public static final String DEPTH         = "depth";
    public static final String JOURNEY_TYPE  = "journey_type";

    // analytics_jobs table
    public static final String JOB_TYPE    = "job_type";
    public static final String STATUS      = "status";
    public static final String STARTED_AT  = "started_at";
    public static final String TS          = "ts";
  }

  /** MySQL table names. */
  public static final class MysqlTables {
    private MysqlTables() {}

    public static final String FUNNEL         = "funnel";
    public static final String JOURNEY        = "journey";
    public static final String ANALYTICS_JOBS = "analytics_jobs";
    public static final String PROJECTS       = "projects";
  }

  /**
   * ClickHouse INSERT column-list strings for each table, kept here so a schema
   * change only requires updating one place.
   */
  public static final class ChColumns {
    private ChColumns() {}

    public static final String FUNNEL_RESULTS_COLS =
        "FunnelId,ProjectId,RunTime,StepIndex,StepName,UserCount,ConversionPct,MedianStepSeconds,"
            + "OrderCount,Revenue,AvgOrderValue,LostRevenue";

    public static final String JOURNEY_RESULTS_COLS =
        "JourneyId,ProjectId,RunTime,Direction,PosFrom,EventFrom,PosTo,EventTo,UserCount";

    public static final String FUNNEL_SESSION_STATE_COLS =
        "FunnelId,ProjectId,RunTime,SessionId,UserId,"
            + "LastReachedStep,LastReachedStepName,LastReachedAt,"
            + "DropoffStep,TimeToDropoffSec,ScreenAtDropoff,TraceIdAtDropoff,"
            + "AppVersion,OsName,OsVersion,Platform,DeviceModel,NetworkProvider,GeoCountry";

    public static final String FUNNEL_USER_STATE_COLS =
        "FunnelId,ProjectId,RunTime,UserId,MaxReachedStep,DropoffStep,"
            + "CanonicalSessionId,CanonicalLastReachedAt,CanonicalTraceIdAtDropoff,"
            + "CanonicalScreenAtDropoff,AppVersion,OsName,OsVersion,Platform,DeviceModel,"
            + "NetworkProvider,GeoCountry,SessionAttempts";

    public static final String FUNNEL_DROPOFF_ATTRIBUTION_COLS =
        "FunnelId,ProjectId,RunTime,StepIndex,CauseKind,CauseKey,CauseLabel,"
            + "DropoffCohort,DropoffAffected,ConverterCohort,ConverterAffected,"
            + "Lift,PValue,ExampleSessions";

    public static final String EVENT_CATALOG_ENTRIES_COLS = "ProjectId,FilterKey,FilterValue";
  }

  /** Attribution precompute window and cap values (matches ClickHouse DAO). */
  public static final class Attribution {
    private Attribution() {}

    public static final int    WINDOW_BEFORE_SEC = 30;
    public static final int    WINDOW_AFTER_SEC  = 60;
    public static final int    EXAMPLES_PER_CAUSE = 50;
    public static final double LIFT_CAP           = 999.0;
  }

  /** Event-catalog batch knobs. */
  public static final class Catalog {
    private Catalog() {}

    public static final int COLD_LOOKBACK_DAYS = 7;
    public static final int DEFAULT_CHUNK_SIZE  = 5_000;
  }
}
