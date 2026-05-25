package org.dreamhorizon.pulsespark;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.dreamhorizon.pulsespark.model.FunnelDefinition;
import org.dreamhorizon.pulsespark.model.FunnelFilter;
import org.dreamhorizon.pulsespark.model.FunnelStep;
import org.dreamhorizon.pulsespark.model.JourneyDefinition;

public class MysqlRepository {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<FunnelStep>> STEPS_TYPE = new TypeReference<>() {
  };
  private static final TypeReference<List<FunnelFilter>> FILTERS_TYPE = new TypeReference<>() {
  };

  private final String jdbcUrl;
  private final String user;
  private final String password;

  public MysqlRepository(String host, int port, String db, String user, String password) {
    this.jdbcUrl = "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true"
      .formatted(host, port, db);
    this.user = user;
    this.password = password;
  }

  /**
   * Fetches funnels. If referenceId is non-null, fetches the single funnel with that id.
   * Otherwise fetches all AUTO funnels whose expiry is null or in the future.
   */
  public List<FunnelDefinition> fetchFunnels(Long referenceId) throws Exception {
    var sql = referenceId != null
      ? "SELECT * FROM " + SparkConstants.MysqlTables.FUNNEL + " WHERE id = ?"
      : "SELECT * FROM " + SparkConstants.MysqlTables.FUNNEL + " WHERE funnel_type = '"
          + SparkConstants.JobStatus.AUTO + "' AND (expiry IS NULL OR expiry >= NOW())";

    var results = new ArrayList<FunnelDefinition>();
    try (var conn = DriverManager.getConnection(jdbcUrl, user, password);
         var stmt = conn.prepareStatement(sql)) {

      if (referenceId != null) {
        stmt.setLong(1, referenceId);
      }
      var rs = stmt.executeQuery();
      Set<String> cols = resultColumns(rs);

      while (rs.next()) {
        List<FunnelStep> steps = MAPPER.readValue(rs.getString(SparkConstants.MysqlColumns.STEPS_JSON), STEPS_TYPE);
        List<FunnelFilter> filters = rs.getString(SparkConstants.MysqlColumns.FILTERS_JSON) != null
          ? MAPPER.readValue(rs.getString(SparkConstants.MysqlColumns.FILTERS_JSON), FILTERS_TYPE)
          : List.of();

        String revenueAttribute = cols.contains(SparkConstants.MysqlColumns.REVENUE_ATTRIBUTE)
            ? rs.getString(SparkConstants.MysqlColumns.REVENUE_ATTRIBUTE) : null;
        Integer revenueStepIndex = null;
        if (cols.contains(SparkConstants.MysqlColumns.REVENUE_STEP_INDEX)) {
          int v = rs.getInt(SparkConstants.MysqlColumns.REVENUE_STEP_INDEX);
          revenueStepIndex = rs.wasNull() ? null : v;
        }
        String currency = cols.contains(SparkConstants.MysqlColumns.CURRENCY)
            ? rs.getString(SparkConstants.MysqlColumns.CURRENCY) : null;

        if ((revenueAttribute == null || revenueAttribute.isBlank())
            && "fancode".equals(rs.getString(SparkConstants.MysqlColumns.PROJECT_ID))) {
          revenueAttribute = "order.value";
          if (revenueStepIndex == null) {
            revenueStepIndex = 4;
          }
          if (currency == null) {
            currency = "INR";
          }
        }

        results.add(new FunnelDefinition(
          rs.getLong(SparkConstants.MysqlColumns.ID),
          rs.getString(SparkConstants.MysqlColumns.PROJECT_ID),
          steps,
          rs.getLong(SparkConstants.MysqlColumns.WINDOW_SECONDS),
          rs.getString(SparkConstants.MysqlColumns.MODE),
          rs.getInt(SparkConstants.MysqlColumns.DATE_RANGE),
          filters,
          rs.getString(SparkConstants.MysqlColumns.FUNNEL_TYPE),
          rs.getString(SparkConstants.MysqlColumns.STEP_ORDER_TYPE),
          rs.getTimestamp(SparkConstants.MysqlColumns.START_TIME),
          rs.getTimestamp(SparkConstants.MysqlColumns.END_TIME),
          revenueAttribute,
          revenueStepIndex,
          currency
        ));
      }
    }
    return results;
  }

  private static Set<String> resultColumns(ResultSet rs) throws SQLException {
    ResultSetMetaData md = rs.getMetaData();
    Set<String> names = new HashSet<>(md.getColumnCount() * 2);
    for (int i = 1; i <= md.getColumnCount(); i++) {
      names.add(md.getColumnLabel(i).toLowerCase());
    }
    return names;
  }

