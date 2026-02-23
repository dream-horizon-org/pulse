/**
 * Mock Responses for Real-time Querying Feature
 *
 * Includes:
 * - Table metadata (columns, types)
 * - Query submission responses
 * - Job status responses
 * - Query results
 */


// Table metadata response
export const mockTableMetadata = {
  databaseName: "pulse_athena_db",
  tableName: "otel_data",
  columns: [
    { name: "event_name", type: "varchar" },
    { name: "android_os_api_level", type: "varchar" },
    { name: "os_version", type: "varchar" },
    { name: "app_build_id", type: "varchar" },
    { name: "app_build_name", type: "varchar" },
    { name: "device_manufacturer", type: "varchar" },
    { name: "device_model_identifier", type: "varchar" },
    { name: "os_name", type: "varchar" },
    { name: "service_name", type: "varchar" },
    { name: "session_id", type: "varchar" },
    { name: "screen_name", type: "varchar" },
    { name: "network_carrier_mcc", type: "varchar" },
    { name: "network_carrier_mnc", type: "varchar" },
    { name: "network_carrier_icc", type: "varchar" },
    { name: "pulse_app_state", type: "varchar" },
    { name: "span_id", type: "varchar" },
    { name: "timestamp", type: "timestamp" },
    { name: "trace_id", type: "varchar" },
    { name: "scope_name", type: "varchar" },
    { name: "flags", type: "bigint" },
    { name: "observed_timestamp", type: "timestamp" },
    { name: "props", type: "json" },
  ],
};

// Generate random event names
const eventNames = [
  "app_open",
  "screen_view",
  "button_click",
  "purchase_complete",
  "add_to_cart",
  "search",
  "sign_in",
  "sign_out",
  "notification_received",
  "share",
  "scroll",
  "video_play",
  "video_pause",
  "form_submit",
  "error_occurred",
];

// Generate random platforms
const platforms = ["android", "ios", "web"];

// Generate random device manufacturers
const manufacturers = [
  "Samsung",
  "Google",
  "Apple",
  "OnePlus",
  "Xiaomi",
  "Huawei",
];

// Generate random device models
const deviceModels = [
  "SM-G998B",
  "Pixel 7",
  "iPhone 14",
  "OP9 Pro",
  "Mi 12",
  "P50",
];

// Generate random screen names
const screenNames = [
  "HomeScreen",
  "ProductDetail",
  "Cart",
  "Checkout",
  "Profile",
  "Settings",
  "Search",
  "Login",
];

// Generate a random timestamp within the last 24 hours
function randomTimestamp(): string {
  const now = new Date();
  const hoursAgo = Math.floor(Math.random() * 24);
  const minutesAgo = Math.floor(Math.random() * 60);
  now.setHours(now.getHours() - hoursAgo);
  now.setMinutes(now.getMinutes() - minutesAgo);
  return now.toISOString();
}

// Generate mock props JSON
function generateMockProps(): Record<string, unknown> {
  return {
    "android.os.api_level": String(Math.floor(Math.random() * 10) + 28),
    "app.build_id": String(Math.floor(Math.random() * 100) + 1),
    "app.build_name": `v${Math.floor(Math.random() * 5) + 1}.${Math.floor(Math.random() * 10)}.${Math.floor(Math.random() * 20)}`,
    "device.battery_level": Math.floor(Math.random() * 100),
    "device.is_charging": Math.random() > 0.5,
    "network.type": ["wifi", "cellular", "ethernet"][
      Math.floor(Math.random() * 3)
    ],
    "user.country": ["US", "IN", "UK", "DE", "FR", "JP"][
      Math.floor(Math.random() * 6)
    ],
  };
}

// Generate a single mock row
function generateMockRow(): Record<string, unknown> {
  return {
    event_name: eventNames[Math.floor(Math.random() * eventNames.length)],
    android_os_api_level: String(Math.floor(Math.random() * 10) + 28),
    os_version: `${Math.floor(Math.random() * 5) + 10}.${Math.floor(Math.random() * 5)}.${Math.floor(Math.random() * 5)}`,
    app_build_id: String(Math.floor(Math.random() * 100) + 1),
    app_build_name: `v${Math.floor(Math.random() * 5) + 1}.${Math.floor(Math.random() * 10)}.${Math.floor(Math.random() * 20)}`,
    device_manufacturer:
      manufacturers[Math.floor(Math.random() * manufacturers.length)],
    device_model_identifier:
      deviceModels[Math.floor(Math.random() * deviceModels.length)],
    os_name: platforms[Math.floor(Math.random() * platforms.length)],
    service_name: "pulse-mobile-sdk",
    session_id: `sess_${Math.random().toString(36).substr(2, 16)}`,
    screen_name: screenNames[Math.floor(Math.random() * screenNames.length)],
    network_carrier_mcc: String(Math.floor(Math.random() * 999) + 1),
    network_carrier_mnc: String(Math.floor(Math.random() * 99) + 1),
    network_carrier_icc: String(Math.floor(Math.random() * 999) + 1),
    pulse_app_state: ["foreground", "background"][
      Math.floor(Math.random() * 2)
    ],
    span_id: Math.random().toString(16).substr(2, 16),
    timestamp: randomTimestamp(),
    trace_id: Math.random().toString(16).substr(2, 32),
    scope_name: "pulse.events",
    flags: Math.floor(Math.random() * 256),
    observed_timestamp: randomTimestamp(),
    props: JSON.stringify(generateMockProps()),
  };
}

/**
 * Generate mock query results based on the SQL query
 */
