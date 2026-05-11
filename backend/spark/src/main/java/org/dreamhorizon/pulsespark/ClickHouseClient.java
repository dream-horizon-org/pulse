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
import java.util.Locale;
import org.dreamhorizon.pulsespark.model.FunnelResult;
import org.dreamhorizon.pulsespark.model.JourneyTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClickHouseClient {

  private static final Logger log = LoggerFactory.getLogger(ClickHouseClient.class);

  private final HttpClient http;
  private final URI baseUri;
  private final String db;

  public ClickHouseClient(String host, int port, String db, String user, String password) {
    this.db = db;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    this.baseUri = URI.create(String.format(
        "http://%s:%d/?database=%s&user=%s&password=%s",
        host, port,
        URLEncoder.encode(db, StandardCharsets.UTF_8),
        URLEncoder.encode(user, StandardCharsets.UTF_8),
        URLEncoder.encode(password, StandardCharsets.UTF_8)
    ));
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
        .append("(FunnelId,ProjectId,RunTime,StepIndex,StepName,UserCount,ConversionPct,MedianStepSeconds,")
        .append("OrderCount,Revenue,AvgOrderValue,LostRevenue) VALUES ");
    for (int i = 0; i < rows.size(); i++) {
      var r = rows.get(i);
      if (i > 0) {
        sb.append(',');
      }
      String medianVal = r.medianStepSeconds() == null ? "NULL" : String.valueOf(r.medianStepSeconds());
      String orderCountVal = r.orderCount() == null ? "NULL" : String.valueOf(r.orderCount());
      String revenueVal = formatDecimal(r.revenue());
      String aovVal = formatDecimal(r.avgOrderValue());
      String lostVal = formatDecimal(r.lostRevenue());
      sb.append(String.format(Locale.ROOT,
          "('%d','%s','%s',%d,'%s',%d,%.4f,%s,%s,%s,%s,%s)",
          r.funnelId(), esc(r.projectId()), esc(r.runTime()),
          r.stepIndex(), esc(r.stepName()), r.userCount(), r.conversionPct(),
          medianVal, orderCountVal, revenueVal, aovVal, lostVal
      ));
    }
    execute("insertFunnelResults", sb.toString());
    log.info("Inserted {} funnel_result rows", rows.size());
  }

  private static String formatDecimal(Double v) {
    if (v == null || Double.isNaN(v) || Double.isInfinite(v)) {
      return "NULL";
    }
    return String.format(Locale.ROOT, "%.4f", v);
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
