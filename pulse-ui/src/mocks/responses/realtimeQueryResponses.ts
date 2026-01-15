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
const manufacturers = ["Samsung", "Google", "Apple", "OnePlus", "Xiaomi", "Huawei"];

// Generate random device models
const deviceModels = ["SM-G998B", "Pixel 7", "iPhone 14", "OP9 Pro", "Mi 12", "P50"];

// Generate random screen names
const screenNames = ["HomeScreen", "ProductDetail", "Cart", "Checkout", "Profile", "Settings", "Search", "Login"];

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
    "network.type": ["wifi", "cellular", "ethernet"][Math.floor(Math.random() * 3)],
    "user.country": ["US", "IN", "UK", "DE", "FR", "JP"][Math.floor(Math.random() * 6)],
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
    device_manufacturer: manufacturers[Math.floor(Math.random() * manufacturers.length)],
    device_model_identifier: deviceModels[Math.floor(Math.random() * deviceModels.length)],
    os_name: platforms[Math.floor(Math.random() * platforms.length)],
    service_name: "pulse-mobile-sdk",
    session_id: `sess_${Math.random().toString(36).substr(2, 16)}`,
    screen_name: screenNames[Math.floor(Math.random() * screenNames.length)],
    network_carrier_mcc: String(Math.floor(Math.random() * 999) + 1),
    network_carrier_mnc: String(Math.floor(Math.random() * 99) + 1),
    network_carrier_icc: String(Math.floor(Math.random() * 999) + 1),
    pulse_app_state: ["foreground", "background"][Math.floor(Math.random() * 2)],
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
  const hasAggregation = sqlUpper.includes("COUNT(") || sqlUpper.includes("SUM(") || sqlUpper.includes("AVG(") || sqlUpper.includes("MIN(") || sqlUpper.includes("MAX(");
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
      ? groupByMatch[1].split(",").map((c) => c.trim().replace(/"/g, "").replace(/\s+/g, ""))
      : [];
    
    // Add group by columns first
    groupByCols.forEach((col) => {
      // Clean up column name (remove table prefix, json functions, etc.)
      const cleanCol = col.replace(/^[^.]+\./, "").replace(/json_extract_scalar\([^)]+\)/i, "json_field");
      columns.push({ name: cleanCol, type: "varchar" });
    });
    
    // Add aggregation columns from aliases
    aliases.forEach((alias) => {
      if (!columns.find((c) => c.name === alias)) {
        const type = alias.toLowerCase().includes("count") ? "bigint" : "double";
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
        const cleanCol = col.replace(/^[^.]+\./, "").replace(/json_extract_scalar\([^)]+\)/i, "json_field");
        if (cleanCol.includes("event_name") || cleanCol === "event_name") {
          row[cleanCol] = eventNames[Math.floor(Math.random() * eventNames.length)];
        } else if (cleanCol.includes("screen_name")) {
          row[cleanCol] = screenNames[Math.floor(Math.random() * screenNames.length)];
        } else if (cleanCol.includes("manufacturer") || cleanCol.includes("device")) {
          row[cleanCol] = manufacturers[Math.floor(Math.random() * manufacturers.length)];
        } else if (cleanCol.includes("os_name")) {
          row[cleanCol] = platforms[Math.floor(Math.random() * platforms.length)];
        } else {
          row[cleanCol] = `value_${i + 1}`;
        }
      });
      
      // Generate values for aggregation columns
      columns.forEach((col) => {
        if (!row[col.name]) {
          if (col.type === "bigint" || col.name.toLowerCase().includes("count")) {
            row[col.name] = Math.floor(Math.random() * 5000) + 100;
          } else if (col.type === "double") {
            row[col.name] = Math.round((Math.random() * 1000 + 10) * 100) / 100;
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
    __mockQueryJobs?: Map<string, {
      status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";
      sql: string;
      createdAt: number;
      completesAt: number;
      pollCount: number;
    }>;
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
export function createQueryJob(sql: string): { jobId: string; status: string } {
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
  
  console.log("[Mock] Created job:", jobId, "activeJobs size:", activeJobs.size);
  
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
  
  console.log("[Mock] Getting status for job:", jobId, "found:", !!job, "activeJobs size:", activeJobs.size);
  
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
  
  console.log("[Mock] Job status check - pollCount:", job.pollCount, "elapsed:", now - job.createdAt, "completesAt:", job.completesAt - job.createdAt);
  
  // Complete after 2 polls or after time elapsed
  if (job.pollCount >= 2 || now >= job.completesAt) {
    job.status = "SUCCEEDED";
    const results = generateMockQueryResults(job.sql);
    console.log("[Mock] Job completed, returning results with", results.rows.length, "rows");
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
  const isSimpleCount = sql.toUpperCase().includes("COUNT(*)") && !sql.toUpperCase().includes("GROUP BY");
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

  const statuses: Array<"COMPLETED" | "FAILED" | "CANCELLED"> = ["COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "FAILED", "CANCELLED"];
  
  const queries: MockQueryHistoryItem[] = historyQueries.map((queryString, index) => {
    const hoursAgo = index * 2 + Math.floor(Math.random() * 3);
    const createdAt = Date.now() - hoursAgo * 60 * 60 * 1000;
    const status = statuses[index % statuses.length];
    const executionTimeMs = status === "COMPLETED" ? Math.floor(Math.random() * 5000) + 500 : 0;
    
    return {
      jobId: "query_" + createdAt + "_" + index,
      queryString,
      queryExecutionId: "athena_exec_" + createdAt + "_" + index,
      status,
      resultLocation: status === "COMPLETED" ? "s3://pulse-results/queries/" + createdAt + "/" : undefined,
      errorMessage: status === "FAILED" ? "Query execution failed: syntax error near line 1" : undefined,
      dataScannedInBytes: status === "COMPLETED" ? Math.floor(Math.random() * 100000000) + 1000000 : undefined,
      createdAt,
      updatedAt: createdAt + 1000,
      completedAt: status === "COMPLETED" || status === "FAILED" ? createdAt + executionTimeMs : undefined,
    };
  });

  return { 
    queries,
    total: queries.length,
    limit: 50,
    offset: 0,
  };
}

/**
 * Cancel a query job
 */
export function cancelQueryJob(jobId: string): { success: boolean; message: string } {
  const activeJobs = getJobsMap();
  const job = activeJobs.get(jobId);
  
  if (job) {
    job.status = "FAILED";
    activeJobs.delete(jobId);
    console.log("[Mock] Cancelled job:", jobId);
    return { success: true, message: "Query cancelled successfully" };
  }
  
  return { success: true, message: "Query cancelled (job not found or already completed)" };
}