export function generateMockQueryResults(sql: string): {
  columns: { name: string; type: string }[];
  rows: Record<string, unknown>[];
  totalRows: number;
  executionTimeMs: number;
  dataScannedInBytes: number;
  hasMore: boolean;
  nextToken: string | null;
} {
  const sqlUpper = sql.toUpperCase();
  const hasAggregation =
    sqlUpper.includes("COUNT(") ||
    sqlUpper.includes("SUM(") ||
    sqlUpper.includes("AVG(") ||
    sqlUpper.includes("MIN(") ||
    sqlUpper.includes("MAX(");
  const hasGroupBy = sqlUpper.includes("GROUP BY");

  // Determine number of rows
  let numRows = 50;
  const limitMatch = sql.match(/LIMIT\s+(\d+)/i);
  if (limitMatch) {
    numRows = Math.min(parseInt(limitMatch[1], 10), 100);
  }

  // Parse columns from SELECT - extract aliases
  const columns: { name: string; type: string }[] = [];
  const rows: Record<string, unknown>[] = [];

  // Parse SELECT clause to get column aliases
  const selectMatch = sql.match(/SELECT\s+([\s\S]+?)\s+FROM/i);
  const selectClause = selectMatch ? selectMatch[1] : "";

  // Extract column aliases from SELECT clause
  const aliases: string[] = [];
  const aliasRegex = /AS\s+"([^"]+)"/gi;
  let aliasMatch;
  while ((aliasMatch = aliasRegex.exec(selectClause)) !== null) {
    aliases.push(aliasMatch[1]);
  }

  if (hasAggregation) {
    // Aggregated query
    const groupByMatch = sql.match(/GROUP BY\s+([^ORDER\nLIMIT]+)/i);
    const groupByCols = groupByMatch
      ? groupByMatch[1]
          .split(",")
          .map((c) =>
            c
              .trim()
              .replace(/"/g, "")
              .replace(/\s+/g, "")
          )
      : [];

    // Add group by columns first
    groupByCols.forEach((col) => {
      // Clean up column name (remove table prefix, json functions, etc.)
      const cleanCol = col
        .replace(/^[^.]+\./, "")
        .replace(/json_extract_scalar\([^)]+\)/i, "json_field");
      columns.push({ name: cleanCol, type: "varchar" });
    });

    // Add aggregation columns from aliases
    aliases.forEach((alias) => {
      if (!columns.find((c) => c.name === alias)) {
        const type = alias.toLowerCase().includes("count")
          ? "bigint"
          : "double";
        columns.push({ name: alias, type });
      }
    });

    // Fallback if no aliases found
    if (columns.length === 0) {
      if (sqlUpper.includes("COUNT(")) {
        columns.push({ name: "count", type: "bigint" });
      }
      if (sqlUpper.includes("SUM(")) {
        columns.push({ name: "sum", type: "double" });
      }
      if (sqlUpper.includes("AVG(")) {
        columns.push({ name: "avg", type: "double" });
      }
    }

    // Determine row count based on GROUP BY
    // Without GROUP BY: aggregation returns 1 row
    // With GROUP BY: returns multiple rows (up to limit)
    const aggRowCount = hasGroupBy ? Math.min(numRows, 15) : 1;

    for (let i = 0; i < aggRowCount; i++) {
      const row: Record<string, unknown> = {};

      // Generate values for group by columns
      groupByCols.forEach((col) => {
        const cleanCol = col
          .replace(/^[^.]+\./, "")
          .replace(/json_extract_scalar\([^)]+\)/i, "json_field");
        if (cleanCol.includes("event_name") || cleanCol === "event_name") {
          row[cleanCol] =
            eventNames[Math.floor(Math.random() * eventNames.length)];
        } else if (cleanCol.includes("screen_name")) {
          row[cleanCol] =
            screenNames[Math.floor(Math.random() * screenNames.length)];
        } else if (
          cleanCol.includes("manufacturer") ||
          cleanCol.includes("device")
        ) {
          row[cleanCol] =
            manufacturers[Math.floor(Math.random() * manufacturers.length)];
        } else if (cleanCol.includes("os_name")) {
          row[cleanCol] =
            platforms[Math.floor(Math.random() * platforms.length)];
        } else {
          row[cleanCol] = `value_${i + 1}`;
        }
      });

      // Generate values for aggregation columns
      columns.forEach((col) => {
        if (!row[col.name]) {
          if (
            col.type === "bigint" ||
            col.name.toLowerCase().includes("count")
          ) {
            row[col.name] = Math.floor(Math.random() * 5000) + 100;
          } else if (col.type === "double") {
            row[col.name] =
              Math.round((Math.random() * 1000 + 10) * 100) / 100;
          }
        }
      });

      rows.push(row);
    }
  } else {
    // Generate raw data results
    // Get all columns from metadata
    mockTableMetadata.columns.forEach((col) => {
      columns.push({ name: col.name, type: col.type });
    });

    // Generate rows
    for (let i = 0; i < numRows; i++) {
      rows.push(generateMockRow());
    }
  }

  return {
    columns,
    rows,
    totalRows: rows.length,
    executionTimeMs: Math.floor(Math.random() * 3000) + 500,
    dataScannedInBytes: Math.floor(Math.random() * 50000000) + 1000000,
    hasMore: rows.length >= numRows,
    nextToken: rows.length >= numRows ? `next_${Date.now()}` : null,
  };
}

// Store for active jobs (simulates async query execution)
// Using a global to survive hot reloads
declare global {
  interface Window {
    __mockQueryJobs?: Map<
      string,
      {
        status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";
        sql: string;
        createdAt: number;
        completesAt: number;
        pollCount: number;
      }
    >;
  }
}

function getJobsMap() {
  if (typeof window !== "undefined") {
    if (!window.__mockQueryJobs) {
      window.__mockQueryJobs = new Map();
    }
    return window.__mockQueryJobs;
  }
  // Fallback for SSR
  return new Map();
}

/**
 * Create a new query job
 */
export function createQueryJob(
  sql: string
): { jobId: string; status: string } {
  const jobId = `query_${Date.now()}_${Math.random().toString(36).substr(2, 8)}`;
  const now = Date.now();

  const activeJobs = getJobsMap();
  activeJobs.set(jobId, {
    status: "QUEUED",
    sql,
    createdAt: now,
    completesAt: now + 8000, // Complete in 8 seconds (after 1-2 polls at 5s interval)
    pollCount: 0,
  });

  console.log(
    "[Mock] Created job:",
    jobId,
    "activeJobs size:",
    activeJobs.size
  );

  return { jobId, status: "QUEUED" };
}

/**
 * Get job status and results
 */
export function getQueryJobStatus(jobId: string): {
  jobId: string;
  status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";
  results?: ReturnType<typeof generateMockQueryResults>;
  error?: string;
} {
  const activeJobs = getJobsMap();
  const job = activeJobs.get(jobId);

  console.log(
    "[Mock] Getting status for job:",
    jobId,
    "found:",
    !!job,
    "activeJobs size:",
    activeJobs.size
  );

  if (!job) {
    // Job not found - could be hot reload. Return completed with mock data
    console.log("[Mock] Job not found, returning mock completed results");
    const mockSql = "SELECT * FROM otel_data LIMIT 100";
    const results = generateMockQueryResults(mockSql);
    return {
      jobId,
      status: "SUCCEEDED",
      results,
    };
  }

  // Increment poll count
  job.pollCount += 1;

  const now = Date.now();

  console.log(
    "[Mock] Job status check - pollCount:",
    job.pollCount,
    "elapsed:",
    now - job.createdAt,
    "completesAt:",
    job.completesAt - job.createdAt
  );

  // Complete after 2 polls or after time elapsed
  if (job.pollCount >= 2 || now >= job.completesAt) {
    job.status = "SUCCEEDED";
    const results = generateMockQueryResults(job.sql);
    console.log(
      "[Mock] Job completed, returning results with",
      results.rows.length,
      "rows"
    );
    return {
      jobId,
      status: "SUCCEEDED",
      results,
    };
  } else if (job.pollCount >= 1) {
    job.status = "RUNNING";
  }

  console.log("[Mock] Job still", job.status);
  return {
    jobId,
    status: job.status,
  };
}

/**
 * Check if query should return immediate results (simple queries)
 */
export function shouldReturnImmediate(sql: string): boolean {
  // For demo, return immediate for simple COUNT queries
  const isSimpleCount =
    sql.toUpperCase().includes("COUNT(*)") &&
    !sql.toUpperCase().includes("GROUP BY");
  return isSimpleCount && Math.random() > 0.5;
}

/**
 * Query History Item type matching GetQueryHistoryResponse interface
 */
export type MockQueryHistoryItem = {
  jobId: string;
  queryString: string;
  queryExecutionId?: string;
  status: "SUBMITTED" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED";
  resultLocation?: string;
  errorMessage?: string;
  dataScannedInBytes?: number;
  createdAt?: number;
  updatedAt?: number;
  completedAt?: number;
};

/**
 * Generate mock query history
 * Matches GetQueryHistoryResponse interface
 */
