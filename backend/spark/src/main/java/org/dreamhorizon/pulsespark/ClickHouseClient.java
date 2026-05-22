package org.dreamhorizon.pulsespark;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.dreamhorizon.pulsespark.model.FunnelAttributionRow;
import org.dreamhorizon.pulsespark.model.FunnelResult;
import org.dreamhorizon.pulsespark.model.FunnelSessionState;
import org.dreamhorizon.pulsespark.model.FunnelUserState;
import org.dreamhorizon.pulsespark.model.JourneyTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClickHouseClient {

  private static final Logger log = LoggerFactory.getLogger(ClickHouseClient.class);

  private final HttpClient http;
  private final URI baseUri;
  private final String db;

  /**
   * ClickHouse HTTP interface over HTTPS or HTTP URL (tunnel, Ingress). Credentials are appended as
   * query params.
   */
  public static ClickHouseClient fromHttpEndpoint(String httpEndpoint, String db, String user, String password) {
    try {
      return new ClickHouseClient(resolveHttpEndpoint(httpEndpoint.trim(), db, user, password), db);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Invalid ClickHouse URL: " + httpEndpoint, e);
    }
  }

  /** {@code http://host:port} (typical HTTP port {@code 8123}). */
  public ClickHouseClient(String host, int port, String db, String user, String password) {
    this(
        URI.create(
            String.format(
                "http://%s:%d/?database=%s&user=%s&password=%s",
                host,
                port,
                URLEncoder.encode(db, StandardCharsets.UTF_8),
                URLEncoder.encode(user, StandardCharsets.UTF_8),
                URLEncoder.encode(password, StandardCharsets.UTF_8))),
        db);
  }

  private ClickHouseClient(URI baseUri, String db) {
    this.db = db;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    this.baseUri = baseUri;
  }

  static boolean looksLikeHttpEndpointUrl(String value) {
    if (value == null) {
      return false;
    }
    String s = value.trim().toLowerCase();
    return s.startsWith("https://") || s.startsWith("http://");
  }

  private static URI resolveHttpEndpoint(String httpEndpoint, String db, String user, String password)
      throws URISyntaxException {
    URI in = new URI(httpEndpoint);
    String scheme = in.getScheme();
    if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
      throw new URISyntaxException(httpEndpoint, "URL must use http or https");
    }
    if (in.getRawAuthority() == null || in.getRawAuthority().isBlank()) {
      throw new URISyntaxException(httpEndpoint, "URL missing host");
    }
    String rawPath = in.getRawPath();
    if (rawPath == null || rawPath.isEmpty()) {
      rawPath = "/";
    }
    String basePart = scheme + "://" + in.getRawAuthority() + rawPath;
    String creds =
        String.format(
            "database=%s&user=%s&password=%s",
            URLEncoder.encode(db, StandardCharsets.UTF_8),
            URLEncoder.encode(user, StandardCharsets.UTF_8),
            URLEncoder.encode(password, StandardCharsets.UTF_8));
    String mergedQuery =
        (in.getRawQuery() != null && !in.getRawQuery().isBlank())
            ? in.getRawQuery() + "&" + creds
            : creds;
    return new URI(basePart + "?" + mergedQuery);
  }

  public void ping() {
    execute("ping", "SELECT 1");
  }

  public void insertFunnelResults(List<FunnelResult> rows) {
    if (rows.isEmpty()) {
      log.warn("insertFunnelResults: no rows to insert");
      return;
    }
    var sb = new StringBuilder()
        .append("INSERT INTO ").append(db).append(".funnel_results ")
        .append("(FunnelId,ProjectId,RunTime,StepIndex,StepName,UserCount,ConversionPct,MedianStepSeconds) VALUES ");
    for (int i = 0; i < rows.size(); i++) {
      var r = rows.get(i);
      if (i > 0) {
        sb.append(',');
      }
      String medianVal = r.medianStepSeconds() == null ? "NULL" : String.valueOf(r.medianStepSeconds());
      sb.append(String.format("('%d','%s','%s',%d,'%s',%d,%.4f,%s)",
          r.funnelId(), esc(r.projectId()), esc(r.runTime()),
          r.stepIndex(), esc(r.stepName()), r.userCount(), r.conversionPct(), medianVal
      ));
    }
    execute("insertFunnelResults", sb.toString());
    log.info("Inserted {} funnel_result rows", rows.size());
  }

  public void insertJourneyResults(List<JourneyTransition> rows) {
    if (rows.isEmpty()) {
      log.warn("insertJourneyResults: no rows to insert");
      return;
    }
    var sb = new StringBuilder()
        .append("INSERT INTO ").append(db).append(".journey_results ")
        .append("(JourneyId,ProjectId,RunTime,Direction,PosFrom,EventFrom,PosTo,EventTo,UserCount) VALUES ");
    for (int i = 0; i < rows.size(); i++) {
      var r = rows.get(i);
      if (i > 0) {
        sb.append(',');
      }
      sb.append(String.format("('%d','%s','%s','%s',%d,'%s',%d,'%s',%d)",
          r.journeyId(), esc(r.projectId()), esc(r.runTime()),
          esc(r.direction()), r.posFrom(), esc(r.eventFrom()),
          r.posTo(), esc(r.eventTo()), r.userCount()
      ));
    }
    execute("insertJourneyResults", sb.toString());
    log.info("Inserted {} journey_result rows", rows.size());
  }

  /**
   * Chunked insert into {@code otel.funnel_session_state} — one row per session that
   * entered the funnel. Uses {@link #bulkInsert} under the hood so large cohorts
   * (millions of rows) don't balloon into a single HTTP POST.
   */
  public void insertFunnelSessionState(List<FunnelSessionState> rows) {
    if (rows.isEmpty()) {
      log.warn("insertFunnelSessionState: no rows to insert");
      return;
    }
    var columnList =
        "FunnelId,ProjectId,RunTime,SessionId,UserId,"
            + "LastReachedStep,LastReachedStepName,LastReachedAt,"
            + "DropoffStep,TimeToDropoffSec,ScreenAtDropoff,TraceIdAtDropoff,"
            + "AppVersion,OsName,OsVersion,Platform,DeviceModel,NetworkProvider,GeoCountry";
    var values = new java.util.ArrayList<String>(rows.size());
    for (var r : rows) {
      values.add(String.format(
          "('%d','%s','%s','%s','%s',%d,'%s',toDateTime64(%d,3,'UTC'),%d,%d,'%s','%s',"
              + "'%s','%s','%s','%s','%s','%s','%s')",
          r.funnelId(), esc(r.projectId()), esc(r.runTime()),
          esc(r.sessionId()), esc(r.userId()),
          r.lastReachedStep(), esc(r.lastReachedStepName()),
          r.lastReachedAtEpochSec(),
          r.dropoffStep(), r.timeToDropoffSec(),
          esc(r.screenAtDropoff()), esc(r.traceIdAtDropoff()),
          esc(r.appVersion()), esc(r.osName()), esc(r.osVersion()),
          esc(r.platform()), esc(r.deviceModel()),
          esc(r.networkProvider()), esc(r.geoCountry())
      ));
    }
    bulkInsert("funnel_session_state", columnList, values, 5000);
    log.info("Inserted {} funnel_session_state rows", rows.size());
  }

  /**
   * Chunked insert into {@code otel.funnel_user_state} — per-user rollup with a
   * canonical-session anchor. Only produced for funnels whose {@code mode} is
   * {@code UNIQUE_USERS}; SESSIONS mode funnels skip this call entirely.
   */
  public void insertFunnelUserState(List<FunnelUserState> rows) {
    if (rows.isEmpty()) {
      log.warn("insertFunnelUserState: no rows to insert");
      return;
    }
    var columnList =
        "FunnelId,ProjectId,RunTime,UserId,MaxReachedStep,DropoffStep,"
            + "CanonicalSessionId,CanonicalLastReachedAt,CanonicalTraceIdAtDropoff,"
            + "CanonicalScreenAtDropoff,AppVersion,OsName,OsVersion,Platform,DeviceModel,"
            + "NetworkProvider,GeoCountry,SessionAttempts";
    var values = new java.util.ArrayList<String>(rows.size());
    for (var r : rows) {
      values.add(String.format(
          "('%d','%s','%s','%s',%d,%d,'%s',toDateTime64(%d,3,'UTC'),'%s','%s',"
              + "'%s','%s','%s','%s','%s','%s','%s',%d)",
          r.funnelId(), esc(r.projectId()), esc(r.runTime()),
          esc(r.userId()), r.maxReachedStep(), r.dropoffStep(),
          esc(r.canonicalSessionId()), r.canonicalLastReachedAtEpochSec(),
          esc(r.canonicalTraceIdAtDropoff()), esc(r.canonicalScreenAtDropoff()),
          esc(r.appVersion()), esc(r.osName()), esc(r.osVersion()),
          esc(r.platform()), esc(r.deviceModel()),
          esc(r.networkProvider()), esc(r.geoCountry()),
          r.sessionAttempts()
      ));
    }
    bulkInsert("funnel_user_state", columnList, values, 5000);
    log.info("Inserted {} funnel_user_state rows", rows.size());
  }

  /**
   * Chunked insert into {@code otel.funnel_dropoff_attribution} — precomputed (step × cause)
   * ranking rows. Side-panel reads from this table first; falls back to a live OTel join only
   * when no rows exist for the requested {@code (FunnelId, RunTime)}.
   *
   * <p>{@code PValue} is stubbed to {@code 0.0} (chi-square deferred — same as the CH
   * compute path).
   */
  public void insertFunnelDropoffAttribution(List<FunnelAttributionRow> rows) {
    if (rows.isEmpty()) {
      log.warn("insertFunnelDropoffAttribution: no rows to insert");
      return;
    }
    var columnList =
        "FunnelId,ProjectId,RunTime,StepIndex,CauseKind,CauseKey,CauseLabel,"
            + "DropoffCohort,DropoffAffected,ConverterCohort,ConverterAffected,"
            + "Lift,PValue,ExampleSessions";
    var values = new java.util.ArrayList<String>(rows.size());
    for (var r : rows) {
      values.add(String.format(
          "('%d','%s','%s',%d,'%s','%s','%s',%d,%d,%d,%d,%.4f,%.4f,%s)",
          r.funnelId(), esc(r.projectId()), esc(r.runTime()),
          r.stepIndex(), esc(r.causeKind()), esc(r.causeKey()), esc(r.causeLabel()),
          r.dropoffCohort(), r.dropoffAffected(),
          r.converterCohort(), r.converterAffected(),
          r.lift(), r.pValue(),
          formatStringArray(r.exampleSessions())
      ));
    }
    bulkInsert("funnel_dropoff_attribution", columnList, values, 5000);
    log.info("Inserted {} funnel_dropoff_attribution rows", rows.size());
  }

  /**
   * Formats a list of session IDs as a ClickHouse Array(String) literal, e.g.
   * {@code ['sess-1','sess-2','sess-3']}. Caps at 50 entries to match the CH writer.
   */
  private static String formatStringArray(List<String> values) {
    if (values == null || values.isEmpty()) {
      return "[]";
    }
    int cap = Math.min(values.size(), 50);
    var sb = new StringBuilder("[");
    for (int i = 0; i < cap; i++) {
      if (i > 0) sb.append(',');
      sb.append('\'').append(esc(values.get(i))).append('\'');
    }
    sb.append(']');
    return sb.toString();
  }

  public void bulkInsert(String table, String columnList, List<String> valueRows, int chunkSize) {
    if (valueRows.isEmpty()) {
      log.warn("bulkInsert {}: no rows to insert", table);
      return;
    }
    for (int offset = 0; offset < valueRows.size(); offset += chunkSize) {
      var batch = valueRows.subList(offset, Math.min(offset + chunkSize, valueRows.size()));
      var sql = "INSERT INTO " + db + "." + table + " (" + columnList + ") VALUES "
          + String.join(",", batch);
      execute("bulkInsert:" + table, sql);
    }
    log.info("bulkInsert {}: inserted {} rows", table, valueRows.size());
  }

  private void execute(String operation, String sql) {
    log.info("ClickHouse operation={} sqlBytes={}", operation, sql.length());
    var request = HttpRequest.newBuilder()
        .uri(baseUri)
        .header("Content-Type", "text/plain; charset=utf-8")
        .POST(HttpRequest.BodyPublishers.ofString(sql, StandardCharsets.UTF_8))
        .timeout(Duration.ofSeconds(120))
        .build();
    try {
      var response = http.send(request, HttpResponse.BodyHandlers.ofString());
      var body = response.body() == null ? "" : response.body();
      if (response.statusCode() != 200) {
        var preview = body.substring(0, Math.min(500, body.length()));
        log.error("ClickHouse {} failed: status={} body={}", operation, response.statusCode(), preview);
        throw new RuntimeException("ClickHouse [%d]: %s".formatted(response.statusCode(), preview));
      }
    } catch (IOException | InterruptedException e) {
      log.error("ClickHouse {} request failed: {}", operation, e.getMessage(), e);
      throw new RuntimeException("ClickHouse request failed: " + e.getMessage(), e);
    }
  }

  private static String esc(String s) {
    return s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'");
  }
}
