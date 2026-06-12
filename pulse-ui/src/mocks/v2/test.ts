/**
 * Test script for V2 data query mock generator
 * Run with: npx ts-node src/mocks/v2/test.ts
 */

import { generateDataQueryMockResponseV2 } from "./dataQueryMockGenerator";

console.log("\n🧪 Testing Data Query Mock Generator V2\n");

// Test 1: Time-series query
console.log("=== Test 1: Time-Series Query ===");
const timeSeriesRequest = {
  dataType: "TRACES" as const,
  timeRange: {
    start: "2025-07-07T05:04:00Z",
    end: "2025-07-07T05:09:00Z",
  },
  select: [
    {
      function: "TIME_BUCKET" as const,
      param: { bucket: "1m", field: "Timestamp" },
      alias: "t1",
    },
    { function: "APDEX" as const, alias: "apdex" },
    { function: "INTERACTION_SUCCESS_COUNT" as const, alias: "success_count" },
    { function: "INTERACTION_ERROR_COUNT" as const, alias: "error_count" },
  ],
  groupBy: ["t1"],
  orderBy: [{ field: "t1", direction: "ASC" as const }],
};

const timeSeriesResponse = generateDataQueryMockResponseV2(timeSeriesRequest);
console.log("✅ Fields:", timeSeriesResponse.fields);
console.log("✅ Rows count:", timeSeriesResponse.rows.length);
console.log("✅ First row:", timeSeriesResponse.rows[0]);
console.log(
  "✅ Last row:",
  timeSeriesResponse.rows[timeSeriesResponse.rows.length - 1],
);

// Test 2: Grouped query (Platform)
console.log("\n=== Test 2: Grouped Query (Platform) ===");
const groupedRequest = {
  dataType: "TRACES" as const,
  timeRange: {
    start: "2025-07-07T05:04:00Z",
    end: "2025-07-07T05:09:00Z",
  },
  select: [
    {
      function: "COL" as const,
      alias: "platform",
      param: { field: "Platform" },
    },
    { function: "APDEX" as const, alias: "apdex" },
    { function: "CRASH" as const, alias: "crashes" },
  ],
  groupBy: ["platform"],
};

const groupedResponse = generateDataQueryMockResponseV2(groupedRequest);
console.log("✅ Fields:", groupedResponse.fields);
console.log("✅ Rows count:", groupedResponse.rows.length);
groupedResponse.rows.forEach((row) => console.log("  Row:", row));

// Test 3: Grouped query (Indian Regions)
console.log("\n=== Test 3: Grouped Query (Indian Regions) ===");
const regionRequest = {
  dataType: "TRACES" as const,
  timeRange: {
    start: "2025-07-07T05:04:00Z",
    end: "2025-07-07T05:09:00Z",
  },
  select: [
    { function: "COL" as const, alias: "region", param: { field: "GeoState" } },
    { function: "INTERACTION_SUCCESS_COUNT" as const, alias: "success_count" },
  ],
  groupBy: ["region"],
};

const regionResponse = generateDataQueryMockResponseV2(regionRequest);
console.log("✅ Fields:", regionResponse.fields);
console.log("✅ Rows count:", regionResponse.rows.length);
console.log("✅ Sample regions:");
regionResponse.rows
  .slice(0, 5)
  .forEach((row) => console.log("  Region:", row[0]));

// Test 4: Grouped query (Indian Devices)
console.log("\n=== Test 4: Grouped Query (Indian Devices) ===");
const deviceRequest = {
  dataType: "TRACES" as const,
  timeRange: {
    start: "2025-07-07T05:04:00Z",
    end: "2025-07-07T05:09:00Z",
  },
  select: [
    {
      function: "COL" as const,
      alias: "device",
      param: { field: "DeviceModel" },
    },
    { function: "CRASH" as const, alias: "crashes" },
    { function: "ANR" as const, alias: "anr" },
    { function: "FROZEN_FRAME" as const, alias: "frozen_frames" },
  ],
  groupBy: ["device"],
};

const deviceResponse = generateDataQueryMockResponseV2(deviceRequest);
console.log("✅ Fields:", deviceResponse.fields);
console.log("✅ Rows count:", deviceResponse.rows.length);
console.log("✅ Sample devices:");
deviceResponse.rows
  .slice(0, 5)
  .forEach((row) =>
    console.log(
      `  ${row[0]}: Crashes=${row[1]}, ANR=${row[2]}, Frozen=${row[3]}`,
    ),
  );

// Test 5: Grouped query (Indian Network Providers)
console.log("\n=== Test 5: Grouped Query (Indian Network Providers) ===");
const networkRequest = {
  dataType: "TRACES" as const,
  timeRange: {
    start: "2025-07-07T05:04:00Z",
    end: "2025-07-07T05:09:00Z",
  },
  select: [
    {
      function: "COL" as const,
      alias: "network",
      param: { field: "NetworkProvider" },
    },
    { function: "DURATION_P95" as const, alias: "p95_latency" },
  ],
  groupBy: ["network"],
};

const networkResponse = generateDataQueryMockResponseV2(networkRequest);
console.log("✅ Fields:", networkResponse.fields);
console.log("✅ Rows count:", networkResponse.rows.length);
console.log("✅ Sample networks:");
networkResponse.rows.forEach((row) =>
  console.log(`  ${row[0]}: P95=${row[1]}ms`),
);

// Test 6: Aggregate query
console.log("\n=== Test 6: Aggregate Query ===");
const aggregateRequest = {
  dataType: "TRACES" as const,
  timeRange: {
    start: "2025-07-07T05:04:00Z",
    end: "2025-07-07T05:09:00Z",
  },
  select: [
    { function: "APDEX" as const, alias: "apdex" },
    { function: "INTERACTION_SUCCESS_COUNT" as const, alias: "success_count" },
    { function: "INTERACTION_ERROR_COUNT" as const, alias: "error_count" },
    { function: "USER_CATEGORY_EXCELLENT" as const, alias: "user_excellent" },
    { function: "USER_CATEGORY_POOR" as const, alias: "user_poor" },
  ],
};

const aggregateResponse = generateDataQueryMockResponseV2(aggregateRequest);
console.log("✅ Fields:", aggregateResponse.fields);
console.log("✅ Rows count:", aggregateResponse.rows.length);
console.log("✅ Row:", aggregateResponse.rows[0]);

// Test 7: With filters (interaction names)
console.log("\n=== Test 7: With Filters (Interaction Names) ===");
const filteredRequest = {
  dataType: "TRACES" as const,
  timeRange: {
    start: "2025-07-07T05:04:00Z",
    end: "2025-07-07T05:09:00Z",
  },
  select: [
    {
      function: "COL" as const,
      alias: "interaction_name",
      param: { field: "SpanName" },
    },
    { function: "APDEX" as const, alias: "apdex" },
    { function: "CRASH" as const, alias: "crashes" },
  ],
  filters: [
    {
      field: "SpanName",
      operator: "IN" as const,
      value: ["Login", "Checkout", "ProductView"],
    },
  ],
  groupBy: ["interaction_name"],
};

const filteredResponse = generateDataQueryMockResponseV2(filteredRequest);
console.log("✅ Fields:", filteredResponse.fields);
console.log("✅ Rows count:", filteredResponse.rows.length);
console.log("✅ Interactions (should be Login, Checkout, ProductView):");
filteredResponse.rows.forEach((row) =>
  console.log(`  ${row[0]}: Apdex=${row[1]}, Crashes=${row[2]}`),
);

console.log("\n✅ All V2 Tests Complete! 🎉\n");