export function generateMockQueryHistory(): {
  queries: MockQueryHistoryItem[];
  total: number;
  limit: number;
  offset: number;
} {
  const historyQueries = [
    "SELECT * FROM pulse_athena_db.otel_data WHERE timestamp >= TIMESTAMP '2024-01-15 00:00:00' LIMIT 100",
    "SELECT event_name, COUNT(*) as count FROM pulse_athena_db.otel_data GROUP BY event_name ORDER BY count DESC LIMIT 20",
    "SELECT device_manufacturer, COUNT(DISTINCT session_id) as sessions FROM pulse_athena_db.otel_data GROUP BY device_manufacturer",
    "SELECT screen_name, COUNT(*) as views FROM pulse_athena_db.otel_data WHERE event_name = 'screen_view' GROUP BY screen_name",
    "SELECT os_name, COUNT(*) as events FROM pulse_athena_db.otel_data GROUP BY os_name ORDER BY events DESC",
    "SELECT json_extract_scalar(props, '$.network.type') as network, COUNT(*) FROM pulse_athena_db.otel_data GROUP BY 1",
    "SELECT * FROM pulse_athena_db.otel_data WHERE event_name = 'app_open' LIMIT 50",
    "SELECT COUNT(*) as total_events FROM pulse_athena_db.otel_data",
  ];

  const statuses: Array<"COMPLETED" | "FAILED" | "CANCELLED"> = [
    "COMPLETED",
    "COMPLETED",
    "COMPLETED",
    "COMPLETED",
    "FAILED",
    "CANCELLED",
  ];

  const queries: MockQueryHistoryItem[] = historyQueries.map(
    (queryString, index) => {
      const hoursAgo = index * 2 + Math.floor(Math.random() * 3);
      const createdAt = Date.now() - hoursAgo * 60 * 60 * 1000;
      const status = statuses[index % statuses.length];
      const executionTimeMs =
        status === "COMPLETED"
          ? Math.floor(Math.random() * 5000) + 500
          : 0;

      return {
        jobId: "query_" + createdAt + "_" + index,
        queryString,
        queryExecutionId: "athena_exec_" + createdAt + "_" + index,
        status,
        resultLocation:
          status === "COMPLETED"
            ? "s3://pulse-results/queries/" + createdAt + "/"
            : undefined,
        errorMessage:
          status === "FAILED"
            ? "Query execution failed: syntax error near line 1"
            : undefined,
        dataScannedInBytes:
          status === "COMPLETED"
            ? Math.floor(Math.random() * 100000000) + 1000000
            : undefined,
        createdAt,
        updatedAt: createdAt + 1000,
        completedAt:
          status === "COMPLETED" || status === "FAILED"
            ? createdAt + executionTimeMs
            : undefined,
      };
    }
  );

  return {
    queries,
    total: queries.length,
    limit: 50,
    offset: 0,
  };
}

// --------------------------------------------------------------------------
// AI query mock insights — domain-aware, rich KeyPoint objects
// --------------------------------------------------------------------------

/**
 * Per-finding chart configuration (same shape as AiChartConfig)
 */
interface MockChartConfig {
  type: "bar" | "line" | "pie" | "area";
  title: string;
  xAxisLabel?: string;
  yAxisLabel?: string;
  data: {
    labels: string[];
    datasets: { name: string; values: number[] }[];
  };
}

/** Severity level for visual hierarchy */
type MockSeverity = "critical" | "warning" | "healthy" | "info";

/** Prominent metric badge on a finding */
interface MockMetric {
  label: string;
  value: string;
  previousValue?: string;
}

/**
 * A rich key finding with headline, explanation, evidence, and optional chart.
 */
interface MockKeyPoint {
  text: string;
  detail: string;
  evidence: string[];
  chartConfig?: MockChartConfig;
  severity: MockSeverity;
  metric?: MockMetric;
}

/**
 * Generate domain-aware mock insights based on the natural language query.
 * Each key finding is a rich object referencing real Pulse metrics
 * (screens, interactions, crashes, ANR, frozen frames, network, apdex, p50/p95).
 */
