package org.dreamhorizon.pulsespark;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.dreamhorizon.pulsespark.model.FunnelResult;
import org.dreamhorizon.pulsespark.model.JourneyTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClickHouseClient {

  private static final Logger log = LoggerFactory.getLogger(ClickHouseClient.class);

  private final HttpClient http;
  private final URI baseUri;
  private final String db;

  /**
   * ClickHouse HTTP interface. {@code host} may be a bare hostname or a full origin
   * {@code http(s)://host[:port]} (e.g. Cloudflare quick tunnel).
   */
  public ClickHouseClient(String host, int port, String db, String user, String password) {
    this.db = db;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    String origin = host == null ? "" : host.trim();
    if (origin.startsWith("http://") || origin.startsWith("https://")) {
      this.baseUri = buildRequestUri(URI.create(origin), db, user, password);
    } else {
      this.baseUri = buildRequestUri(URI.create("http://" + origin + ":" + port), db, user, password);
    }
  }

  private static URI buildRequestUri(URI origin, String db, String user, String password) {
    var q = "database=%s&user=%s&password=%s".formatted(
        URLEncoder.encode(db, StandardCharsets.UTF_8),
        URLEncoder.encode(user, StandardCharsets.UTF_8),
        URLEncoder.encode(password, StandardCharsets.UTF_8));
    String scheme = origin.getScheme();
    String host = origin.getHost();
    if (scheme == null || host == null) {
      throw new IllegalArgumentException("Invalid ClickHouse URL (need scheme and host): " + origin);
    }
    int port = origin.getPort();
    String authority = port == -1 ? host : host + ":" + port;
    String rawPath = origin.getPath();
    String path;
    if (rawPath == null || rawPath.isEmpty() || "/".equals(rawPath)) {
      path = "/";
    } else {
      path = rawPath.endsWith("/") ? rawPath : rawPath + "/";
    }
    return URI.create("%s://%s%s?%s".formatted(scheme, authority, path, q));
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
        .append("INSERT INTO ").append(db).append(".").append(SparkConstants.ClickHouse.TABLE_FUNNEL_RESULTS).append(" ")
        .append("(").append(SparkConstants.ClickHouse.INSERT_COLUMNS_FUNNEL_RESULTS).append(") VALUES ");
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
        .append("INSERT INTO ").append(db).append(".").append(SparkConstants.ClickHouse.TABLE_JOURNEY_RESULTS).append(" ")
        .append("(").append(SparkConstants.ClickHouse.INSERT_COLUMNS_JOURNEY_RESULTS).append(") VALUES ");
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

  /** Chunked HTTP INSERT into {@link SparkConstants.ClickHouse#TABLE_FUNNEL_SESSION_STATE}. */
  public void insertFunnelSessionState(List<String> valueTuples, int chunkSize) {
    bulkInsert(SparkConstants.ClickHouse.TABLE_FUNNEL_SESSION_STATE,
        SparkConstants.ClickHouse.INSERT_COLUMNS_FUNNEL_SESSION_STATE, valueTuples, chunkSize);
  }

  /** Chunked HTTP INSERT into {@link SparkConstants.ClickHouse#TABLE_FUNNEL_USER_STATE}. */
  public void insertFunnelUserState(List<String> valueTuples, int chunkSize) {
    bulkInsert(SparkConstants.ClickHouse.TABLE_FUNNEL_USER_STATE,
        SparkConstants.ClickHouse.INSERT_COLUMNS_FUNNEL_USER_STATE, valueTuples, chunkSize);
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
