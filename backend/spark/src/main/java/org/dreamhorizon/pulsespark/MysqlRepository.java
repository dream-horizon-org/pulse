package org.dreamhorizon.pulsespark;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
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
    this.jdbcUrl = buildJdbcUrl(host, port, db);
    this.user = user;
    this.password = password;
  }

  /**
   * {@code host} may be a hostname or {@code https://host[:port]} / {@code http://host[:port]} (e.g. tunnel URL).
   * For {@code https://} URLs the JDBC port defaults to 443 if omitted; {@code useSSL=true} is set.
   */
  private static String buildJdbcUrl(String host, int defaultPort, String db) {
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("mysql_host is required");
    }
    String h = host.trim();
    if (h.startsWith("https://")) {
      URI u = URI.create(h);
      String hostname = u.getHost();
      if (hostname == null) {
        throw new IllegalArgumentException("Invalid MySQL URL (missing host): " + h);
      }
      int p = u.getPort();
      if (p == -1) {
        p = 443;
      }
      return "jdbc:mysql://%s:%d/%s?useSSL=true&allowPublicKeyRetrieval=true&verifyServerCertificate=true"
          .formatted(hostname, p, db);
    }
    if (h.startsWith("http://")) {
      URI u = URI.create(h);
      String hostname = u.getHost();
      if (hostname == null) {
        throw new IllegalArgumentException("Invalid MySQL URL (missing host): " + h);
      }
      int p = u.getPort();
      if (p == -1) {
        p = 80;
      }
      return "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true".formatted(hostname, p, db);
    }
    return "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true".formatted(h, defaultPort, db);
  }

  /**
   * Fetches funnels. If referenceId is non-null, fetches the single funnel with that id.
   * Otherwise fetches all AUTO funnels whose end_time is null or in the future.
   */
  public List<FunnelDefinition> fetchFunnels(Long referenceId) throws Exception {
    var sql = referenceId != null
        ? "SELECT * FROM funnel WHERE id = ?"
        : "SELECT * FROM funnel WHERE funnel_type = 'AUTO' AND (end_time IS NULL OR end_time >= NOW())";

    var results = new ArrayList<FunnelDefinition>();
    try (var conn = DriverManager.getConnection(jdbcUrl, user, password);
         var stmt = conn.prepareStatement(sql)) {

      if (referenceId != null) {
        stmt.setLong(1, referenceId);
      }
      var rs = stmt.executeQuery();

      while (rs.next()) {
        List<FunnelStep> steps = MAPPER.readValue(rs.getString("steps_json"), STEPS_TYPE);
        List<FunnelFilter> filters = rs.getString("filters_json") != null
            ? MAPPER.readValue(rs.getString("filters_json"), FILTERS_TYPE)
            : List.of();

        results.add(new FunnelDefinition(
            rs.getLong("id"),
            rs.getString("project_id"),
            steps,
            rs.getLong("window_seconds"),
            rs.getString("mode"),
            rs.getInt("date_range"),
            filters,
            rs.getString("funnel_type"),
            rs.getString("step_order_type"),
            rs.getTimestamp("start_time"),
            rs.getTimestamp("end_time")
        ));
      }
    }
    return results;
  }

  /**
   * Fetches journeys. If referenceId is non-null, fetches the single journey with that id.
   * Otherwise fetches all AUTO journeys whose end_time is null or in the future.
   */
  public List<JourneyDefinition> fetchJourneys(Long referenceId) throws Exception {
    var sql = referenceId != null
        ? "SELECT * FROM journey WHERE id = ?"
        : "SELECT * FROM journey WHERE journey_type = 'AUTO' AND (end_time IS NULL OR end_time >= NOW())";

    var results = new ArrayList<JourneyDefinition>();
    try (var conn = DriverManager.getConnection(jdbcUrl, user, password);
         var stmt = conn.prepareStatement(sql)) {

      if (referenceId != null) {
        stmt.setLong(1, referenceId);
      }
      var rs = stmt.executeQuery();

      while (rs.next()) {
        List<FunnelFilter> filters = rs.getString("filters_json") != null
            ? MAPPER.readValue(rs.getString("filters_json"), FILTERS_TYPE)
            : List.of();

        results.add(new JourneyDefinition(
            rs.getLong("id"),
            rs.getString("project_id"),
            rs.getString("anchor_event"),
            rs.getString("direction"),
            rs.getInt("depth"),
            rs.getString("mode"),
            rs.getInt("date_range"),
            filters,
            rs.getString("journey_type"),
            rs.getTimestamp("start_time"),
            rs.getTimestamp("end_time")
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
             "SELECT MAX(started_at) AS ts FROM analytics_jobs "
                 + "WHERE job_type = 'EVENTS_INCREMENTAL' "
                 + "AND status = 'SUCCEEDED' AND started_at IS NOT NULL")) {
      var rs = stmt.executeQuery();
      if (!rs.next() || rs.getTimestamp("ts") == null) {
        return Optional.empty();
      }
      return Optional.of(rs.getTimestamp("ts"));
    }
  }

  /**
   * Returns all distinct project IDs from the {@code projects} table.
   */
  public List<String> fetchProjectIds() throws SQLException {
    var ids = new ArrayList<String>();
    try (var conn = DriverManager.getConnection(jdbcUrl, user, password);
         var stmt = conn.createStatement();
         var rs = stmt.executeQuery("SELECT DISTINCT project_id FROM projects")) {
      while (rs.next()) {
        ids.add(rs.getString(1));
      }
    }
    return ids;
  }

  public void updateAnalyticsJobRunning(long analyticsJobId) throws SQLException {
    try (var conn = DriverManager.getConnection(jdbcUrl, user, password);
         var stmt = conn.prepareStatement(
             "UPDATE analytics_jobs SET status = 'RUNNING', started_at = NOW() WHERE id = ?")) {
      stmt.setLong(1, analyticsJobId);
      stmt.executeUpdate();
    }
  }

  public void updateAnalyticsJobSucceeded(long analyticsJobId) throws SQLException {
    try (var conn = DriverManager.getConnection(jdbcUrl, user, password);
         var stmt = conn.prepareStatement(
             "UPDATE analytics_jobs SET status = 'SUCCEEDED', completed_at = NOW() WHERE id = ?")) {
      stmt.setLong(1, analyticsJobId);
      stmt.executeUpdate();
    }
  }

  public void updateAnalyticsJobFailed(long analyticsJobId, String errorMessage) throws SQLException {
    try (var conn = DriverManager.getConnection(jdbcUrl, user, password);
         var stmt = conn.prepareStatement(
             "UPDATE analytics_jobs SET status = 'FAILED', error_message = ?, completed_at = NOW() WHERE id = ?")) {
      stmt.setString(1, errorMessage != null && errorMessage.length() > 2000
          ? errorMessage.substring(0, 2000) : errorMessage);
      stmt.setLong(2, analyticsJobId);
      stmt.executeUpdate();
    }
  }

}