function generateMockInsights(
  naturalLanguageQuery: string,
  _sql: string,
  results: { rows: Record<string, unknown>[]; totalRows: number },
  context?: string
): {
  answer: string;
  keyPoints: MockKeyPoint[];
} {
  const nl = naturalLanguageQuery.toLowerCase();
  const rowCount = results.totalRows;

  const contextPrefix = context
    ? "Building on your earlier findings — "
    : "";

  // ── "Why did the conversion drop?" ──
  if (
    nl.includes("why") &&
    (nl.includes("conversion") || nl.includes("drop") || nl.includes("decrease"))
  ) {
    return {
      answer: `${contextPrefix}Conversion dropped 18% this week (3.2% → 2.6%). The primary driver is a 3x spike in checkout errors on Samsung devices running Android 13, which started roughly 14 hours ago after build v4.8.12 was rolled out.`,
      keyPoints: [
        {
          text: "Checkout error rate jumped from 4% to 12% after build v4.8.12",
          severity: "critical",
          metric: { label: "Error Rate", value: "12%", previousValue: "4%" },
          detail: "The checkout completion flow saw a 3x increase in failures starting 14 hours ago, coinciding with the v4.8.12 rollout. The errors are predominantly HTTP 500 responses from the payment gateway, suggesting a backend integration issue introduced in this build.",
          evidence: [
            "Interaction: Checkout Flow — errorRate 12% (was 4% 7-day avg)",
            "Apdex dropped from 0.91 to 0.72 on affected flow",
            "error_occurred events on Checkout: 1,847 in last 14h vs 412 in prior 14h",
            "Affected build: v4.8.12 (deployed 2025-02-17 22:30 UTC)",
            "Error type: payment_gateway_timeout (78% of failures)",
          ],
          chartConfig: {
            type: "line",
            title: "Checkout Error Rate (Last 7 Days)",
            xAxisLabel: "Day",
            yAxisLabel: "Error Rate %",
            data: {
              labels: ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"],
              datasets: [{ name: "Error Rate", values: [4.1, 3.8, 4.2, 4.0, 11.8, 12.1, 12.0] }],
            },
          },
        },
        {
          text: "Samsung SM-G998B accounts for 62% of all checkout failures",
          severity: "critical",
          metric: { label: "Device Share", value: "62%", previousValue: "34%" },
          detail: "A single device model dominates the failure pool. SM-G998B is the most popular device in the user base (34% of sessions), combined with an Android 13-specific rendering issue that causes the payment form to timeout.",
          evidence: [
            "SM-G998B failures: 1,145 / 1,847 total (62%)",
            "SM-G998B session share: 34% of all sessions",
            "OS: Android 13 (API 33) — 98% of SM-G998B failures",
            "crashRate on SM-G998B: 3.8% (vs 1.2% fleet-wide)",
          ],
          chartConfig: {
            type: "pie",
            title: "Checkout Failures by Device",
            data: {
              labels: ["SM-G998B", "Pixel 7", "Mi 12", "iPhone 14", "Other"],
              datasets: [{ name: "Failures", values: [1145, 204, 186, 112, 200] }],
            },
          },
        },
        {
          text: "Cart-to-purchase funnel dropped from 38% to 21% on affected devices",
          severity: "warning",
          metric: { label: "Funnel Rate", value: "21%", previousValue: "38%" },
          detail: "The funnel analysis shows the drop is concentrated at the payment step. Users are reaching the cart screen normally but failing at the checkout-to-payment transition, confirming the issue is in the payment processing path rather than upstream navigation.",
          evidence: [
            "Cart screen views: 4,218 (stable, no change)",
            "Cart → Checkout transition: 82% (stable)",
            "Checkout → Payment completion: 21% (was 38%)",
            "p50 latency on Payment screen: 4,200ms (was 850ms)",
          ],
          // No chart — will show "Graph not available"
        },
        {
          text: "iOS and web conversion remain stable at 3.4% and 2.9%",
          severity: "healthy",
          metric: { label: "iOS Conv.", value: "3.4%" },
          detail: "Non-Android platforms show no degradation, confirming the issue is isolated to the Android build path. This eliminates backend API changes as a root cause and points to client-side Android code.",
          evidence: [
            "iOS conversion: 3.4% (7-day avg 3.3%)",
            "Web conversion: 2.9% (7-day avg 2.8%)",
            "iOS crashRate: 0.8% (unchanged)",
            "Web errorRate: 1.2% (unchanged)",
          ],
          // No chart — will show "Graph not available"
        },
      ],
    };
  }

  // ── "What are the issues in my checkout flow?" ──
  if (
    nl.includes("checkout") ||
    nl.includes("flow") ||
    nl.includes("interaction") ||
    ((nl.includes("top") || nl.includes("problem")) &&
      (nl.includes("problem") || nl.includes("interaction") || nl.includes("issue")))
  ) {
    return {
      answer: `${contextPrefix}After analysing the checkout interaction, payment flow, and checkout screen metrics, we found an increase in poor experience in the checkout flow. The primary signal is a spike in API latency on the /checkout/pay endpoint in the last 1 hour, accompanied by rising frozen frames. Crash rates and page load times remain healthy.`,
      keyPoints: [
        {
          text: "API latency on /checkout/pay increased 5x in the last 1 hour",
          severity: "critical",
          metric: { label: "P95", value: "2,800ms", previousValue: "420ms" },
          detail: "The /checkout/pay endpoint p95 latency jumped from 420ms to 2,800ms starting approximately 1 hour ago. The spike is correlated with a backend deployment at 14:22 UTC. This is causing payment confirmation screens to hang, leading to a poor checkout experience for 34% of users entering the checkout flow in the last hour.",
          evidence: [
            "Endpoint: /checkout/pay — p95 jumped from 420ms to 2,800ms",
            "p50 increased from 180ms to 1,240ms",
            "Error rate on /checkout/pay: 8.2% (was 1.1% before spike)",
            "Timeout errors (>5s): 312 in last 1h vs 8 in prior 1h",
            "Backend deployment at 14:22 UTC correlates with latency spike",
            "Affected users in checkout: 4,368 / 12,847 (34%)",
          ],
          chartConfig: {
            type: "line",
            title: "Checkout API Latency — P95 (Last 6 Hours)",
            xAxisLabel: "Time",
            yAxisLabel: "P95 Latency (ms)",
            data: {
              labels: ["-6h", "-5h", "-4h", "-3h", "-2h", "-1h", "Now"],
              datasets: [{ name: "P95 Latency", values: [410, 430, 415, 425, 440, 2640, 2800] }],
            },
          },
        },
        {
          text: "Frozen frames on checkout screen increased from 3% to 9.2%",
          severity: "warning",
          metric: { label: "FF Rate", value: "9.2%", previousValue: "3.0%" },
          detail: "The frozen frame rate on the checkout screen rose from a stable 3% to 9.2% in the last hour. This correlates with the API latency spike — the UI thread blocks while waiting for the /checkout/pay response, causing visible jank when users scroll or interact with the payment form during the pending state.",
          evidence: [
            "Screen: Checkout — frozenFrameRate 9.2% (was 3.0% avg over last 6h)",
            "Frozen frame instances: 1,847 in last 1h vs 380 in prior 1h",
            "73% of frozen frames occur during payment_gateway_callback",
            "Average frozen frame duration: 420ms (threshold: 700ms for ANR)",
            "Affected devices: all device tiers equally affected (not a device-specific issue)",
          ],
          chartConfig: {
            type: "bar",
            title: "Frozen Frame Rate — Checkout Screen (Hourly)",
            xAxisLabel: "Hour",
            yAxisLabel: "Frozen Frame Rate %",
            data: {
              labels: ["-6h", "-5h", "-4h", "-3h", "-2h", "-1h", "Now"],
              datasets: [{ name: "FF Rate %", values: [3.1, 2.8, 3.2, 2.9, 3.0, 8.4, 9.2] }],
            },
          },
        },
        {
          text: "Page load time remains consistent — no degradation detected",
          severity: "healthy",
          metric: { label: "Load Time", value: "620ms" },
          detail: "The checkout screen load time is stable at 620ms (well within the 1,000ms target). The initial render, layout, and first meaningful paint timings have not changed. This confirms the issue is not in page loading but in post-load API calls during the payment step.",
          evidence: [
            "Screen: Checkout — screenLoadTime 620ms (target: 1,000ms)",
            "First meaningful paint: 480ms (stable, no change)",
            "Time to interactive: 720ms (stable)",
            "6-hour avg load time: 618ms — current: 622ms (within 1% variance)",
          ],
          // No chart — stable metric, shows "Graph not available"
        },
        {
          text: "No crashes or ANR increase found in the system",
          severity: "healthy",
          metric: { label: "Crash Rate", value: "0.4%" },
          detail: "Both crash rate and ANR rate remain within healthy thresholds across the checkout flow. The crash rate is 0.4% (same as the 7-day average) and ANR rate is 0.3%. This confirms the degradation is purely performance-related (latency + frozen frames) and has not yet escalated to stability issues.",
          evidence: [
            "Checkout crashRate: 0.4% (7-day avg: 0.4%) — no increase",
            "Checkout anrRate: 0.3% (7-day avg: 0.3%) — no increase",
            "Total crashes in last 1h: 12 (within normal range of 8-15/h)",
            "Total ANRs in last 1h: 7 (within normal range of 5-10/h)",
            "Non-fatal errors: 42 in last 1h (within normal range)",
          ],
          // No chart — healthy metric, shows "Graph not available"
        },
      ],
    };
  }

  // ── Crash-specific queries ──
  if (nl.includes("crash") || nl.includes("crashes")) {
    return {
      answer: `${contextPrefix}Detected 1,284 crashes in the last 24 hours across all interactions. The Checkout screen is the top contributor (66%), followed by ProductDetail (18%). Crash rate is 3x above the 7-day average, correlating with build v4.8.12.`,
      keyPoints: [
        {
          text: "Checkout screen: 847 crashes (66% of total), crash rate 4.2%",
          severity: "critical",
          metric: { label: "Crash Rate", value: "4.2%", previousValue: "1.4%" },
          detail: "Crashes on the Checkout screen spiked 6 hours ago. The root cause is a NullPointerException in PaymentBridge.processResponse() when the payment gateway returns an unexpected JSON structure. Only build v4.8.12 is affected.",
          evidence: [
            "Screen: Checkout — crashRate 4.2% (7-day avg 1.4%)",
            "Crash count: 847 in last 24h (was 112 in prior 24h)",
            "Top exception: NullPointerException in PaymentBridge.processResponse()",
            "Affected build: v4.8.12 only",
            "Top device: Redmi Note 11 (38%), Samsung Galaxy A53 (22%)",
            "Top OS: Android 13 (72%), Android 12 (28%)",
          ],
          chartConfig: {
            type: "bar",
            title: "Crashes by Screen (Last 24h)",
            xAxisLabel: "Screen",
            yAxisLabel: "Crash Count",
            data: {
              labels: ["Checkout", "ProductDetail", "Search", "Cart", "Login", "Profile"],
              datasets: [{ name: "Crashes", values: [847, 231, 89, 54, 38, 25] }],
            },
          },
        },
        {
          text: "Redmi Note 11 leads crash count with 487 crashes (38%)",
          severity: "warning",
          metric: { label: "Device Crashes", value: "487", previousValue: "~80" },
          detail: "Low-end Xiaomi devices with 2-3GB RAM are disproportionately affected. Memory pressure during payment processing causes OOM crashes in addition to the NullPointerException.",
          evidence: [
            "Redmi Note 11: 487 crashes — crashRate 6.1%",
            "Samsung Galaxy A53: 282 crashes — crashRate 3.4%",
            "Realme 9 Pro: 156 crashes — crashRate 2.8%",
            "iPhone 14: 12 crashes — crashRate 0.2% (stable)",
          ],
          chartConfig: {
            type: "pie",
            title: "Crash Distribution by Device",
            data: {
              labels: ["Redmi Note 11", "Samsung A53", "Realme 9", "Vivo V23", "iPhone 14", "Other"],
              datasets: [{ name: "Crashes", values: [487, 282, 156, 98, 12, 249] }],
            },
          },
        },
        {
          text: "Android 13 accounts for 72% of crashes",
          severity: "warning",
          metric: { label: "OS Share", value: "72%" },
          detail: "The crash concentration on Android 13 suggests a platform-specific API incompatibility. Android 12 devices show the same crash signature but at a lower rate, possibly due to different memory management behavior.",
          evidence: [
            "Android 13: 924 crashes (72%), crashRate 4.8%",
            "Android 12: 312 crashes (24%), crashRate 2.1%",
            "Android 11: 36 crashes (3%), crashRate 0.4%",
            "iOS: 12 crashes (1%), crashRate 0.2%",
          ],
          // No chart
        },
      ],
    };
  }

  // ── ANR-specific queries ──
  if (nl.includes("anr") || nl.includes("not responding")) {
    return {
      answer: `${contextPrefix}Recorded 578 ANR events in the last 24 hours. The Search screen is the primary contributor (59%), predominantly on low-end devices with less than 4GB RAM. The main-thread blocking is caused by synchronous JSON parsing of search suggestions.`,
      keyPoints: [
        {
          text: "Search screen: 342 ANRs (59%), anrRate 4.8%",
          severity: "critical",
          metric: { label: "ANR Rate", value: "4.8%", previousValue: "1.2%" },
          detail: "The search suggestion feature performs synchronous JSON parsing on the main thread. On devices with <4GB RAM, this blocks the UI for 3-5 seconds, triggering Android's ANR detection. The parsing should be moved to a background thread.",
          evidence: [
            "Screen: Search — anrRate 4.8% (fleet avg 1.2%)",
            "ANR count: 342 in 24h",
            "89% of ANRs on devices with <4GB RAM",
            "Main thread block duration: 3.2s avg (ANR threshold: 5s)",
            "frozenFrameRate on Search: 12.4%",
          ],
          chartConfig: {
            type: "bar",
            title: "ANR by Screen",
            xAxisLabel: "Screen",
            yAxisLabel: "ANR Count",
            data: {
              labels: ["Search", "ProductDetail", "Checkout", "Home", "Cart"],
              datasets: [{ name: "ANR", values: [342, 98, 67, 42, 29] }],
            },
          },
        },
        {
          text: "Redmi Note 11 (2GB): 156 ANRs — top affected device",
          severity: "warning",
          metric: { label: "Device ANRs", value: "156" },
          detail: "The 2GB RAM variant of Redmi Note 11 is most affected due to aggressive memory reclamation by the OS, which slows down JSON deserialization. This device represents 12% of the user base but 27% of all ANRs.",
          evidence: [
            "Redmi Note 11 (2GB): 156 ANRs — anrRate 8.2%",
            "Samsung Galaxy A53: 72 ANRs — anrRate 3.1%",
            "Realme 9 Pro: 48 ANRs — anrRate 2.4%",
            "Device share vs ANR share: 12% of users → 27% of ANRs",
          ],
          // No chart
        },
      ],
    };
  }

  // ── Frozen frame / jank / slow queries ──
  if (nl.includes("frozen") || nl.includes("jank") || nl.includes("slow") || nl.includes("frame")) {
    return {
      answer: `${contextPrefix}Frozen frame rate is elevated at 8.4% across the app (target: <5%). The Search and ProductDetail screens are the worst offenders, with frozen frames concentrated during data loading and list rendering.`,
      keyPoints: [
        {
          text: "Search screen: 12.4% frozen frame rate (target <5%)",
          severity: "critical",
          metric: { label: "FF Rate", value: "12.4%", previousValue: "<5%" },
          detail: "The Search screen renders large result lists without virtualization, causing frame drops during rapid scrolling. Combined with synchronous JSON parsing of search suggestions, the UI thread is overloaded on mid-range devices.",
          evidence: [
            "Screen: Search — frozenFrameRate 12.4%",
            "Frozen frames during scroll: 67% of instances",
            "Frozen frames during data load: 33% of instances",
            "avgTimeSpent on Search: 8.2s (users leaving due to poor performance)",
            "p95 screen load time: 2,800ms",
          ],
          chartConfig: {
            type: "bar",
            title: "Frozen Frame Rate by Screen",
            xAxisLabel: "Screen",
            yAxisLabel: "Frozen Frame Rate %",
            data: {
              labels: ["Search", "ProductDetail", "Checkout", "Home", "Cart", "Profile"],
              datasets: [{ name: "FF Rate %", values: [12.4, 9.8, 6.2, 4.1, 3.2, 2.1] }],
            },
          },
        },
        {
          text: "ProductDetail: 9.8% frozen frame rate during image loading",
          severity: "warning",
          metric: { label: "FF Rate", value: "9.8%", previousValue: "<5%" },
          detail: "High-resolution product images are decoded on the main thread. The frozen frames correlate with image carousel swipes, where multiple 4K images are decoded simultaneously.",
          evidence: [
            "Screen: ProductDetail — frozenFrameRate 9.8%",
            "Image decode avg time: 180ms per image on mid-range devices",
            "Carousel swipe frozen frames: 78% of instances",
            "screenLoadTime: 1,840ms (target: 1,000ms)",
            "networkLoadTime for images: 2,400ms on 4G",
          ],
          // No chart
        },
      ],
    };
  }

  // ── Non-fatal error queries ──
  if (nl.includes("non fatal") || nl.includes("non-fatal") || nl.includes("nonfatal")) {
    return {
      answer: `${contextPrefix}Recorded 2,134 non-fatal errors in the last 24 hours. These are predominantly API response parsing errors and deprecated API usage warnings, concentrated on the ProductDetail and Cart screens.`,
      keyPoints: [
        {
          text: "ProductDetail: 892 non-fatals — API response schema mismatch",
          severity: "warning",
          metric: { label: "Non-Fatals", value: "892" },
          detail: "The product API recently started returning a new field format for pricing. The client is catching and swallowing the parsing error, but this causes the price to display as '$0.00' for some products.",
          evidence: [
            "Non-fatal type: JSONParseException on pricing field",
            "Count: 892 in 24h across 456 unique sessions",
            "Affected endpoint: /api/product/details (fields: price, discount)",
            "User impact: price shows as $0.00 for 6% of product views",
          ],
          chartConfig: {
            type: "bar",
            title: "Non-Fatal Errors by Screen",
            xAxisLabel: "Screen",
            yAxisLabel: "Count",
            data: {
              labels: ["ProductDetail", "Cart", "Checkout", "Search", "Profile"],
              datasets: [{ name: "Non-Fatals", values: [892, 567, 312, 198, 165] }],
            },
          },
        },
        {
          text: "Cart: 567 non-fatals — deprecated API usage warnings",
          severity: "info",
          metric: { label: "Non-Fatals", value: "567" },
          detail: "The cart uses a v1 API endpoint that has been deprecated. While still functional, it returns deprecation warnings that the client logs as non-fatal errors. Migration to v2 is recommended.",
          evidence: [
            "Non-fatal type: DeprecatedApiWarning",
            "Count: 567 in 24h",
            "Endpoint: /api/v1/cart (deprecated, use /api/v2/cart)",
            "No user-facing impact currently, but v1 EOL is scheduled",
          ],
          // No chart
        },
      ],
    };
  }

  // ── Deep dive on API latency / network (follow-up from checkout flow) ──
  if (
    nl.includes("latency") ||
    nl.includes("api") ||
    nl.includes("network") ||
    nl.includes("deep dive") ||
    (context && (context.toLowerCase().includes("latency") || context.toLowerCase().includes("api")))
  ) {
    return {
      answer: `${contextPrefix}Deep-diving into the checkout API latency spike — the degradation is concentrated on Jio and Airtel carriers in the Mumbai and Delhi regions, affecting 12,847 users in the last hour. 68% of affected users still completed checkout, but 32% abandoned — representing approximately 1,398 lost transactions.`,
      keyPoints: [
        {
          text: "Mumbai and Delhi regions show 3x latency vs other regions",
          severity: "critical",
          metric: { label: "Mumbai P95", value: "3,200ms", previousValue: "380ms" },
          detail: "The latency spike is geographically concentrated. Mumbai and Delhi share a common CDN edge node (ap-south-1a) that appears to be saturated. Bangalore, Hyderabad, and Chennai are routing through a different edge node (ap-south-1b) and show normal latency. This points to an infrastructure issue at the specific edge location rather than a backend problem.",
          evidence: [
            "Mumbai region — /checkout/pay p95: 3,200ms (was 380ms)",
            "Delhi region — /checkout/pay p95: 2,900ms (was 410ms)",
            "Bangalore region — /checkout/pay p95: 480ms (normal)",
            "Hyderabad region — /checkout/pay p95: 440ms (normal)",
            "Chennai region — /checkout/pay p95: 460ms (normal)",
            "CDN edge: ap-south-1a (Mumbai+Delhi) saturated at 94% capacity",
            "CDN edge: ap-south-1b (BLR+HYD+MAA) at 41% capacity — healthy",
          ],
          chartConfig: {
            type: "bar",
            title: "Checkout API P95 Latency by Region",
            xAxisLabel: "Region",
            yAxisLabel: "P95 Latency (ms)",
            data: {
              labels: ["Mumbai", "Delhi", "Bangalore", "Hyderabad", "Chennai", "Kolkata"],
              datasets: [{ name: "P95 Latency", values: [3200, 2900, 480, 440, 460, 520] }],
            },
          },
        },
        {
          text: "Jio and Airtel carriers showing highest degradation",
          severity: "critical",
          metric: { label: "Jio P95", value: "3,400ms", previousValue: "420ms" },
          detail: "Within the affected Mumbai and Delhi regions, Jio and Airtel users are hit the hardest. Jio users see a p95 of 3,400ms vs WiFi users at 380ms. The degradation is carrier-specific because Jio and Airtel route through a different set of peering points that add extra hops to reach the saturated CDN edge node.",
          evidence: [
            "Jio — /checkout/pay p95: 3,400ms (was 420ms), errorRate: 9.1%",
            "Airtel — /checkout/pay p95: 2,800ms (was 390ms), errorRate: 7.4%",
            "Vi — /checkout/pay p95: 1,200ms (was 400ms), errorRate: 3.2%",
            "WiFi — /checkout/pay p95: 380ms (unchanged), errorRate: 0.4%",
            "Jio timeout errors: 187 in last 1h (82% of Jio errors)",
            "Airtel timeout errors: 134 in last 1h (79% of Airtel errors)",
          ],
          chartConfig: {
            type: "line",
            title: "Checkout API Latency by Carrier (Last 6h)",
            xAxisLabel: "Time",
            yAxisLabel: "P95 Latency (ms)",
            data: {
              labels: ["-6h", "-5h", "-4h", "-3h", "-2h", "-1h", "Now"],
              datasets: [
                { name: "Jio", values: [420, 410, 430, 440, 450, 3100, 3400] },
                { name: "Airtel", values: [390, 400, 385, 410, 420, 2500, 2800] },
                { name: "WiFi", values: [370, 380, 375, 385, 380, 382, 378] },
              ],
            },
          },
        },
        {
          text: "12,847 users analyzed — 34% experienced degraded checkout",
          severity: "info",
          metric: { label: "Affected", value: "34%", previousValue: "~0%" },
          detail: "In the last 1 hour, 12,847 unique users entered the checkout flow. Of these, 4,368 users (34%) experienced latency above the 2-second threshold on the /checkout/pay endpoint. The remaining 8,479 users (66%) — primarily on WiFi or in non-affected regions — had a normal experience.",
          evidence: [
            "Total users entering checkout in last 1h: 12,847",
            "Users with latency >2s: 4,368 (34%)",
            "Users with normal experience: 8,479 (66%)",
            "Total sessions analyzed: 14,291 (some users had multiple sessions)",
            "Unique devices: 11,842",
            "Data source: checkout interaction + network_api events",
          ],
          // No chart — aggregate stats, shows "Graph not available"
        },
        {
          text: "Mid-range Android devices most affected (62% of impacted users)",
          severity: "warning",
          metric: { label: "Mid-range Share", value: "62%" },
          detail: "While the latency issue is server/network-side, mid-range Android devices amplify the poor experience. These devices have less aggressive network retry logic and lower memory for connection pooling, making them more susceptible to timeout errors. Low-end devices are also affected but have a smaller share of checkout traffic.",
          evidence: [
            "Mid-range Android (Redmi Note 11, Samsung A53, Realme 9): 2,708 affected users (62%)",
            "High-end Android (Samsung S23, Pixel 7): 612 affected users (14%)",
            "iOS (iPhone 13, 14, 15): 524 affected users (12%)",
            "Low-end Android (Redmi 9, Samsung M13): 524 affected users (12%)",
            "Redmi Note 11: 1,245 affected (highest single model — 28.5%)",
            "Samsung Galaxy A53: 987 affected (22.6%)",
          ],
          chartConfig: {
            type: "pie",
            title: "Affected Users by Device Tier",
            data: {
              labels: ["Mid-range Android", "High-end Android", "iOS", "Low-end Android"],
              datasets: [{ name: "Affected Users", values: [2708, 612, 524, 524] }],
            },
          },
        },
        {
          text: "68% of affected users still completed checkout despite latency",
          severity: "warning",
          metric: { label: "Revenue Loss", value: "~$65,706" },
          detail: "Despite the degraded experience, 2,970 out of 4,368 affected users (68%) waited and completed their purchase. However, 1,398 users (32%) abandoned the checkout. Based on the average order value of $47, this represents an estimated revenue impact of $65,706 in the last hour alone. Users who completed checkout had an average wait time of 4.2s on the payment step (vs 0.8s normal).",
          evidence: [
            "Affected users who completed checkout: 2,970 / 4,368 (68%)",
            "Affected users who abandoned: 1,398 / 4,368 (32%)",
            "Abandonment rate for non-affected users: 8% (normal)",
            "Avg wait time on payment step (affected): 4.2s (normal: 0.8s)",
            "Estimated revenue loss: 1,398 × $47 avg order = ~$65,706",
            "Most abandonments occurred after 6+ seconds of waiting",
            "Sessions with retry after abandon: 214 (15% came back within 10 min)",
          ],
          chartConfig: {
            type: "bar",
            title: "Checkout Outcome for Affected Users",
            xAxisLabel: "Outcome",
            yAxisLabel: "Users",
            data: {
              labels: ["Completed", "Abandoned", "Retried Later"],
              datasets: [{ name: "Users", values: [2970, 1184, 214] }],
            },
          },
        },
      ],
    };
  }

  // ── Screen / page view / load time queries ──
  if (nl.includes("screen") || nl.includes("page") || nl.includes("load time")) {
    return {
      answer: `${contextPrefix}Analyzed screen health data across ${rowCount} screen_view events. HomeScreen leads in traffic (34%) but ProductDetail has the worst load time at 1,840ms. Checkout screen has the highest crash rate per screen at 4.2%.`,
      keyPoints: [
        {
          text: "ProductDetail: 1,840ms avg load time (target 1,000ms)",
          severity: "critical",
          metric: { label: "Load Time", value: "1,840ms", previousValue: "1,000ms target" },
          detail: "The ProductDetail screen's load time is 84% above target, driven by large image payloads and synchronous API calls. The screen makes 3 sequential API calls before rendering, which should be parallelized.",
          evidence: [
            "Screen: ProductDetail — screenLoadTime 1,840ms",
            "avgTimeSpent: 18.2s, sessions: 3,398",
            "crashes: 231, anrs: 98, frozenFrames: 334 (frozenFrameRate 9.8%)",
            "networkRequests: 12 per screen load, networkLoadTime: 2,400ms",
            "Bounce rate: 28% (users leaving before full load)",
          ],
          chartConfig: {
            type: "bar",
            title: "Screen Load Time",
            xAxisLabel: "Screen",
            yAxisLabel: "Load Time (ms)",
            data: {
              labels: ["ProductDetail", "Checkout", "Search", "HomeScreen", "Cart", "Profile"],
              datasets: [{ name: "Load Time (ms)", values: [1840, 1420, 980, 620, 540, 380] }],
            },
          },
        },
        {
          text: "Checkout: highest crash rate per screen at 4.2%",
          severity: "critical",
          metric: { label: "Crash Rate", value: "4.2%", previousValue: "1.4%" },
          detail: "While HomeScreen has the most traffic, Checkout has the highest crash rate at 4.2%, making it the riskiest screen in the app. Combined with a 12% error rate, users on this screen face significant reliability issues.",
          evidence: [
            "Screen: Checkout — crashRate 4.2%, errorRate 12%",
            "screenLoadTime: 1,420ms, anrs: 67",
            "frozenFrames: 182, frozenFrameRate: 6.2%",
            "DAU on Checkout: 4,218 (12% of total DAU)",
          ],
          // No chart
        },
        {
          text: "HomeScreen: 34% of views, healthy metrics",
          severity: "healthy",
          metric: { label: "Load Time", value: "620ms" },
          detail: "The HomeScreen is the most visited screen and shows healthy performance metrics. Load time is within target and error rates are minimal.",
          evidence: [
            "Screen: HomeScreen — 5,241 views (34% of total)",
            "screenLoadTime: 620ms (target: 1,000ms)",
            "avgTimeSpent: 12.4s, crashRate: 0.4%",
            "frozenFrameRate: 4.1% (within 5% target)",
          ],
          // No chart
        },
      ],
    };
  }

  // ── "Did user perform X event?" ──
  if (nl.includes("did") && (nl.includes("user") || nl.includes("perform"))) {
    const found = rowCount > 0;
    if (found) {
      return {
        answer: `${contextPrefix}Yes. Found ${rowCount} matching event(s) in the last 24 hours.`,
        keyPoints: [
          {
            text: `Event occurred ${rowCount} time(s) in the last 24 hours`,
            severity: "info",
            metric: { label: "Occurrences", value: String(rowCount) },
            detail: `The queried event was found ${rowCount} time(s). The most recent occurrence was on the Checkout screen during an active session. The user's device and network conditions at the time of the event are captured below.`,
            evidence: [
              `Event count: ${rowCount} between ${new Date(Date.now() - 20 * 3600000).toLocaleTimeString()} and ${new Date().toLocaleTimeString()} today`,
              "Last occurrence: Checkout screen, session sess_a8f2...k91x",
              "Device: Samsung SM-G998B, Android 13, build v4.8.12",
              "Network: cellular (Jio), signal strength: good",
              "Session duration at event time: 4m 32s",
            ],
            // No chart for single-user queries
          },
        ],
      };
    }
    return {
      answer: `${contextPrefix}No matching events found in the last 24 hours across ${Math.floor(Math.random() * 500 + 800).toLocaleString()} records searched.`,
      keyPoints: [
          {
            text: "No matching events found for the specified criteria",
            severity: "info",
            detail: "The search covered all available data partitions for the last 24 hours but found no events matching your query. This could indicate the user was not active, the event name is incorrect, or the time range needs expanding.",
            evidence: [
              "Records searched: " + Math.floor(Math.random() * 500 + 800).toLocaleString(),
              "Time range: last 24 hours (24 hourly partitions)",
              "Data source: pulse_athena_db.otel_data",
              "Suggestion: try expanding time range or verifying event name",
            ],
            // No chart
          },
      ],
    };
  }

  // ── Error-related queries ──
  if (nl.includes("error")) {
    return {
      answer: `${contextPrefix}Found ${rowCount} error_occurred events in the last 24 hours. Errors concentrate on Checkout (48%) and ProductDetail (26%) screens, predominantly affecting Samsung and Xiaomi devices running Android 13.`,
      keyPoints: [
        {
          text: "Checkout screen: 48% of all errors (payment gateway timeouts)",
          severity: "critical",
          metric: { label: "Error Share", value: "48%" },
          detail: "The Checkout screen error spike started 14 hours ago with build v4.8.12. The payment gateway is returning HTTP 500 errors for 12% of payment attempts, causing the checkout flow to fail silently and display a generic error.",
          evidence: [
            `Total error_occurred events: ${rowCount}`,
            "Checkout screen errors: " + Math.round(rowCount * 0.48) + " (48%)",
            "Error type: payment_gateway_timeout (78% of Checkout errors)",
            "Affected sessions: " + Math.min(rowCount, 142) + " unique sessions",
            "Error spike start: ~14h ago, correlating with build v4.8.12",
          ],
          chartConfig: {
            type: "bar",
            title: "Errors by Screen (Last 24h)",
            xAxisLabel: "Screen",
            yAxisLabel: "Error Count",
            data: {
              labels: ["Checkout", "ProductDetail", "Search", "Cart", "Profile"],
              datasets: [{ name: "Errors", values: [882, 498, 237, 134, 61] }],
            },
          },
        },
        {
          text: "Samsung SM-G998B: 31% of all errors — most affected device",
          severity: "warning",
          metric: { label: "Device Share", value: "31%" },
          detail: "The SM-G998B's Android 13 WebView has a known issue with HTTP/2 connection pooling that causes premature connection resets. This exacerbates the payment gateway timeout issue on the Checkout screen.",
          evidence: [
            "SM-G998B error count: " + Math.round(rowCount * 0.31) + " (31%)",
            "Redmi Note 11 errors: " + Math.round(rowCount * 0.18) + " (18%)",
            "Pixel 7 errors: " + Math.round(rowCount * 0.08) + " (8%)",
            "iPhone 14 errors: " + Math.round(rowCount * 0.03) + " (3%)",
          ],
          chartConfig: {
            type: "pie",
            title: "Error Distribution by Device",
            data: {
              labels: ["SM-G998B", "Redmi Note 11", "Galaxy A53", "Pixel 7", "iPhone 14", "Other"],
              datasets: [{ name: "Errors", values: [31, 18, 14, 8, 3, 26] }],
            },
          },
        },
      ],
    };
  }

  // ── Default / general queries ──
  return {
    answer: `${contextPrefix}Analyzed ${rowCount} records from the last 24 hours. The app shows elevated crash rates on the Checkout flow and ANR spikes on the Search screen. Overall apdex is 0.82 (target: 0.90).`,
    keyPoints: [
      {
        text: `${rowCount} events across ${Math.floor(rowCount * 0.6)} unique sessions analyzed`,
        severity: "info",
        metric: { label: "Apdex", value: "0.82", previousValue: "0.90 target" },
        detail: "The overall event volume is within normal daily patterns. The 10:00-14:00 UTC window accounts for 38% of daily volume, which is typical for the Indian user base. Android makes up the majority of traffic.",
        evidence: [
          `Total events: ${rowCount}, unique sessions: ${Math.floor(rowCount * 0.6)}`,
          "Peak period: 10:00-14:00 UTC (38% of daily volume)",
          "Platform split: Android 58%, iOS 31%, Web 11%",
          "Overall apdex: 0.82 (target: 0.90)",
        ],
        chartConfig: {
          type: "area",
          title: "Event Volume (Last 12h)",
          xAxisLabel: "Hour",
          yAxisLabel: "Events",
          data: {
            labels: Array.from({ length: 12 }, (_, i) => {
              const h = (new Date().getHours() - 11 + i + 24) % 24;
              return `${String(h).padStart(2, "0")}:00`;
            }),
            datasets: [{ name: "Events", values: Array.from({ length: 12 }, () => Math.floor(Math.random() * 400) + 200) }],
          },
        },
      },
      {
        text: "error_occurred events up 2.4x compared to previous 24h window",
        severity: "warning",
        metric: { label: "Error Multiplier", value: "2.4x" },
        detail: "Error event volume has more than doubled, driven by the Checkout flow issues. The increase started 14 hours ago and has remained elevated. Non-error events show normal volume.",
        evidence: [
          "error_occurred: current 24h count vs prior 24h — 2.4x increase",
          "Top error screens: Checkout (48%), ProductDetail (26%)",
          "Crash rate: 2.8% (fleet avg 1.0%)",
          "ANR rate: 2.1% (fleet avg 1.2%)",
        ],
        // No chart
      },
    ],
  };
}

