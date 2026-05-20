package org.dreamhorizon.pulsespark;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Shared Spark job constants (names, modes, table targets, aggregations, etc.).
 * Group related values in nested {@code public static final} holder classes (e.g. {@link SparkConstants.OtelLogColumn},
 * {@link SparkConstants.ClickHouse}).
 */
public final class SparkConstants {

  private SparkConstants() {}

  /**
   * Reserved columns: parquet materialized OTL fields + uplift aliases for UNIQUE_USERS identity.
   *
   * @see FunnelComputeJob#buildReadExprs
   */
  public static boolean isReservedDerivedColumn(String name) {
    if (name == null || name.isBlank()) {
      return false;
    }
    if (name.equalsIgnoreCase(Derived.USER_ID) || name.equalsIgnoreCase(Derived.INSTALLATION_ID)) {
      return true;
    }
    for (String c : OtelLogColumn.PARQUET_READ_COLUMNS) {
      if (c.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Parquet column names aligned with ClickHouse {@code otel.otel_logs} (Pulse parquet export contract).
   */
  public static final class OtelLogColumn {

    private OtelLogColumn() {}

    public static final String TIMESTAMP = "Timestamp";
    public static final String PROJECT_ID = "ProjectId";
    public static final String SESSION_ID = "SessionId";
    public static final String EVENT_NAME = "EventName";
    public static final String SCREEN_NAME = "ScreenName";
    public static final String PULSE_TYPE = "PulseType";
    /** From {@code ResourceAttributes['os.name']}. */
    public static final String PLATFORM = "Platform";
    public static final String OS_VERSION = "OsVersion";
    /** From {@code ResourceAttributes['app.build_name']}. */
    public static final String APP_VERSION = "AppVersion";
    public static final String DEVICE_MODEL = "DeviceModel";
    public static final String NETWORK_PROVIDER = "NetworkProvider";
    public static final String SERVICE_NAME = "ServiceName";

    /** OTL {@code LogAttributes}: Avro/Pulse schema {@code map&lt;string,string&gt;}; filters use depth-1 map keys. */
    public static final String LOG_ATTRIBUTES = "LogAttributes";

    public static final String APP_INSTALLATION_ID = "AppInstallationId";

    public static final String[] PARQUET_READ_COLUMNS = {
        EVENT_NAME,
        PROJECT_ID,
        SESSION_ID,
        TIMESTAMP,
        PLATFORM,
        OS_VERSION,
        APP_VERSION,
        DEVICE_MODEL,
        NETWORK_PROVIDER,
        SCREEN_NAME,
        SERVICE_NAME,
        PULSE_TYPE,
        LOG_ATTRIBUTES,
        APP_INSTALLATION_ID,
    };
  }

  /**
   * Pooled OTEL parquet layout under {@link #basePath}: Hive-style folders
   * {@code year=YYYY/month=MM/day=DD/}, then parquet objects (filenames opaque to readers;
   * {@code recursiveFileLookup} loads nested files).
   *
   * @see FunnelComputeJob#readS3ByDateRange
   * @see FunnelComputeJob#readS3ByHours
   */
  public static final class OtelLogsS3 {
    private OtelLogsS3() {}

    /** Default when {@code --s3_bucket_prefix} is omitted. */
    public static final String DEFAULT_BUCKET = "pulse-otel-ingestion";

    /** Key segment under {@code /<projectId>/}. */
    public static final String KEY_PREFIX_SEGMENT = "otel_logs";

    /**
     * @param bucket pooled bucket only (no {@code s3://} prefix)
     * @param projectId {@code ResourceAttributes['project.id']}-style project id string
     */
    public static String basePath(String bucket, String projectId) {
      return "s3a://" + bucket + "/" + projectId + "/" + KEY_PREFIX_SEGMENT + "/";
    }

    /**
     * S3 prefix for one calendar day ({@code …/otel_logs/year=…/month=…/day=…/}).
     *
     * @param otelLogsBasePath {@link #basePath} output (must end with {@code /})
     */
    public static String partitionPrefixForDay(String otelLogsBasePath, LocalDate d) {
      return String.format(
          Locale.ROOT,
          "%syear=%d/month=%02d/day=%02d/",
          otelLogsBasePath,
          d.getYear(),
          d.getMonthValue(),
          d.getDayOfMonth());
    }
  }

  /** Columns materialized in {@link FunnelComputeJob#buildReadExprs}. */
  public static final class Derived {
    private Derived() {}

    /** Logical uplift from {@link OtelLogColumn#APP_INSTALLATION_ID}; matches UNIQUE_USERS grain. */
    public static final String USER_ID = "user_id";

    /** Same as installation id uplift (see {@link OtelLogColumn#APP_INSTALLATION_ID}). */
    public static final String INSTALLATION_ID = "installation_id";
  }

  /** {@code props} JSON paths and logical keys. */
  public static final class PropsJson {
    private PropsJson() {}

    public static final String PATH_USER_ID = "$.user_id";
    public static final String PATH_APP_INSTALLATION_ID = "$['app.installation.id']";

    public static final String KEY_USER_ID = "user_id";
    public static final String KEY_APP_INSTALLATION_ID = "app.installation.id";
  }

  /** Intermediate Spark dataframe column names (funnel + journey analytics). */
  public static final class AnalyticsDataset {
    private AnalyticsDataset() {}

    public static final String IDENTITY = "identity";
    public static final String TS = "ts";
    public static final String TS0 = "ts0";
    public static final String TS_PREV = "ts_prev";
    /** Prefix for per-step completion timestamps in median timeline ({@code ts_1}, …). */
    public static final String TS_STEP_PREFIX = "ts_";

    public static final String STEP_IDX = "step_idx";
    public static final String STEPS_IN_WINDOW = "steps_in_window";
    public static final String MAX_STEPS = "max_steps";

    public static final String TS_ANCHOR = "ts_anchor";
    public static final String POS = "pos";
    public static final String USER_COUNT = "user_count";

    public static final String POS_FROM = "pos_from";
    public static final String EVENT_FROM = "event_from";
    public static final String POS_TO = "pos_to";
    public static final String EVENT_TO = "event_to";

    public static final String JOIN_PREV = "prev";
    public static final String JOIN_NEXT = "next";
    public static final String ID_I = "id_i";
    public static final String TS_I = "ts_i";

    public static final String JOIN_CURR = "curr";
    public static final String JOIN_NXT = "nxt";

    public static final String MEDIAN_JOIN_IDENTITY = "_j_identity";
    public static final String MEDIAN_JOIN_TS0 = "_j_ts0";
    public static final String MEDIAN_SCORE = "_score";
    public static final String MEDIAN_RN = "_rn";
    public static final String MEDIAN_AGG = "med";

    /** Session column carried through funnel bridge joins (event row). */
    public static final String BRIDGE_EVT_SESSION_ID = "bridge_evt_session_id";

    /** Uplifted installation id on funnel bridge paths ({@link Derived#USER_ID}). */
    public static final String BRIDGE_USER_ID_COL = "bridge_uplift_user_id";
  }

  /** Values from MySQL funnel/journey definitions interpreted by Spark jobs. */
  public static final class DefinitionModes {
    private DefinitionModes() {}

    public static final String FUNNEL_SESSIONS = "SESSIONS";
    public static final String JOURNEY_UNIQUE_USERS = "UNIQUE_USERS";
    public static final String JOURNEY_DIRECTION_START = "START";
  }

  /** Event catalog job: FilterKey values and working aliases before ClickHouse insert. */
  public static final class EventCatalog {
    private EventCatalog() {}

    public static final String FILTER_KEY_EVENT = "EVENT";
    public static final String FILTER_KEY_APP_BUILD_NAME = "APP_BUILD_NAME";
    public static final String FILTER_KEY_OS_VERSION = "OS_VERSION";
    public static final String FILTER_KEY_OS_NAME = "OS_NAME";

    public static final String ALIAS_PID = "pid";
    public static final String ALIAS_FK = "fk";
    public static final String ALIAS_FV = "fv";
  }

  /** ClickHouse HTTP insert targets for Spark writers. */
  public static final class ClickHouse {
    private ClickHouse() {}

    public static final String TABLE_FUNNEL_RESULTS = "funnel_results";
    public static final String TABLE_JOURNEY_RESULTS = "journey_results";
    public static final String TABLE_EVENT_CATALOG_ENTRIES = "event_catalog_entries";

    public static final String INSERT_COLUMNS_FUNNEL_RESULTS =
        "FunnelId,ProjectId,RunTime,StepIndex,StepName,UserCount,ConversionPct,MedianStepSeconds";

    public static final String INSERT_COLUMNS_JOURNEY_RESULTS =
        "JourneyId,ProjectId,RunTime,Direction,PosFrom,EventFrom,PosTo,EventTo,UserCount";

    public static final String INSERT_COLUMNS_EVENT_CATALOG_ENTRIES =
        "ProjectId,FilterKey,FilterValue";

    /** Align name with deployed MergeTree; use {@code *_local} if your cluster uses suffixed tables. */
    public static final String TABLE_FUNNEL_SESSION_STATE = "funnel_session_state";

    public static final String TABLE_FUNNEL_USER_STATE = "funnel_user_state";

    /**
     * Nullable tail columns filled with NULL until hydrated from signals / parquet metadata.
     */
    public static final String INSERT_COLUMNS_FUNNEL_SESSION_STATE =
        "FunnelId,ProjectId,RunTime,SessionId,UserId,LastReachedStep,LastReachedStepName,LastReachedAt,DropoffStep,"
            + "TimeToDropoffSec,ScreenAtDropoff,TraceIdAtDropoff,AppVersion,OsName,OsVersion,Platform,DeviceModel,"
            + "NetworkProvider,GeoCountry";

    public static final String INSERT_COLUMNS_FUNNEL_USER_STATE =
        "FunnelId,ProjectId,RunTime,UserId,MaxReachedStep,DropoffStep,CanonicalSessionId,CanonicalLastReachedAt,"
            + "CanonicalTraceIdAtDropoff,CanonicalScreenAtDropoff,AppVersion,OsName,OsVersion,Platform,DeviceModel,"
            + "NetworkProvider,GeoCountry,SessionAttempts";
  }
}