  /**
   * Fetches journeys. If referenceId is non-null, fetches the single journey with that id.
   * Otherwise fetches all AUTO journeys whose expiry is null or in the future.
   */
  public List<JourneyDefinition> fetchJourneys(Long referenceId) throws Exception {
    var sql = referenceId != null
      ? "SELECT * FROM " + SparkConstants.MysqlTables.JOURNEY + " WHERE id = ?"
      : "SELECT * FROM " + SparkConstants.MysqlTables.JOURNEY + " WHERE journey_type = '"
          + SparkConstants.JobStatus.AUTO + "' AND (expiry IS NULL OR expiry >= NOW())";

    var results = new ArrayList<JourneyDefinition>();
    try (var conn = DriverManager.getConnection(jdbcUrl, user, password);
         var stmt = conn.prepareStatement(sql)) {

      if (referenceId != null) {
        stmt.setLong(1, referenceId);
      }
      var rs = stmt.executeQuery();

      while (rs.next()) {
        List<FunnelFilter> filters = rs.getString(SparkConstants.MysqlColumns.FILTERS_JSON) != null
          ? MAPPER.readValue(rs.getString(SparkConstants.MysqlColumns.FILTERS_JSON), FILTERS_TYPE)
          : List.of();

        results.add(new JourneyDefinition(
          rs.getLong(SparkConstants.MysqlColumns.ID),
          rs.getString(SparkConstants.MysqlColumns.PROJECT_ID),
          rs.getString(SparkConstants.MysqlColumns.ANCHOR_EVENT),
          rs.getString(SparkConstants.MysqlColumns.DIRECTION),
          rs.getInt(SparkConstants.MysqlColumns.DEPTH),
          rs.getString(SparkConstants.MysqlColumns.MODE),
          rs.getInt(SparkConstants.MysqlColumns.DATE_RANGE),
          filters,
          rs.getString(SparkConstants.MysqlColumns.JOURNEY_TYPE),
          rs.getTimestamp(SparkConstants.MysqlColumns.START_TIME),
          rs.getTimestamp(SparkConstants.MysqlColumns.END_TIME)
        ));
      }
    }
    return results;
  }

  /**
   * Latest {@code started_at} among succeeded event-catalog Spark jobs (UTC).
   * Matches Spark {@code --job_type EVENTS_INCREMENTAL}.
   * Current run is still {@code RUNNING}, so it is naturally excluded until it succeeds.
   */
  public Optional<Timestamp> getLatestSucceededEventCatalogJobStartedAt() throws SQLException {
    try (var conn = DriverManager.getConnection(jdbcUrl, user, password);
         var stmt = conn.prepareStatement(
           "SELECT MAX(" + SparkConstants.MysqlColumns.STARTED_AT + ") AS " + SparkConstants.MysqlColumns.TS
             + " FROM " + SparkConstants.MysqlTables.ANALYTICS_JOBS
             + " WHERE " + SparkConstants.MysqlColumns.JOB_TYPE + " = '" + SparkConstants.JobStatus.TYPE_EVENTS_INCREMENTAL + "'"
             + " AND " + SparkConstants.MysqlColumns.STATUS + " = '" + SparkConstants.JobStatus.SUCCEEDED + "'"
             + " AND " + SparkConstants.MysqlColumns.STARTED_AT + " IS NOT NULL")) {
      var rs = stmt.executeQuery();
      if (!rs.next() || rs.getTimestamp(SparkConstants.MysqlColumns.TS) == null) {
        return Optional.empty();
      }
      return Optional.of(rs.getTimestamp(SparkConstants.MysqlColumns.TS));
    }
  }

  /**
   * Returns all distinct project IDs from the {@code projects} table.
   */
  public List<String> fetchProjectIds() throws SQLException {
    var ids = new ArrayList<String>();
    try (var conn = DriverManager.getConnection(jdbcUrl, user, password);
         var stmt = conn.createStatement();
         var rs = stmt.executeQuery(
           "SELECT DISTINCT " + SparkConstants.MysqlColumns.PROJECT_ID + " FROM " + SparkConstants.MysqlTables.PROJECTS)) {
      while (rs.next()) {
        ids.add(rs.getString(1));
      }
    }
    return ids;
  }

  public void updateAnalyticsJobRunning(long analyticsJobId) throws SQLException {
    try (var conn = DriverManager.getConnection(jdbcUrl, user, password);
         var stmt = conn.prepareStatement(
           "UPDATE " + SparkConstants.MysqlTables.ANALYTICS_JOBS
             + " SET " + SparkConstants.MysqlColumns.STATUS + " = '" + SparkConstants.JobStatus.RUNNING + "'"
             + ", " + SparkConstants.MysqlColumns.STARTED_AT + " = NOW()"
             + " WHERE " + SparkConstants.MysqlColumns.ID + " = ?")) {
      stmt.setLong(1, analyticsJobId);
      stmt.executeUpdate();
    }
  }

  public void updateAnalyticsJobSucceeded(long analyticsJobId) throws SQLException {
    try (var conn = DriverManager.getConnection(jdbcUrl, user, password);
         var stmt = conn.prepareStatement(
           "UPDATE " + SparkConstants.MysqlTables.ANALYTICS_JOBS
             + " SET " + SparkConstants.MysqlColumns.STATUS + " = '" + SparkConstants.JobStatus.SUCCEEDED + "'"
             + ", completed_at = NOW()"
             + " WHERE " + SparkConstants.MysqlColumns.ID + " = ?")) {
      stmt.setLong(1, analyticsJobId);
      stmt.executeUpdate();
    }
  }

  public void updateAnalyticsJobFailed(long analyticsJobId, String errorMessage) throws SQLException {
    try (var conn = DriverManager.getConnection(jdbcUrl, user, password);
         var stmt = conn.prepareStatement(
           "UPDATE " + SparkConstants.MysqlTables.ANALYTICS_JOBS
             + " SET " + SparkConstants.MysqlColumns.STATUS + " = '" + SparkConstants.JobStatus.FAILED + "'"
             + ", error_message = ?, completed_at = NOW()"
             + " WHERE " + SparkConstants.MysqlColumns.ID + " = ?")) {
      stmt.setString(1, errorMessage != null && errorMessage.length() > 2000
        ? errorMessage.substring(0, 2000) : errorMessage);
      stmt.setLong(2, analyticsJobId);
      stmt.executeUpdate();
    }
  }
}