/**
 * Convert a natural language query to a mock SQL query and generate results.
 * This simulates the AI query endpoint behavior.
 */
export function generateAiQueryResponse(
  naturalLanguageQuery: string,
  context?: string
): {
  jobId: string;
  status: "COMPLETED";
  message: string;
  generatedSql: string;
  resultData: Record<string, unknown>[];
  nextToken: string | null;
  dataScannedInBytes: number;
  createdAt: string;
  completedAt: string;
  insights?: {
    answer: string;
    keyPoints: MockKeyPoint[];
  };
  sourcesAnalyzed?: string[];
  timeRange?: { start: string; end: string };
} {
  const nlLower = naturalLanguageQuery.toLowerCase();
  let generatedSql: string;

  // Generate time range (default to last 24 hours)
  const endTime = new Date();
  const startTime = new Date(endTime.getTime() - 24 * 60 * 60 * 1000);
  const formatTimestamp = (date: Date): string => {
    const year = date.getUTCFullYear();
    const month = String(date.getUTCMonth() + 1).padStart(2, "0");
    const day = String(date.getUTCDate()).padStart(2, "0");
    const hours = String(date.getUTCHours()).padStart(2, "0");
    const minutes = String(date.getUTCMinutes()).padStart(2, "0");
    const seconds = String(date.getUTCSeconds()).padStart(2, "0");
    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
  };
  const startTimestamp = formatTimestamp(startTime);
  const endTimestamp = formatTimestamp(endTime);

  // Simple pattern matching to generate SQL with timestamp filters
  if (nlLower.includes("count") && nlLower.includes("event")) {
    if (nlLower.includes("by") || nlLower.includes("group")) {
      generatedSql = `SELECT event_name, COUNT(*) AS "event_count" FROM pulse_athena_db.otel_data WHERE timestamp >= TIMESTAMP '${startTimestamp}' AND timestamp <= TIMESTAMP '${endTimestamp}' GROUP BY event_name ORDER BY "event_count" DESC LIMIT 20;`;
    } else {
      generatedSql = `SELECT COUNT(*) AS "total_events" FROM pulse_athena_db.otel_data WHERE timestamp >= TIMESTAMP '${startTimestamp}' AND timestamp <= TIMESTAMP '${endTimestamp}';`;
    }
  } else if (nlLower.includes("top") && nlLower.includes("user")) {
    const limitMatch = nlLower.match(/top\s+(\d+)/);
    const limit = limitMatch ? parseInt(limitMatch[1], 10) : 10;
    generatedSql = `SELECT session_id, COUNT(*) AS "event_count" FROM pulse_athena_db.otel_data WHERE timestamp >= TIMESTAMP '${startTimestamp}' AND timestamp <= TIMESTAMP '${endTimestamp}' GROUP BY session_id ORDER BY "event_count" DESC LIMIT ${limit};`;
  } else if (nlLower.includes("error")) {
    generatedSql = `SELECT event_name, screen_name, timestamp, session_id FROM pulse_athena_db.otel_data WHERE event_name = 'error_occurred' AND timestamp >= TIMESTAMP '${startTimestamp}' AND timestamp <= TIMESTAMP '${endTimestamp}' ORDER BY timestamp DESC LIMIT 50;`;
  } else if (nlLower.includes("screen") || nlLower.includes("page")) {
    generatedSql = `SELECT screen_name, COUNT(*) AS "view_count" FROM pulse_athena_db.otel_data WHERE event_name = 'screen_view' AND timestamp >= TIMESTAMP '${startTimestamp}' AND timestamp <= TIMESTAMP '${endTimestamp}' GROUP BY screen_name ORDER BY "view_count" DESC LIMIT 20;`;
  } else if (
    nlLower.includes("device") ||
    nlLower.includes("manufacturer")
  ) {
    generatedSql = `SELECT device_manufacturer, device_model_identifier, COUNT(*) AS "event_count" FROM pulse_athena_db.otel_data WHERE timestamp >= TIMESTAMP '${startTimestamp}' AND timestamp <= TIMESTAMP '${endTimestamp}' GROUP BY device_manufacturer, device_model_identifier ORDER BY "event_count" DESC LIMIT 20;`;
  } else if (
    nlLower.includes("most common") ||
    nlLower.includes("popular")
  ) {
    generatedSql = `SELECT event_name, COUNT(*) AS "event_count" FROM pulse_athena_db.otel_data WHERE timestamp >= TIMESTAMP '${startTimestamp}' AND timestamp <= TIMESTAMP '${endTimestamp}' GROUP BY event_name ORDER BY "event_count" DESC LIMIT 10;`;
  } else if (nlLower.includes("did") && nlLower.includes("user")) {
    generatedSql = `SELECT * FROM pulse_athena_db.otel_data WHERE timestamp >= TIMESTAMP '${startTimestamp}' AND timestamp <= TIMESTAMP '${endTimestamp}' LIMIT 100;`;
  } else {
    generatedSql = `SELECT * FROM pulse_athena_db.otel_data WHERE timestamp >= TIMESTAMP '${startTimestamp}' AND timestamp <= TIMESTAMP '${endTimestamp}' ORDER BY timestamp DESC LIMIT 100;`;
  }

  const results = generateMockQueryResults(generatedSql);
  const now = new Date().toISOString();

  const insights = generateMockInsights(
    naturalLanguageQuery,
    generatedSql,
    { rows: results.rows, totalRows: results.totalRows },
    context
  );

  const tableMatch = generatedSql.match(/FROM\s+([\w.]+)/i);
  const sourcesAnalyzed = tableMatch
    ? [tableMatch[1]]
    : ["pulse_athena_db.otel_data"];

  const timeRange = {
    start: startTime.toISOString(),
    end: endTime.toISOString(),
  };

  return {
    jobId: `ai_query_${Date.now()}`,
    status: "COMPLETED",
    message: "AI query completed successfully",
    generatedSql,
    resultData: results.rows,
    nextToken: results.nextToken,
    dataScannedInBytes: results.dataScannedInBytes,
    createdAt: now,
    completedAt: now,
    insights,
    sourcesAnalyzed,
    timeRange,
  };
}

/**
 * Cancel a query job
 */
export function cancelQueryJob(
  jobId: string
): { success: boolean; message: string } {
  const activeJobs = getJobsMap();
  const job = activeJobs.get(jobId);

  if (job) {
    job.status = "FAILED";
    activeJobs.delete(jobId);
    console.log("[Mock] Cancelled job:", jobId);
    return { success: true, message: "Query cancelled successfully" };
  }

  return {
    success: true,
    message: "Query cancelled (job not found or already completed)",
  };
}
