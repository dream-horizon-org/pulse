package org.dreamhorizon.pulsespark;

/**
 * Shared Spark job constants (names, modes, table targets, aggregations, etc.).
 * Group related values in nested {@code public static final} holder classes (e.g. {@link SparkConstants.VectorLog},
 * {@link SparkConstants.ClickHouse}).
 */
public final class SparkConstants {

  private SparkConstants() {}

  /**
   * Reserved uplift aliases: never duplicate these via props JSON projection.
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
    for (String c : VectorLog.LOG_READ_COLUMNS) {
      if (c.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  /** Top-level Vector parquet fields used after read projection. */
  public static final class VectorLog {
    private VectorLog() {}

    public static final String EVENT_NAME = "event_name";
    public static final String PROJECT_ID = "project_id";
    public static final String SESSION_ID = "session_id";
    public static final String TIMESTAMP = "timestamp";
    public static final String OS_NAME = "os_name";
    public static final String OS_VERSION = "os_version";
    public static final String APP_BUILD_NAME = "app_build_name";
    public static final String DEVICE_MANUFACTURER = "device_manufacturer";
    public static final String DEVICE_MODEL_IDENTIFIER = "device_model_identifier";
    public static final String NETWORK_CARRIER_ICC = "network_carrier_icc";
    public static final String SCREEN_NAME = "screen_name";
    public static final String SERVICE_NAME = "service_name";

    /** Raw JSON column on Vector parquet before projection. */
    public static final String PROPS = "props";

    /** Columns read from parquet for funnel/journey (order preserved for stable schemas). */
    public static final String[] LOG_READ_COLUMNS = {
        EVENT_NAME,
        PROJECT_ID,
        SESSION_ID,
        TIMESTAMP,
        OS_NAME,
        OS_VERSION,
        APP_BUILD_NAME,
        DEVICE_MANUFACTURER,
        DEVICE_MODEL_IDENTIFIER,
        NETWORK_CARRIER_ICC,
        SCREEN_NAME,
        SERVICE_NAME,
    };
  }

  /** Columns materialized in {@link FunnelComputeJob#buildReadExprs}. */
  public static final class Derived {
    private Derived() {}

    /** Coalesce: {@code props.user_id} then {@code props['app.installation.id']}. */
    public static final String USER_ID = "user_id";

    /** UNIQUE_USERS identity: {@code props['app.installation.id']} only. */
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
  }
}
