package org.dreamhorizon.pulsespark;

import org.dreamhorizon.pulsespark.model.FunnelResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class ClickHouseClient {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseClient.class);

    private final HttpClient http;
    private final URI baseUri;
    private final String db;

    public ClickHouseClient(String host, int port, String db, String user, String password) {
        this.db   = db;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.baseUri = URI.create(String.format(
                "http://%s:%d/?database=%s&user=%s&password=%s",
                host, port,
                URLEncoder.encode(db,       StandardCharsets.UTF_8),
                URLEncoder.encode(user,     StandardCharsets.UTF_8),
                URLEncoder.encode(password, StandardCharsets.UTF_8)
        ));
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void deleteFunnelResults(String funnelId, String runDate) {
        execute(String.format(
                "ALTER TABLE %s.funnel_results DELETE WHERE funnel_id = '%s' AND run_date = '%s'",
                db, esc(funnelId), runDate
        ));
        log.info("Deleted funnel_results for funnel_id={} run_date={}", funnelId, runDate);
    }

    public void insertFunnelResults(List<FunnelResult> rows) {
        if (rows.isEmpty()) return;

        var sb = new StringBuilder()
                .append("INSERT INTO ").append(db).append(".funnel_results ")
                .append("(funnel_id,project_id,run_date,step_index,step_name,user_count,conversion_pct) VALUES ");

        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            if (i > 0) sb.append(',');
            sb.append(String.format("('%s','%s','%s',%d,'%s',%d,%.4f)",
                    esc(r.funnelId()), esc(r.projectId()), r.runDate(),
                    r.stepIndex(), esc(r.stepName()), r.userCount(), r.conversionPct()
            ));
        }
        execute(sb.toString());
        log.info("Inserted {} funnel_result rows", rows.size());
    }

    /**
     * Bulk-insert arbitrary rows into an AggregatingMergeTree table.
     * {@code valueRows} must contain pre-formatted SQL value tuples, e.g. {@code "('a','b',42)"}.
     */
    public void bulkInsert(String table, String columnList, List<String> valueRows, int chunkSize) {
        for (int offset = 0; offset < valueRows.size(); offset += chunkSize) {
            var batch = valueRows.subList(offset, Math.min(offset + chunkSize, valueRows.size()));
            var sql = "INSERT INTO " + db + "." + table + " (" + columnList + ") VALUES "
                    + String.join(",", batch);
            execute(sql);
            log.info("Bulk-inserted {} rows into {}.{} (offset {})", batch.size(), db, table, offset);
        }
    }

    public void sendCallback(String callbackUrl, String funnelId, String status,
                             String runDate, String errorMessage) {
        var body = errorMessage != null
                ? """
                  {"funnel_id":"%s","status":"%s","run_date":"%s","error_message":"%s"}
                  """.formatted(funnelId, status, runDate, errorMessage.replace("\"", "\\\"")).trim()
                : """
                  {"funnel_id":"%s","status":"%s","run_date":"%s"}
                  """.formatted(funnelId, status, runDate).trim();

        var request = HttpRequest.newBuilder()
                .uri(URI.create(callbackUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(10))
                .build();
        try {
            http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (IOException | InterruptedException e) {
            log.warn("Callback POST failed for funnel {}: {}", funnelId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private void execute(String sql) {
        var request = HttpRequest.newBuilder()
                .uri(baseUri)
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(sql, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(120))
                .build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                var preview = response.body().substring(0, Math.min(500, response.body().length()));
                throw new RuntimeException("ClickHouse [%d]: %s".formatted(response.statusCode(), preview));
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("ClickHouse request failed: " + e.getMessage(), e);
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
