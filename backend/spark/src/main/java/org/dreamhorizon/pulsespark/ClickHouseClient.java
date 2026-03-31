package org.dreamhorizon.pulsespark;

import org.dreamhorizon.pulsespark.model.FunnelResult;
import org.dreamhorizon.pulsespark.model.JourneyTransition;

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

    public void deleteFunnelResults(long funnelId, String runTime) {
        execute("deleteFunnelResults", String.format(
                "ALTER TABLE %s.funnel_results DELETE WHERE funnel_id = '%d' AND run_time = '%s'",
                db, funnelId, runTime
        ));
        log.info("Deleted funnel_results for funnel_id={} run_time={}", funnelId, runTime);
    }

    public void ping() {
        execute("ping", "SELECT 1");
    }

    public void insertFunnelResults(List<FunnelResult> rows) {
        if (rows.isEmpty()) {
            log.warn("Skipping funnel_results insert because computed row set is empty");
            return;
        }

        var sb = new StringBuilder()
                .append("INSERT INTO ").append(db).append(".funnel_results ")
                .append("(funnel_id,project_id,run_time,step_index,step_name,user_count,conversion_pct) VALUES ");

        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            if (i > 0) sb.append(',');
            sb.append(String.format("('%d','%s','%s',%d,'%s',%d,%.4f)",
                    r.funnelId(), esc(r.projectId()), r.runTime(),
                    r.stepIndex(), esc(r.stepName()), r.userCount(), r.conversionPct()
            ));
        }
        execute("insertFunnelResults", sb.toString());
        log.info("Inserted {} funnel_result rows", rows.size());
    }

    public void deleteJourneyResults(long journeyId, String runTime) {
        execute("deleteJourneyResults", String.format(
                "ALTER TABLE %s.journey_results DELETE WHERE journey_id = '%d' AND run_time = '%s'",
                db, journeyId, runTime
        ));
        log.info("Deleted journey_results for journey_id={} run_time={}", journeyId, runTime);
    }

    public void insertJourneyResults(List<JourneyTransition> rows) {
        if (rows.isEmpty()) {
            log.warn("Skipping journey_results insert because computed row set is empty");
            return;
        }

        var sb = new StringBuilder()
                .append("INSERT INTO ").append(db).append(".journey_results ")
                .append("(journey_id,project_id,run_time,direction,pos_from,event_from,pos_to,event_to,user_count) VALUES ");

        for (int i = 0; i < rows.size(); i++) {
            var r = rows.get(i);
            if (i > 0) sb.append(',');
            sb.append(String.format("('%d','%s','%s','%s',%d,'%s',%d,'%s',%d)",
                    r.journeyId(), esc(r.projectId()), r.runTime(),
                    esc(r.direction()), r.posFrom(), esc(r.eventFrom()),
                    r.posTo(), esc(r.eventTo()), r.userCount()
            ));
        }
        execute("insertJourneyResults", sb.toString());
        log.info("Inserted {} journey_result rows", rows.size());
    }

    public void bulkInsert(String table, String columnList, List<String> valueRows, int chunkSize) {
        if (valueRows.isEmpty()) {
            log.warn("Skipping bulk insert into {}.{} because valueRows is empty", db, table);
            return;
        }
        for (int offset = 0; offset < valueRows.size(); offset += chunkSize) {
            var batch = valueRows.subList(offset, Math.min(offset + chunkSize, valueRows.size()));
            var sql = "INSERT INTO " + db + "." + table + " (" + columnList + ") VALUES "
                    + String.join(",", batch);
            execute("bulkInsert:" + table, sql);
            log.info("Bulk-inserted {} rows into {}.{} (offset {})", batch.size(), db, table, offset);
        }
    }

    private void execute(String operation, String sql) {
        log.info("Executing ClickHouse operation={} sqlBytes={}", operation, sql.length());
        var request = HttpRequest.newBuilder()
                .uri(baseUri)
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(sql, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(120))
                .build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            var body = response.body() == null ? "" : response.body();
            var bodyPreview = body.substring(0, Math.min(500, body.length()));
            if (response.statusCode() != 200) {
                log.error("ClickHouse operation failed operation={} status={} body={}",
                        operation, response.statusCode(), bodyPreview);
                throw new RuntimeException("ClickHouse [%d]: %s".formatted(response.statusCode(), bodyPreview));
            }
            if (!body.isBlank()) {
                log.info("ClickHouse operation response operation={} status={} body={}",
                        operation, response.statusCode(), bodyPreview);
            } else {
                log.info("ClickHouse operation succeeded operation={} status={}",
                        operation, response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("ClickHouse request exception operation={} message={}", operation, e.getMessage(), e);
            throw new RuntimeException("ClickHouse request failed: " + e.getMessage(), e);
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
