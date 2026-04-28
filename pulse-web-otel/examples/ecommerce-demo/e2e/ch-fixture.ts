/**
 * ClickHouse fixture for M4 CH integration tests.
 *
 * Queries otel.otel_traces and otel.otel_logs via the CH HTTP interface
 * at localhost:8123. Polls until the expected row appears (spans take
 * ~batch_delay + ~2s collector→CH ingest).
 *
 * Requires full stack running: deploy/scripts/start.sh
 *
 * CH creds from deploy/.env:
 *   OTEL_CLICKHOUSE_USER=pulse_user
 *   OTEL_CLICKHOUSE_PASSWORD=pulse_password
 */

// ─── Connection ───────────────────────────────────────────────────────────────

const CH_HOST = process.env["CH_HOST"] ?? "http://localhost:8123";
const CH_USER = process.env["CH_USER"] ?? "pulse_user";
const CH_PASS = process.env["CH_PASS"] ?? "pulse_password";
const CH_DB   = process.env["CH_DB"]   ?? "otel";

// Service name the E2E app registers with
export const SERVICE_NAME = "ecommerce-demo";

// ─── Types ────────────────────────────────────────────────────────────────────

export interface ChSpanRow {
  SpanName: string;
  ServiceName: string;
  span_ts: string;         // toString(Timestamp) — renamed to avoid ORDER BY alias collision
  span_duration: string;   // toString(Duration)  — renamed for same reason
  PulseType: string;
  screen_name: string;
  url_path: string;
  navigation_type: string;
  start_type: string;
  load_duration_ms: string;
  ttfb_ms: string;
  tti: string;
  session_duration: string;
  previous_screen_name: string;
}

export interface ChLogRow {
  log_ts: string;
  PulseType: string;
  Body: string;
  screen_name: string;
}

// ─── Raw HTTP query ───────────────────────────────────────────────────────────

export async function chQuery<T = Record<string, string>>(
  sql: string,
): Promise<T[]> {
  const url = new URL(CH_HOST);
  url.searchParams.set("query", sql);
  url.searchParams.set("default_format", "JSONEachRow");
  url.searchParams.set("user", CH_USER);
  url.searchParams.set("password", CH_PASS);

  const res = await fetch(url.toString(), { method: "GET" });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`CH query failed (${res.status}): ${body}`);
  }
  const text = await res.text();
  if (!text.trim()) return [];
  // JSONEachRow = one JSON object per line
  return text
    .trim()
    .split("\n")
    .map((line) => JSON.parse(line) as T);
}

/** Check CH is reachable. Returns false if the stack is not running. */
export async function isCHAvailable(): Promise<boolean> {
  try {
    await chQuery("SELECT 1");
    return true;
  } catch {
    return false;
  }
}

// ─── Poll helpers ─────────────────────────────────────────────────────────────

/**
 * Poll until CH returns at least one row, or throw on timeout.
 * Each poll re-issues the full query — CH is eventually consistent after
 * the OTEL collector flushes (~batch_delay + ~2s ingest latency).
 */
export async function pollUntilCH<T>(
  querySql: string,
  timeoutMs = 20_000,
  description = "CH row",
): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const rows = await chQuery<T>(querySql);
    if (rows.length > 0) return rows[0]!;
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`CH timeout (${timeoutMs}ms): no ${description} found`);
}

// ─── Span query builders ──────────────────────────────────────────────────────

/** Base WHERE for this test run — last 120 seconds, our service only. */
function baseWhere(extraSeconds = 120): string {
  return `
    ServiceName = '${SERVICE_NAME}'
    AND Timestamp > now() - INTERVAL ${extraSeconds} SECOND
  `;
}

/**
 * Wait for a span with the given SpanName to appear in otel_traces.
 * Returns the first matching row.
 */
export function waitForCHSpan(
  spanName: string,
  extraWhere = "",
  timeoutMs = 20_000,
): Promise<ChSpanRow> {
  const sql = `
    SELECT
      SpanName,
      ServiceName,
      toString(Timestamp) AS span_ts,
      toString(Duration)  AS span_duration,
      PulseType,
      SpanAttributes['screen.name']          AS screen_name,
      SpanAttributes['url.path']             AS url_path,
      SpanAttributes['navigation.type']      AS navigation_type,
      SpanAttributes['start.type']           AS start_type,
      SpanAttributes['load.duration_ms']     AS load_duration_ms,
      SpanAttributes['ttfb_ms']              AS ttfb_ms,
      SpanAttributes['tti']                  AS tti,
      SpanAttributes['session.duration']     AS session_duration,
      SpanAttributes['previous_screen.name'] AS previous_screen_name
    FROM ${CH_DB}.otel_traces
    WHERE ${baseWhere()}
      AND SpanName = '${spanName}'
      ${extraWhere ? `AND ${extraWhere}` : ""}
    ORDER BY Timestamp DESC
    LIMIT 1
    FORMAT JSONEachRow
  `;
  return pollUntilCH<ChSpanRow>(sql, timeoutMs, `span(${spanName})`);
}

/**
 * Wait for a log with the given PulseType to appear in otel_logs.
 */
export function waitForCHLog(
  pulseType: string,
  extraWhere = "",
  timeoutMs = 20_000,
): Promise<ChLogRow> {
  const sql = `
    SELECT
      toString(Timestamp)               AS log_ts,
      PulseType,
      Body,
      LogAttributes['screen.name']      AS screen_name
    FROM ${CH_DB}.otel_logs
    WHERE ${baseWhere()}
      AND PulseType = '${pulseType}'
      ${extraWhere ? `AND ${extraWhere}` : ""}
    ORDER BY Timestamp DESC
    LIMIT 1
    FORMAT JSONEachRow
  `;
  return pollUntilCH<ChLogRow>(sql, timeoutMs, `log(${pulseType})`);
}

/**
 * Count spans matching criteria. Used for negative assertions.
 */
export async function countCHSpans(
  spanName: string,
  extraWhere = "",
  windowSeconds = 30,
): Promise<number> {
  const sql = `
    SELECT count() AS cnt
    FROM ${CH_DB}.otel_traces
    WHERE ServiceName = '${SERVICE_NAME}'
      AND Timestamp > now() - INTERVAL ${windowSeconds} SECOND
      AND SpanName = '${spanName}'
      ${extraWhere ? `AND ${extraWhere}` : ""}
    FORMAT JSONEachRow
  `;
  const rows = await chQuery<{ cnt: string }>(sql);
  return Number(rows[0]?.cnt ?? 0);
}

/**
 * Count logs matching criteria. Used for negative assertions.
 */
export async function countCHLogs(
  pulseType: string,
  extraWhere = "",
  windowSeconds = 30,
): Promise<number> {
  const sql = `
    SELECT count() AS cnt
    FROM ${CH_DB}.otel_logs
    WHERE ServiceName = '${SERVICE_NAME}'
      AND Timestamp > now() - INTERVAL ${windowSeconds} SECOND
      AND PulseType = '${pulseType}'
      ${extraWhere ? `AND ${extraWhere}` : ""}
    FORMAT JSONEachRow
  `;
  const rows = await chQuery<{ cnt: string }>(sql);
  return Number(rows[0]?.cnt ?? 0);
}
