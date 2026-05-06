/**
 * ClickHouse fixture for nextjs-demo CH integration tests.
 *
 * Requires full stack running: deploy/scripts/start.sh
 *
 * Queries otel.otel_logs via the CH HTTP interface at localhost:8123.
 *
 * NOTE: device.crash / non_fatal are routed by the collector to stack_trace_events.
 *       session.start / custom events go to otel_logs.
 *
 * Run: yarn workspace nextjs-demo e2e:ch
 */

const CH_HOST = process.env["CH_HOST"] ?? "http://localhost:8123";
const CH_USER = process.env["CH_USER"] ?? "pulse_user";
const CH_PASS = process.env["CH_PASS"] ?? "pulse_password";
const CH_DB   = process.env["CH_DB"]   ?? "otel";

export const SERVICE_NAME = "nextjs-demo";

export interface ChLogRow {
  log_ts: string;
  PulseType: string;
  Body: string;
  screen_name: string;
  session_id: string;
}

export interface ChStackTraceRow {
  log_ts: string;
  PulseType: string;
  ExceptionMessage: string;
  ExceptionType: string;
}

async function chQuery<T>(sql: string): Promise<T[]> {
  const url = `${CH_HOST}/?user=${CH_USER}&password=${CH_PASS}&database=${CH_DB}`;
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "text/plain" },
    body: sql + " FORMAT JSON",
  });
  if (!res.ok) throw new Error(`CH query failed: ${res.status} ${await res.text()}`);
  const json = (await res.json()) as { data: T[] };
  return json.data;
}

async function pollUntil<T>(
  fn: () => Promise<T | undefined>,
  timeoutMs: number,
  description: string,
): Promise<T> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const result = await fn();
    if (result !== undefined) return result;
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`CH timeout (${timeoutMs}ms): ${description}`);
}

export async function waitForChLog(
  pulseType: string,
  serviceName = SERVICE_NAME,
  timeoutMs = 20_000,
): Promise<ChLogRow> {
  return pollUntil(
    async () => {
      const rows = await chQuery<ChLogRow>(
        `SELECT toUnixTimestamp(Timestamp) AS log_ts,
                LogAttributes['pulse.type'] AS PulseType,
                Body,
                LogAttributes['screen.name'] AS screen_name,
                LogAttributes['session.id'] AS session_id
         FROM otel_logs
         WHERE ServiceName = '${serviceName}'
           AND LogAttributes['pulse.type'] = '${pulseType}'
           AND Timestamp > now() - INTERVAL 5 MINUTE
         ORDER BY Timestamp DESC
         LIMIT 1`,
      );
      return rows[0];
    },
    timeoutMs,
    `CH otel_logs pulse.type=${pulseType} serviceName=${serviceName}`,
  );
}

export async function waitForChStackTrace(
  pulseType: "device.crash" | "non_fatal",
  serviceName = SERVICE_NAME,
  timeoutMs = 20_000,
): Promise<ChStackTraceRow> {
  return pollUntil(
    async () => {
      const rows = await chQuery<ChStackTraceRow>(
        `SELECT toUnixTimestamp(Timestamp) AS log_ts,
                LogAttributes['pulse.type'] AS PulseType,
                LogAttributes['exception.message'] AS ExceptionMessage,
                LogAttributes['exception.type'] AS ExceptionType
         FROM otel_logs
         WHERE ServiceName = '${serviceName}'
           AND LogAttributes['pulse.type'] = '${pulseType}'
           AND Timestamp > now() - INTERVAL 5 MINUTE
         ORDER BY Timestamp DESC
         LIMIT 1`,
      );
      return rows[0];
    },
    timeoutMs,
    `CH otel_logs pulse.type=${pulseType} serviceName=${serviceName}`,
  );
}
