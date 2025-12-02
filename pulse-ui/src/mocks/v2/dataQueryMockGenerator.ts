/**
 * Mock Data Query Generator V2
 *
 * Generates realistic mock responses for the data query API
 * based on request body parameters
 */

import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import {
  DataQueryRequestBody,
  DataQueryResponse,
} from "../../hooks/useGetDataQuery/useGetDataQuery.interface";
import { SpanType } from "../../constants/PulseOtelSemcov";

// Extend dayjs with UTC support
dayjs.extend(utc);

export class DataQueryMockGeneratorV2 {
  private static instance: DataQueryMockGeneratorV2;

  private constructor() {}

  static getInstance(): DataQueryMockGeneratorV2 {
    if (!DataQueryMockGeneratorV2.instance) {
      DataQueryMockGeneratorV2.instance = new DataQueryMockGeneratorV2();
    }
    return DataQueryMockGeneratorV2.instance;
  }

  /**
   * Generate mock response based on request body
   */
  generateResponse(requestBody: DataQueryRequestBody): DataQueryResponse {
    const { select, timeRange, groupBy, filters, limit } = requestBody;

    console.log("[DataQueryMockV2] Generating response for:", {
      hasTimeBucket: select.some((s) => s.function === "TIME_BUCKET"),
      hasGroupBy: groupBy && groupBy.length > 0,
      timeRange,
      selectCount: select.length,
      limit,
    });

    // Check if this is a time-series query (has TIME_BUCKET)
    const hasTimeBucket = select.some((s) => s.function === "TIME_BUCKET");

    // Check if this is grouped by other fields
    const hasGroupBy = groupBy && groupBy.length > 0 && !groupBy.includes("t1");

    // Check if this is a session records query (has event_names, traceid, spanid)
    const hasEventNames = select.some((s) => s.alias === "event_names");
    const hasTraceId = select.some((s) => s.alias === "traceid");
    const hasSpanId = select.some((s) => s.alias === "spanid");

    // Check if this is an exceptions query
    const isExceptionsQuery = requestBody.dataType === "EXCEPTIONS";

    // Check if this is a GroupId filter query (issue detail page)
    const groupIdFilter = filters?.find(
      (f) => f.field === "GroupId" && f.operator === "EQ",
    );
    const isGroupIdQuery = !!groupIdFilter;

    // Check if this is a stack traces query (has trace_id/span_id and stacktrace fields)
    const isStackTracesQuery =
      isGroupIdQuery &&
      (select.some((s) => s.alias === "trace_id") ||
        select.some((s) => s.alias === "span_id")) &&
      select.some((s) => s.alias === "stacktrace");

    // Check if this is a screen breakdown query (groups by screen_name)
    const isScreenBreakdownQuery =
      isGroupIdQuery && groupBy?.includes("screen_name");

    // Check if this is an issue summary query (groups by ExceptionMessage/ExceptionType for top 10)
    const isIssueSummaryQuery =
      isGroupIdQuery &&
      (groupBy?.includes("ExceptionMessage") ||
        groupBy?.includes("error_message"));

    if (isStackTracesQuery) {
      // Stack traces query - return individual exception entries
      const response = this.generateIssueStackTracesResponse(requestBody);
      console.log(
        "[DataQueryMockV2] Generated stack traces response:",
        response.rows.length,
        "rows",
      );
      return response;
    } else if (isScreenBreakdownQuery) {
      // Screen breakdown query
      const response = this.generateIssueScreenBreakdownResponse(requestBody);
      console.log(
        "[DataQueryMockV2] Generated screen breakdown response:",
        response.rows.length,
        "rows",
      );
      return response;
    } else if (isIssueSummaryQuery) {
      // Issue summary query (top 10 crashes for GroupId)
      const response = this.generateIssueSummaryResponse(requestBody);
      console.log(
        "[DataQueryMockV2] Generated issue summary response:",
        response.rows.length,
        "rows",
      );
      return response;
    } else if (
      isExceptionsQuery &&
      groupBy?.includes("group_id") &&
      select.some((s) => s.alias === "first_seen") &&
      select.some((s) => s.alias === "last_seen")
    ) {
      // Timestamp query (grouped by GroupId, selecting first_seen and last_seen)
      const response = this.generateExceptionTimestampsResponse(requestBody);
      console.log(
        "[DataQueryMockV2] Generated exception timestamps response:",
        response.rows.length,
        "rows",
      );
      return response;
    } else if (isExceptionsQuery && groupBy?.includes("group_id")) {
      // Exceptions list query (grouped by GroupId)
      const response = this.generateExceptionsListResponse(requestBody);
      console.log(
        "[DataQueryMockV2] Generated exceptions list response:",
        response.rows.length,
        "rows",
      );
      return response;
    } else if (hasTimeBucket) {
      // Time-series data
      const response = this.generateTimeSeriesResponse(requestBody);
      console.log(
        "[DataQueryMockV2] Generated time-series response:",
        response.rows.length,
        "rows",
      );
      return response;
    } else if (hasGroupBy) {
      // Check if this is a network error breakdown query (groups by status_code and/or error_type)
      const isNetworkErrorBreakdown =
        (groupBy?.includes("status_code") || groupBy?.includes("error_type")) &&
        filters?.some(
          (f) =>
            f.field === "SpanType" &&
            f.operator === "LIKE" &&
            Array.isArray(f.value) &&
            f.value
              .map((v) => String(v))
              .some((v: string) => v.includes("network.")),
        );

      // Check if this is a network issues by provider query (groups by network_provider)
      const isNetworkIssuesByProvider =
        groupBy?.includes("network_provider") &&
        filters?.some(
          (f) =>
            f.field === "SpanType" &&
            (f.operator === "EQ" || f.operator === "LIKE") &&
            Array.isArray(f.value) &&
            (f.value
              .map((v) => String(v))
              .some((v: string) => v === "network.0") ||
              f.value
                .map((v) => String(v))
                .some((v: string) => v.includes("network.4")) ||
              f.value
                .map((v) => String(v))
                .some((v: string) => v.includes("network.5"))),
        );

      if (isNetworkErrorBreakdown) {
        const response =
          this.generateNetworkErrorBreakdownResponse(requestBody);
        console.log(
          "[DataQueryMockV2] Generated network error breakdown response:",
          response.rows.length,
          "rows",
        );
        return response;
      } else if (isNetworkIssuesByProvider) {
        const response =
          this.generateNetworkIssuesByProviderResponse(requestBody);
        console.log(
          "[DataQueryMockV2] Generated network issues by provider response:",
          response.rows.length,
          "rows",
        );
        return response;
      }

      // Grouped data (e.g., by platform, region, device)
      const response = this.generateGroupedResponse(requestBody);
      console.log(
        "[DataQueryMockV2] Generated grouped response:",
        response.rows.length,
        "rows",
      );
      return response;
    } else if (
      (hasEventNames && (hasTraceId || hasSpanId)) ||
      (hasTraceId && hasSpanId && !hasTimeBucket && !hasGroupBy)
    ) {
      // Individual session records (multiple rows)
      // This handles both SessionReplays (with event_names) and SessionTimeline (with traceid/spanid)
      const response = this.generateIndividualRecordsResponse(requestBody);
      console.log(
        "[DataQueryMockV2] Generated individual records response:",
        response.rows.length,
        "rows",
      );
      return response;
    } else {
      // Aggregate data (single row)
      const response = this.generateAggregateResponse(requestBody);
      console.log(
        "[DataQueryMockV2] Generated aggregate response:",
        response.rows.length,
        "rows",
      );
      return response;
    }
  }

  /**
   * Generate time-series response (with TIME_BUCKET)
   */
  private generateTimeSeriesResponse(
    requestBody: DataQueryRequestBody,
  ): DataQueryResponse {
    const { select, timeRange, filters } = requestBody;

    console.log("[DataQueryMockV2] Time-series query - filters:", filters);

    // Get time bucket parameter
    const timeBucketField = select.find((s) => s.function === "TIME_BUCKET");
    const bucketSize = timeBucketField?.param?.bucket || "1m";

    // Generate time points
    const startTime = dayjs(timeRange.start).utc();
    const endTime = dayjs(timeRange.end).utc();

    console.log("[DataQueryMockV2] Time range:", {
      start: startTime.format(),
      end: endTime.format(),
      bucket: bucketSize,
    });

    const timePoints = this.generateTimePoints(startTime, endTime, bucketSize);

    if (timePoints.length === 0) {
      console.warn(
        "[DataQueryMockV2] No time points generated! Check time range.",
      );
    }

    // Extract fields (aliases)
    const fields: string[] = select.map((s) => s.alias);

    // Get groupBy values if any (for screen_name, etc.)
    const groupByFields = requestBody.groupBy || [];
    const groupByValues: Record<string, string> = {};
    
    // Extract groupBy field values from filters or use defaults
    groupByFields.forEach((groupField) => {
      if (groupField !== "t1") {
        // Find COL function for this groupBy field
        const colFunction = select.find(
          (s) => s.function === "COL" && s.alias === groupField,
        );
        if (colFunction) {
          const actualField = colFunction.param?.field || groupField;
          const groupValues = this.getGroupValues(actualField, filters);
          // Use first value or default
          groupByValues[groupField] = groupValues[0] || "default";
        }
      }
    });

    // Generate rows
    const rows: string[][] = timePoints.map((timestamp) => {
      const row: string[] = [];

      select.forEach((selectField) => {
        // Get groupValue for this field if it's a groupBy field
        const groupValue = groupByValues[selectField.alias] || undefined;
        
        const value = this.generateValueForFunction(
          selectField.function,
          timestamp,
          filters,
          groupValue,
          selectField.param, // Pass param for CUSTOM functions to check expressions
        );
        row.push(value);
      });

      return row;
    });

    console.log("[DataQueryMockV2] Sample row:", rows[0]);

    return { fields, rows };
  }

  /**
   * Generate grouped response (by platform, region, device, etc.)
   */
  private generateGroupedResponse(
    requestBody: DataQueryRequestBody,
  ): DataQueryResponse {
    const { select, groupBy, filters, limit } = requestBody;

    console.log(
      "[DataQueryMockV2] Grouped query - groupBy:",
      groupBy,
      "filters:",
      filters,
    );

    // Check if this is a network API query
    const isNetworkQuery =
      filters?.some(
        (f) =>
          f.field === "SpanType" &&
          f.operator === "LIKE" &&
          Array.isArray(f.value) &&
          f.value
            .map((v) => String(v))
            .some((v: string) => v.includes("network.")),
      ) || false;

    // Check if grouping by method and url (network list query)
    const isNetworkListQuery =
      (isNetworkQuery &&
        groupBy?.includes("method") &&
        groupBy?.includes("url")) ||
      false;

    // Check if this is a network detail query (has specific method and url filters)
    const methodFilter = filters?.find(
      (f) => f.field === "SpanAttributes['http.method']" && f.operator === "EQ",
    );
    const urlFilter = filters?.find(
      (f) => f.field === "SpanAttributes['http.url']" && f.operator === "EQ",
    );
    const isNetworkDetailQuery = Boolean(
      isNetworkQuery && methodFilter && urlFilter,
    );

    if (isNetworkListQuery || isNetworkDetailQuery) {
      return this.generateNetworkResponse(requestBody, isNetworkDetailQuery);
    }

    // Determine what we're grouping by
    const groupByField = groupBy?.[0] || "";

    // Check if there's a COL function for this groupBy field
    const colFunction = select.find(
      (s) => s.function === "COL" && s.alias === groupByField,
    );

    console.log("[DataQueryMockV2] COL function found:", colFunction);
    console.log("[DataQueryMockV2] GroupBy field:", groupByField);

    // Generate group values based on the COL function's field parameter or groupBy field
    const actualField = colFunction?.param?.field || groupByField;
    console.log("[DataQueryMockV2] Actual field to use:", actualField);

    const groupValues = this.getGroupValues(actualField, filters);

    console.log(
      "[DataQueryMockV2] Group values for",
      actualField,
      ":",
      groupValues,
    );

    // Extract fields - only use aliases from select, not the groupBy field
    // (The COL function's alias will be in the select array)
    const fields: string[] = select.map((s) => s.alias);

    // Generate rows
    const rows: string[][] = groupValues.map((groupValue) => {
      const row: string[] = [];

      select.forEach((selectField) => {
        const value = this.generateValueForFunction(
          selectField.function,
          null,
          filters,
          groupValue,
          selectField.param, // Pass the param object for CUSTOM functions
        );
        row.push(value);
      });

      return row;
    });

    console.log(
      "[DataQueryMockV2] Generated grouped response with",
      rows.length,
      "rows, fields:",
      fields,
    );

    // Apply limit if specified
    let limitedRows = rows;

    // If there's an orderBy with spanfreq DESC (top interactions query), sort intelligently
    const hasSpanfreqOrder = requestBody.orderBy?.some(
      (order) => order.field === "spanfreq" && order.direction === "DESC",
    );

    if (hasSpanfreqOrder && limit && rows.length > 0) {
      // For top interactions, sort by quality: best Apdex, lowest error rate, lowest latency
      const apdexIndex = fields.indexOf("apdex");
      const errorRateIndex = fields.indexOf("error_count");
      const successCountIndex = fields.indexOf("success_count");
      const p50Index = fields.indexOf("p50");

      console.log("[DataQueryMockV2] Sorting for top quality interactions");

      // Calculate quality score for each row
      const rowsWithScore = rows.map((row) => {
        const apdex = parseFloat(row[apdexIndex]) || 0;
        const errorCount = parseFloat(row[errorRateIndex]) || 0;
        const successCount = parseFloat(row[successCountIndex]) || 1;
        const errorRate =
          successCount > 0 ? errorCount / (successCount + errorCount) : 0;
        const p50 = parseFloat(row[p50Index]) || 0;

        // Quality score: Higher Apdex is better, lower error rate is better, lower latency is better
        // Normalize to 0-100 scale
        const apdexScore = apdex * 100; // 0-100
        const errorRateScore = (1 - errorRate) * 100; // 0-100 (inverted, so lower error = higher score)
        const latencyScore = Math.max(0, 100 - p50 / 10); // Lower latency = higher score

        // Weighted average: Apdex (40%), Error Rate (40%), Latency (20%)
        const qualityScore =
          apdexScore * 0.4 + errorRateScore * 0.4 + latencyScore * 0.2;

        return { row, qualityScore };
      });

      // Sort by quality score (highest first)
      rowsWithScore.sort((a, b) => b.qualityScore - a.qualityScore);

      // Take top N
      limitedRows = rowsWithScore.slice(0, limit).map((item) => item.row);

      console.log(
        "[DataQueryMockV2] Selected top",
        limit,
        "interactions by quality",
      );
    } else if (limit) {
      // Regular limit without special sorting
      limitedRows = rows.slice(0, limit);
    }

    console.log(
      "[DataQueryMockV2] After limit:",
      limitedRows.length,
      "rows (limit:",
      limit || "none",
      ")",
    );

    return { fields, rows: limitedRows };
  }

  /**
   * Generate aggregate response (single row, no grouping)
   */
  private generateAggregateResponse(
    requestBody: DataQueryRequestBody,
  ): DataQueryResponse {
    const { select, filters } = requestBody;

    // Extract fields (aliases)
    const fields: string[] = select.map((s) => s.alias);

    // Generate single row
    const row: string[] = select.map((selectField) => {
      return this.generateValueForFunction(
        selectField.function,
        null,
        filters,
        undefined,
        selectField.param, // Pass the param object for CUSTOM functions
      );
    });

    return { fields, rows: [row] };
  }

  /**
   * Generate individual session records response (multiple rows)
   */
  private generateIndividualRecordsResponse(
    requestBody: DataQueryRequestBody,
  ): DataQueryResponse {
    const { select, filters, limit, timeRange } = requestBody;

    // Extract fields (aliases)
    const fields: string[] = select.map((s) => s.alias);

    // Check if this is a SessionTimeline query (has TraceId filter)
    const traceIdFilter = filters?.find(
      (f) =>
        f.field === "TraceId" ||
        f.field === "traceid" ||
        f.field.toLowerCase() === "traceid",
    );
    const isSessionTimeline = !!traceIdFilter;
    let targetTraceId = "";
    if (traceIdFilter) {
      // Handle different filter value formats
      if (
        Array.isArray(traceIdFilter.value) &&
        traceIdFilter.value.length > 0
      ) {
        targetTraceId = String(traceIdFilter.value[0]).trim();
      } else if (traceIdFilter.value) {
        targetTraceId = String(traceIdFilter.value).trim();
      }
    }

    console.log("[DataQueryMockV2] TraceId filter detected:", {
      hasFilter: !!traceIdFilter,
      filterField: traceIdFilter?.field,
      filterValue: traceIdFilter?.value,
      extractedTraceId: targetTraceId,
      isSessionTimeline,
    });

    // For SessionTimeline, ensure we have a valid traceId
    if (isSessionTimeline && !targetTraceId) {
      console.warn(
        "[DataQueryMockV2] SessionTimeline query but no traceId in filter",
      );
      return { fields: [], rows: [] };
    }

    // Generate number of rows
    // For SessionTimeline: generate multiple spans (15-25) for the same traceId to show waterfall
    // For SessionReplays: generate multiple sessions (default 10)
    const numRows = isSessionTimeline
      ? 20 // Generate 20 spans for SessionTimeline waterfall view
      : limit && limit > 0
        ? Math.min(limit, 1000)
        : 10;

    // Log for debugging
    if (isSessionTimeline) {
      console.log(
        "[DataQueryMockV2] SessionTimeline query detected, targetTraceId:",
        targetTraceId,
        "will generate",
        numRows,
        "spans for waterfall view",
      );
    }

    // Generate unique trace IDs and span IDs
    const traceIds = new Set<string>();
    const spanIds = new Set<string>();

    // Event types for generating event_names

    // Device options
    const deviceOptions = [
      "iPhone 14 Pro",
      "iPhone 13",
      "Samsung Galaxy S23",
      "Google Pixel 7",
      "OnePlus 11",
      "Xiaomi 13",
      "Oppo Find X5",
    ];

    // OS versions
    const osVersions = ["iOS 17.0", "iOS 16.5", "Android 13", "Android 14"];

    // States (Indian states)
    const states = ["MH", "KA", "DL", "TN", "UP", "WB", "GJ", "RJ"];

    const rows: string[][] = [];

    // Generate time range for timestamps
    const startTime = timeRange
      ? dayjs(timeRange.start).utc()
      : dayjs().utc().subtract(7, "days");
    const endTime = timeRange ? dayjs(timeRange.end).utc() : dayjs().utc();
    const timeRangeMs = endTime.diff(startTime);

    // For SessionTimeline, generate consistent device/OS/state for all spans (same trace)
    let sessionDevice: string | null = null;
    let sessionOsVersion: string | null = null;
    let sessionOsName: string | null = null;
    let sessionState: string | null = null;
    let sessionUserId: string | null = null;

    if (isSessionTimeline && targetTraceId) {
      // Use traceId as seed for consistent values
      const seed = targetTraceId.charCodeAt(targetTraceId.length - 1) || 0;
      sessionDevice = deviceOptions[seed % deviceOptions.length];
      sessionOsVersion = osVersions[seed % osVersions.length];
      sessionOsName = sessionDevice.includes("iPhone") ? "iOS" : "Android";
      sessionState = states[seed % states.length];
      sessionUserId = `user_${seed}_${sessionOsName}_${sessionOsVersion.replace(/\./g, "_")}`;
    }

    for (let i = 0; i < numRows; i++) {
      // For SessionTimeline: ALWAYS use the exact target traceId from filter for ALL rows
      // For SessionReplays: generate sequential trace IDs
      let traceId: string;
      if (isSessionTimeline) {
        if (!targetTraceId || targetTraceId.trim() === "") {
          console.error(
            `[DataQueryMockV2] SessionTimeline query but targetTraceId is empty, skipping row ${i}`,
            { targetTraceId, traceIdFilter },
          );
          continue;
        }
        traceId = targetTraceId.trim(); // Use the exact traceId from filter
        // Log first row to verify
        if (i === 0) {
          console.log(
            `[DataQueryMockV2] Using traceId from filter for all rows: "${traceId}"`,
          );
        }
      } else {
        traceId = `traceid${i + 1}`;
        traceIds.add(traceId);
      }

      // Ensure traceId is not empty
      if (!traceId || traceId.trim() === "") {
        console.warn(`[DataQueryMockV2] Skipping row ${i} - empty traceId`);
        continue;
      }

      // Generate unique span ID
      let spanId = this.generateHexString(16).toUpperCase();
      while (spanIds.has(spanId)) {
        spanId = this.generateHexString(16).toUpperCase();
      }
      spanIds.add(spanId);

      // Generate session ID (session1, session2, etc.)
      const sessionId = `session${i + 1}`;

      // Generate timestamp within time range
      // For SessionTimeline: distribute spans evenly across time range for waterfall view
      // For SessionReplays: random timestamps
      let timestamp: string;
      if (isSessionTimeline && numRows > 1) {
        // Distribute spans evenly across the time range
        const progress = i / (numRows - 1); // 0 to 1
        const spanTime = startTime.add(progress * timeRangeMs, "millisecond");
        timestamp = spanTime.toISOString();
      } else {
        // Random timestamp for SessionReplays
        const randomTime = startTime.add(
          Math.random() * timeRangeMs,
          "millisecond",
        );
        timestamp = randomTime.toISOString();
      }

      // Generate event names (can be single or comma-separated multiple events)
      // Priority: crash > anr > non_fatal > frozen > empty (completed)
      // For SessionTimeline, generate events for each span to determine ANR/CRASH
      // For SessionReplays, generate events per session
      const rand = Math.random();
      const events: string[] = [];

      // For SessionTimeline: generate events for the single span
      // For SessionReplays, 40% chance to generate multiple events
      const hasMultipleEvents = isSessionTimeline
        ? true // Single span can have multiple events
        : Math.random() < 0.4;

      if (rand < 0.2) {
        // P1: device.crash (highest priority)
        events.push("device.crash");
        if (hasMultipleEvents) {
          // Add lower priority events to test priority selection
          events.push("device.anr");
          events.push("non_fatal");
          events.push("app.jank.frozen");
        }
      } else if (rand < 0.4) {
        // P2: device.anr
        events.push("device.anr");
        if (hasMultipleEvents) {
          // Add lower priority events
          events.push("non_fatal");
          events.push("app.jank.frozen");
        }
      } else if (rand < 0.6) {
        // P3: non_fatal
        events.push("non_fatal");
        if (hasMultipleEvents) {
          // Add lower priority events
          events.push("app.jank.frozen");
        }
      } else if (rand < 0.8) {
        // P4: app.jank.frozen (lowest priority event)
        events.push("app.jank.frozen");
        // No lower priority events to add
      } else if (rand < 0.9) {
        // Test combination: anr + frozen (should pick anr)
        events.push("device.anr");
        if (hasMultipleEvents) {
          events.push("app.jank.frozen");
        }
      } else {
        // Test combination: non_fatal + frozen (should pick non_fatal)
        events.push("non_fatal");
        if (hasMultipleEvents) {
          events.push("app.jank.frozen");
        }
      }
      // Note: rand >= 0.95 would be empty (completed session), but we're not handling that range

      // Generate event names
      const eventNames = events.join(",");
      const eventTimestamps =
        events.length > 0 ? events.map(() => timestamp).join(",") : "";

      // Generate device and OS (consistent for SessionTimeline, random for SessionReplays)
      const device =
        isSessionTimeline && sessionDevice
          ? sessionDevice
          : deviceOptions[Math.floor(Math.random() * deviceOptions.length)];
      const osVersion =
        isSessionTimeline && sessionOsVersion
          ? sessionOsVersion
          : osVersions[Math.floor(Math.random() * osVersions.length)];
      const osName =
        isSessionTimeline && sessionOsName
          ? sessionOsName
          : device.includes("iPhone")
            ? "iOS"
            : "Android";
      const state =
        isSessionTimeline && sessionState
          ? sessionState
          : states[Math.floor(Math.random() * states.length)];

      // Generate duration in milliseconds
      // For SessionTimeline: varied durations based on span type (shorter for quick operations, longer for complex ones)
      // For SessionReplays: random durations (max 200 seconds = 200000 ms)
      let durationMs: number;
      if (isSessionTimeline) {
        // Vary duration based on span index to simulate different operation types
        // Early spans (AppStart, ScreenView) are longer, middle spans (API calls) are shorter, later spans vary
        const spanType = i % 4;
        if (spanType === 0) {
          // App initialization, screen loads - longer (500ms to 3000ms)
          durationMs = Math.floor(Math.random() * 2500) + 500;
        } else if (spanType === 1) {
          // Network requests - medium (100ms to 1500ms)
          durationMs = Math.floor(Math.random() * 1400) + 100;
        } else if (spanType === 2) {
          // Quick operations - short (10ms to 200ms)
          durationMs = Math.floor(Math.random() * 190) + 10;
        } else {
          // Mixed operations - varied (50ms to 2000ms)
          durationMs = Math.floor(Math.random() * 1950) + 50;
        }
      } else {
        // SessionReplays: random durations (max 200 seconds = 200000 ms)
        durationMs = Math.floor(Math.random() * 200000) + 1000; // 1000ms (1s) to 200000ms (200s)
      }
      // Convert to nanoseconds (API expects nanoseconds, transform converts to milliseconds)
      const durationNs = durationMs * 1000000; // Convert milliseconds to nanoseconds
      const duration = durationNs.toString(); // Send as nanoseconds

      // Generate user ID (consistent for SessionTimeline, random for SessionReplays)
      const userId =
        isSessionTimeline && sessionUserId
          ? sessionUserId
          : `user_${Math.floor(Math.random() * 10000)}_${osName}_${osVersion.replace(/\./g, "_")}`;

      // Generate row based on select fields
      const row: string[] = select.map((selectField) => {
        const alias = selectField.alias;

        switch (alias) {
          case "event_names":
            return eventNames;
          case "event_timestamps":
            return eventTimestamps;
          case "interaction_timestamp":
            return timestamp;
          case "traceid":
            return traceId;
          case "spanid":
            return spanId;
          case "sessionid":
            return sessionId;
          case "device":
            return device;
          case "duration":
            return duration;
          case "userid":
            return userId;
          case "manufacturer":
            if (device.includes("iPhone")) return "Apple";
            if (device.includes("Samsung")) return "Samsung";
            if (device.includes("Pixel")) return "Google";
            if (device.includes("OnePlus")) return "OnePlus";
            if (device.includes("Xiaomi")) return "Xiaomi";
            if (device.includes("Oppo")) return "Oppo";
            return "";
          case "os_name":
            return osName === "iOS" ? "ios" : "androidfull";
          case "os_type":
            return osName;
          case "os_version":
            return osVersion.split(" ")[1] || "14";
          case "os_description":
            return "";
          case "state":
            return state;
          case "country":
            return "IN";
          case "frozen_frame":
            return eventNames.includes("frozen") ? "1.0" : "0.0";
          case "anr":
            // For individual spans, return 1 if span has ANR event, 0 otherwise
            // Check for "device.anr" or "anr" in event names
            const hasAnr =
              eventNames.toLowerCase().includes("device.anr") ||
              eventNames.toLowerCase().includes("anr");
            return hasAnr ? "1" : "0";
          case "crash":
            // For individual spans, return 1 if span has crash event, 0 otherwise
            // Check for "device.crash" or "crash" in event names
            const hasCrash =
              eventNames.toLowerCase().includes("device.crash") ||
              eventNames.toLowerCase().includes("crash");
            return hasCrash ? "1" : "0";
          case "spanname":
            // Generate span names for SessionTimeline
            // Use traceId as seed to ensure consistent names per session
            const spanNames = [
              "AppStart",
              "ScreenView",
              "NetworkRequest",
              "UserInteraction",
              "DatabaseQuery",
              "API Call",
              "ImageLoad",
              "Render",
              "Navigation",
              "Authentication",
              "DataFetch",
              "CacheRead",
              "FileUpload",
              "PaymentProcessing",
              "SearchQuery",
              "NotificationSend",
              "LocationUpdate",
              "AnalyticsTrack",
              "BackgroundSync",
              "PushNotification",
            ];
            const spanNameSeed = traceId.charCodeAt(traceId.length - 1) || 0;
            return spanNames[(spanNameSeed + i) % spanNames.length];
          case "timestamp":
            return timestamp;
          case "error":
            return eventNames.toLowerCase().includes("crash") ||
              eventNames.toLowerCase().includes("anr")
              ? "true"
              : "false";
          case "error_type":
            if (eventNames.toLowerCase().includes("crash")) return "crash";
            if (eventNames.toLowerCase().includes("anr")) return "anr";
            return "";
          case "error_message":
            if (eventNames.toLowerCase().includes("crash"))
              return "Application crashed";
            if (eventNames.toLowerCase().includes("anr"))
              return "Application not responding";
            return "";
          default:
            // For other fields, use the standard function generator
            return this.generateValueForFunction(
              selectField.function,
              timestamp,
              filters,
              undefined,
              selectField.param,
            );
        }
      });

      rows.push(row);
    }

    return { fields, rows };
  }

  /**
   * Generate a random hex string of specified length
   */
  private generateHexString(length: number): string {
    const chars = "0123456789ABCDEF";
    let result = "";
    for (let i = 0; i < length; i++) {
      result += chars[Math.floor(Math.random() * chars.length)];
    }
    return result;
  }

  /**
   * Generate time points based on bucket size
   */
  private generateTimePoints(
    start: dayjs.Dayjs,
    end: dayjs.Dayjs,
    bucketSize: string,
  ): string[] {
    const points: string[] = [];
    let current = start;

    // Parse bucket size (e.g., "1m", "5m", "1h", "1d", "1w", "1M")
    const bucketValue = parseInt(bucketSize);
    const bucketUnit = bucketSize.replace(/\d+/g, "");

    let unit: dayjs.ManipulateType = "minute";
    if (bucketUnit === "m") unit = "minute";
    else if (bucketUnit === "h") unit = "hour";
    else if (bucketUnit === "d") unit = "day";
    else if (bucketUnit === "w") unit = "week";
    else if (bucketUnit === "M") unit = "month";

    console.log(
      "[DataQueryMockV2] Bucket size:",
      bucketSize,
      "-> value:",
      bucketValue,
      "unit:",
      bucketUnit,
      "->",
      unit,
    );

    // Generate time points
    while (current.isBefore(end) || current.isSame(end)) {
      // Format as ISO string with 'Z' suffix (e.g., "2025-07-07T05:04Z")
      const formatted = current.format("YYYY-MM-DDTHH:mm") + "Z";
      points.push(formatted);
      current = current.add(bucketValue, unit);
    }

    console.log(
      "[DataQueryMockV2] Generated",
      points.length,
      "time points from",
      start.format(),
      "to",
      end.format(),
    );

    return points;
  }

  /**
   * Generate value for a specific function type
   */
  private generateValueForFunction(
    functionType: string,
    timestamp: string | null,
    filters?: any[],
    groupValue?: string,
    param?: any, // Additional parameter object (e.g., for CUSTOM functions)
  ): string {
    // Get interaction/span name filter if exists
    const interactionFilter = filters?.find(
      (f) => f.field === "InteractionName" || f.field === "SpanName",
    );
    const interactionName = Array.isArray(interactionFilter?.value)
      ? interactionFilter.value[0]
      : interactionFilter?.value || "default";

    switch (functionType) {
      case "TIME_BUCKET":
        return timestamp || new Date().toISOString();

      case "APDEX":
        return this.randomApdex(interactionName, groupValue).toFixed(16);

      case "INTERACTION_SUCCESS_COUNT":
        return this.randomCount(
          80,
          150,
          interactionName,
          groupValue,
        ).toString();

      case "INTERACTION_ERROR_COUNT":
        return this.randomCount(10, 50, interactionName, groupValue).toString();

      case "INTERACTION_ERROR_DISTINCT_USERS":
        return this.randomCount(5, 30, interactionName, groupValue).toString();

      case "FROZEN_FRAME":
        return this.randomCount(5, 25, interactionName, groupValue).toString();

      case "ANR":
        return this.randomCount(3, 15, interactionName, groupValue).toString();

      case "CRASH":
        return this.randomCount(2, 12, interactionName, groupValue).toString();

      case "USER_CATEGORY_EXCELLENT":
        return this.randomCount(20, 50, interactionName, groupValue).toString();

      case "USER_CATEGORY_GOOD":
        return this.randomCount(
          50,
          100,
          interactionName,
          groupValue,
        ).toString();

      case "USER_CATEGORY_AVERAGE":
        return this.randomCount(0, 20, interactionName, groupValue).toString();

      case "USER_CATEGORY_POOR":
        return this.randomCount(10, 40, interactionName, groupValue).toString();

      case "DURATION_P50":
        return this.randomLatency(
          200,
          800,
          interactionName,
          groupValue,
        ).toString();

      case "DURATION_P95":
        // Return latency in milliseconds (not nanoseconds)
        const p95Ms = this.randomLatency(
          800,
          2000,
          interactionName,
          groupValue,
        );
        return p95Ms.toString();

      case "DURATION_P99":
        return this.randomLatency(
          1500,
          4000,
          interactionName,
          groupValue,
        ).toString();

      case "NET_0":
        return this.randomCount(0, 2, interactionName, groupValue).toString();

      case "NET_2XX":
        return this.randomCount(
          80,
          140,
          interactionName,
          groupValue,
        ).toString();

      case "NET_3XX":
        return this.randomCount(0, 5, interactionName, groupValue).toString();

      case "NET_4XX":
        return this.randomCount(2, 15, interactionName, groupValue).toString();

      case "NET_5XX":
        return this.randomCount(0, 8, interactionName, groupValue).toString();

      case "COL":
        return groupValue || "unknown";

      case "CUSTOM":
        // Handle custom expressions based on the expression parameter
        const expression = param?.expression || "";

        // Handle sumIf(Duration, SpanType = 'screen_session') - total time spent on screen
        if (
          expression.includes("sumIf(Duration") &&
          expression.includes("screen_session")
        ) {
          // Generate realistic total time spent (in nanoseconds)
          // Hook calculates: avgTimeSpent = timeSpent / (sessions * 1000000)
          // To get 20-60 seconds avg per session with 50-200 sessions:
          // timeSpent = (20-60) * (50-200) * 1000000 = 1,000,000,000 - 12,000,000,000 ns
          const totalNs = this.randomCount(
            1000000000, // 1 billion ns = 20s * 50 sessions * 1M
            12000000000, // 12 billion ns = 60s * 200 sessions * 1M
            interactionName,
            groupValue,
          );
          return totalNs.toString();
        }

        // Handle sumIf(Duration, SpanType = 'screen_load') - total screen load time
        if (
          expression.includes("sumIf(Duration") &&
          expression.includes("screen_load")
        ) {
          // Generate realistic total load time (in nanoseconds)
          // Hook calculates: avgLoadTime = loadTime / (loads * 1000000)
          // To get 1-3 seconds avg per load with 50-200 loads:
          // loadTime = (1-3) * (50-200) * 1000000 = 50,000,000 - 600,000,000 ns
          const totalNs = this.randomCount(
            50000000, // 50 million ns = 1s * 50 loads * 1M
            600000000, // 600 million ns = 3s * 200 loads * 1M
            interactionName,
            groupValue,
          );
          return totalNs.toString();
        }

        // Handle countIf(SpanType = 'screen_session') - session count
        if (
          expression.includes("countIf") &&
          expression.includes("screen_session")
        ) {
          // Generate realistic session count per time bucket
          // 50-200 sessions per time bucket
          return this.randomCount(
            50,
            200,
            interactionName,
            groupValue,
          ).toString();
        }

        // Handle countIf(SpanType = 'screen_load') - load count
        if (
          expression.includes("countIf") &&
          expression.includes("screen_load")
        ) {
          // Generate realistic load count per time bucket
          // 50-200 loads per time bucket
          return this.randomCount(
            50,
            200,
            interactionName,
            groupValue,
          ).toString();
        }

        // Handle COUNT() expression
        if (expression.includes("COUNT()")) {
          if (groupValue) {
            // Check if this is a screen count vs interaction count
            const isScreenQuery = groupValue.includes("Screen");

            if (isScreenQuery) {
              // Screens get higher counts
              return this.randomCount(
                50000,
                100000,
                interactionName,
                groupValue,
              ).toString();
            } else {
              // Interactions get lower counts
              return this.randomCount(
                200,
                400,
                interactionName,
                groupValue,
              ).toString();
            }
          }
        }

        // Handle uniqCombined(UserId) and similar
        if (expression.includes("uniqCombined(UserId)")) {
          return this.randomCount(
            100000,
            150000,
            interactionName,
            groupValue,
          ).toString();
        }

        // Handle uniqCombined(SessionId)
        if (expression.includes("uniqCombined(SessionId)")) {
          return this.randomCount(
            5000,
            15000,
            interactionName,
            groupValue,
          ).toString();
        }

        // Default fallback for other CUSTOM expressions
        return this.randomCount(
          1000,
          5000,
          interactionName,
          groupValue,
        ).toString();

      default:
        return "0";
    }
  }

  /**
   * Get group values based on groupBy field
   */
  private getGroupValues(groupByField: string, filters?: any[]): string[] {
    // Handle special field names like SpanAttributes['screen.name']
    let normalizedField = groupByField.toLowerCase();

    // Extract actual field name from SpanAttributes notation
    if (normalizedField.includes(`spanattributes['${SpanType.SCREEN_NAME}']`)) {
      normalizedField = "screen_name";
    }

    // Early return for screen_name to use predefined values
    if (normalizedField === "screen_name") {
      const screenNames = [
        "HomeScreen",
        "ProductListScreen",
        "ProductDetailScreen",
        "CheckoutFormScreen",
        "PaymentScreen",
        "ProfileScreen",
        "SearchResultsScreen",
        "OrderListScreen",
        "CartScreen",
        "WishlistScreen",
        "SettingsScreen",
        "NotificationsScreen",
      ];
      
      // Check if there's a filter with specific screen names
      if (filters) {
        const screenNameFilter = filters.find(
          (f) =>
            f.field === groupByField ||
            f.field?.toLowerCase().includes("screen") ||
            f.field?.toLowerCase().includes("screen_name"),
        );
        
        if (screenNameFilter && screenNameFilter.value) {
          const filterValues = Array.isArray(screenNameFilter.value)
            ? screenNameFilter.value
            : [screenNameFilter.value];
          console.log(
            "[DataQueryMockV2] Using filter values for screen_name:",
            filterValues,
          );
          return filterValues.filter(Boolean);
        }
      }
      
      console.log(
        "[DataQueryMockV2] Using predefined screen names:",
        screenNames,
      );
      return screenNames;
    }

    // Handle custom attributes like UserAttributes['subscriptionPlan']
    const userAttributeMatch = groupByField.match(
      /userattributes\['([^']+)'\]/i,
    );
    if (userAttributeMatch) {
      const attributeName = userAttributeMatch[1];
      console.log(
        "[DataQueryMockV2] Detected UserAttributes custom attribute:",
        attributeName,
      );

      // Only support subscriptionPlan attribute
      if (attributeName.toLowerCase() !== "subscriptionplan") {
        console.log(
          "[DataQueryMockV2] Unsupported custom attribute:",
          attributeName,
          "- only 'subscriptionPlan' is supported",
        );
        return [];
      }

      // First, check if there's a filter for this field with specific values
      if (filters) {
        const fieldFilter = filters.find(
          (f) =>
            f.field === groupByField ||
            f.field
              ?.toLowerCase()
              .includes(`userattributes['${attributeName.toLowerCase()}']`),
        );

        if (fieldFilter && fieldFilter.value) {
          const values = Array.isArray(fieldFilter.value)
            ? fieldFilter.value
            : [fieldFilter.value];

          // Validate that values are from the allowed set
          const allowedValues = ["Starter", "Pro", "Enterprise"];
          const validValues = values.filter((v: any) =>
            allowedValues.includes(String(v)),
          );

          if (validValues.length > 0) {
            console.log(
              "[DataQueryMockV2] Using filter values for subscriptionPlan:",
              validValues,
            );
            return validValues;
          } else {
            // Invalid values provided - return empty array to show no data
            console.log(
              "[DataQueryMockV2] Invalid subscriptionPlan values provided:",
              values,
              "- returning empty data",
            );
            return [];
          }
        }
      }

      // Default values for subscriptionPlan (only if no filter is provided)
      return ["Starter", "Pro", "Enterprise"];
    }

    // Handle custom attributes like SpanAttributes['subscriptionPlan'] (legacy)
    const spanAttributeMatch = groupByField.match(
      /spanattributes\['([^']+)'\]/i,
    );
    if (spanAttributeMatch) {
      const attributeName = spanAttributeMatch[1];
      console.log(
        "[DataQueryMockV2] Detected SpanAttributes custom attribute:",
        attributeName,
      );

      // First, check if there's a filter for this field with specific values
      if (filters) {
        const fieldFilter = filters.find(
          (f) =>
            f.field === groupByField ||
            f.field?.toLowerCase().includes(attributeName.toLowerCase()),
        );

        if (fieldFilter && fieldFilter.value) {
          const values = Array.isArray(fieldFilter.value)
            ? fieldFilter.value
            : [fieldFilter.value];

          console.log(
            "[DataQueryMockV2] Using filter values for SpanAttributes",
            attributeName,
            ":",
            values,
          );
          return values.filter(Boolean); // Remove any null/undefined values
        }
      }

      // For subscriptionPlan, return default values if no filter
      if (attributeName.toLowerCase() === "subscriptionplan") {
        return ["Starter", "Pro", "Enterprise"];
      }

      // For other custom attributes, return generic values
      return ["Value1", "Value2", "Value3"];
    }

    console.log(
      "[DataQueryMockV2] Normalizing field:",
      groupByField,
      "→",
      normalizedField,
    );

    // First, check if there's a filter for this field with specific values
    if (filters) {
      const fieldFilter = filters.find(
        (f) =>
          f.field === groupByField ||
          (f.field === "SpanName" && groupByField === "interaction_name") ||
          (f.field === "InteractionName" &&
            groupByField === "interaction_name"),
      );

      if (fieldFilter && fieldFilter.value) {
        const values = Array.isArray(fieldFilter.value)
          ? fieldFilter.value
          : [fieldFilter.value];

        console.log(
          "[DataQueryMockV2] Using filter values for",
          groupByField,
          ":",
          values,
        );
        return values.filter(Boolean); // Remove any null/undefined values
      }
    }

    // Handle custom attribute aliases (e.g., "subscriptionPlan" when groupBy uses the alias)
    // Check if this might be a custom attribute alias by looking at filters
    if (filters && normalizedField === "subscriptionplan") {
      // Look for UserAttributes filters
      const userAttrFilter = filters.find((f) =>
        f.field?.toLowerCase().includes("userattributes['subscriptionplan']"),
      );
      if (userAttrFilter && userAttrFilter.value) {
        const values = Array.isArray(userAttrFilter.value)
          ? userAttrFilter.value
          : [userAttrFilter.value];

        // Validate that values are from the allowed set
        const allowedValues = ["Starter", "Pro", "Enterprise"];
        const validValues = values.filter((v: any) =>
          allowedValues.includes(String(v)),
        );

        if (validValues.length > 0) {
          console.log(
            "[DataQueryMockV2] Using filter values for subscriptionPlan alias:",
            validValues,
          );
          return validValues;
        } else {
          // Invalid values provided - return empty array to show no data
          console.log(
            "[DataQueryMockV2] Invalid subscriptionPlan alias values provided:",
            values,
            "- returning empty data",
          );
          return [];
        }
      }
      // Default values for subscriptionPlan (only if no filter is provided)
      return ["Starter", "Pro", "Enterprise"];
    }

    // Map of field to possible values (case-insensitive matching)
    const groupValueMap: { [key: string]: string[] } = {
      platform: ["Android", "iOS"],
      region: [
        "Maharashtra",
        "Karnataka",
        "Delhi",
        "Tamil Nadu",
        "Uttar Pradesh",
        "West Bengal",
        "Gujarat",
        "Rajasthan",
      ],
      geostate: [
        "Maharashtra",
        "Karnataka",
        "Delhi",
        "Tamil Nadu",
        "Uttar Pradesh",
        "West Bengal",
        "Gujarat",
        "Rajasthan",
      ],
      devicemodel: [
        "Samsung Galaxy S21",
        "iPhone 13 Pro",
        "Redmi Note 11",
        "OnePlus Nord 2",
        "Realme 9 Pro",
        "Vivo V23",
        "Oppo Reno 7",
        "iPhone 14",
        "Samsung Galaxy A53",
        "Poco X4 Pro",
      ],
      osversion: [
        "Android 13",
        "Android 12",
        "Android 11",
        "iOS 16",
        "iOS 15",
        "iOS 14",
      ],
      appversion: ["1.0.0", "1.1.0", "1.2.0", "2.0.0", "2.1.0"],
      networkprovider: [
        "Jio",
        "Airtel",
        "Vi (Vodafone Idea)",
        "BSNL",
        "Aircel",
        "Other",
      ],
      connectiontype: ["WiFi", "4G", "5G", "3G"],
      spanname: [
        "LoginSuccess",
        "PaymentSuccess",
        "CheckoutComplete",
        "ProductView",
        "AddToCart",
        "CreateAccount",
        "SearchResults",
        "ContestJoinSuccess",
      ],
      interactionname: [
        "LoginSuccess",
        "PaymentSuccess",
        "CheckoutComplete",
        "ProductView",
        "AddToCart",
        "CreateAccount",
        "SearchResults",
        "ContestJoinSuccess",
      ],
      interaction_name: [
        "LoginSuccess",
        "PaymentSuccess",
        "CheckoutComplete",
        "ProductView",
        "AddToCart",
        "CreateAccount",
        "SearchResults",
        "ContestJoinSuccess",
      ],
      screen_name: [
        "HomeScreen",
        "ProductListScreen",
        "ProductDetailScreen",
        "CheckoutFormScreen",
        "PaymentScreen",
        "ProfileScreen",
        "SearchResultsScreen",
        "OrderListScreen",
      ],
    };

    // Try to find values using normalized field name
    const values = groupValueMap[normalizedField];

    if (values && values.length > 0) {
      console.log(
        "[DataQueryMockV2] Using predefined values for",
        groupByField,
        ":",
        values,
      );
      return values;
    }

    // Fallback - should not happen if mappings are correct
    console.error(
      "[DataQueryMockV2] ❌ NO MAPPING FOUND for field:",
      groupByField,
      "normalized:",
      normalizedField,
      "- CHECK YOUR MOCK CONFIGURATION!",
    );
    // Return at least 5-8 generic items to match limit requests
    return [
      "Item1",
      "Item2",
      "Item3",
      "Item4",
      "Item5",
      "Item6",
      "Item7",
      "Item8",
    ];
  }

  /**
   * Generate random Apdex score (0-1)
   */
  private randomApdex(seed: string = "default", groupValue?: string): number {
    const hash = this.hashString(
      seed + (groupValue || "") + Date.now().toString().slice(-4),
    );
    const base = 0.8 + (hash % 15) / 100; // 0.80 - 0.95
    const variance = (Math.random() - 0.5) * 0.08;
    return Math.max(0.5, Math.min(1, base + variance));
  }

  /**
   * Generate random count
   */
  private randomCount(
    min: number,
    max: number,
    seed: string = "default",
    groupValue?: string,
  ): number {
    const hash = this.hashString(
      seed + (groupValue || "") + Date.now().toString().slice(-4),
    );
    const range = max - min;
    const base = min + (hash % range);
    const variance = Math.floor((Math.random() - 0.5) * range * 0.3);
    return Math.max(0, base + variance);
  }

  /**
   * Generate random latency (ms)
   */
  private randomLatency(
    min: number,
    max: number,
    seed: string = "default",
    groupValue?: string,
  ): number {
    const hash = this.hashString(
      seed + (groupValue || "") + Date.now().toString().slice(-4),
    );
    const range = max - min;
    const base = min + (hash % range);
    const variance = Math.floor((Math.random() - 0.5) * range * 0.3);
    return Math.max(min, base + variance);
  }

  /**
   * Simple string hash function for consistent randomness
   */
  private hashString(str: string): number {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = (hash << 5) - hash + char;
      hash = hash & hash; // Convert to 32-bit integer
    }
    return Math.abs(hash);
  }

  /**
   * Generate network API response (for network list and detail queries)
   */
  private generateNetworkResponse(
    requestBody: DataQueryRequestBody,
    isDetailQuery: boolean,
  ): DataQueryResponse {
    const { select, filters } = requestBody;

    console.log(
      "[DataQueryMockV2] Generating network response (detail:",
      isDetailQuery,
      ")",
    );

    // Extract fields
    const fields: string[] = select.map((s) => s.alias);

    // Mock network API data
    const networkApis = [
      { method: "GET", url: "/api/v1/users/profile" },
      { method: "POST", url: "/api/v1/transactions/list" },
      { method: "POST", url: "/api/v1/auth/refresh" },
      { method: "GET", url: "/api/v1/products/search" },
      { method: "POST", url: "/api/v1/orders/create" },
      { method: "GET", url: "/api/v1/notifications/list" },
      { method: "GET", url: "/api/v1/teams/list" },
      { method: "POST", url: "/api/v1/contests/join" },
    ];

    // For detail query, filter to specific method and url
    let filteredApis = networkApis;
    if (isDetailQuery) {
      const methodFilter = filters?.find(
        (f) =>
          f.field === "SpanAttributes['http.method']" && f.operator === "EQ",
      );
      const urlFilter = filters?.find(
        (f) => f.field === "SpanAttributes['http.url']" && f.operator === "EQ",
      );

      const targetMethod = methodFilter
        ? Array.isArray(methodFilter.value)
          ? String(methodFilter.value[0])
          : String(methodFilter.value || "")
        : "";
      const targetUrl = urlFilter
        ? Array.isArray(urlFilter.value)
          ? String(urlFilter.value[0])
          : String(urlFilter.value || "")
        : "";

      filteredApis = networkApis.filter(
        (api) => api.method === targetMethod && api.url === targetUrl,
      );

      // If not found, create a single entry with the requested method and url
      if (filteredApis.length === 0 && targetMethod && targetUrl) {
        filteredApis = [{ method: targetMethod, url: targetUrl }];
      }
    }

    // For list query, we might need to filter by screen name if provided
    if (!isDetailQuery) {
      const screenNameFilter = filters?.find(
        (f) =>
          f.field === `SpanAttributes['${SpanType.SCREEN_NAME}']` && f.operator === "EQ",
      );
      if (screenNameFilter) {
        // In a real scenario, we'd filter by screen name, but for mock we'll return all
        // The screen name filter is handled by the backend in real queries
      }
    }

    // Generate rows
    const rows: string[][] = filteredApis.map((api) => {
      const row: string[] = [];

      select.forEach((selectField) => {
        const alias = selectField.alias;

        switch (alias) {
          case "method":
            row.push(api.method);
            break;
          case "url":
            row.push(api.url);
            break;
          case "total_requests":
            // Generate realistic request count based on API type
            // GET requests typically have more volume
            const baseTotal =
              api.method === "GET"
                ? this.randomCount(5000, 80000, api.method + api.url)
                : this.randomCount(1000, 25000, api.method + api.url);
            row.push(baseTotal.toString());
            break;
          case "success_requests":
            // 96-99.5% success rate (more realistic)
            const totalRequests =
              api.method === "GET"
                ? this.randomCount(5000, 80000, api.method + api.url)
                : this.randomCount(1000, 25000, api.method + api.url);
            const successRate = 0.96 + Math.random() * 0.035; // 96-99.5%
            row.push(Math.floor(totalRequests * successRate).toString());
            break;
          case "response_time":
            // Average response time in ms
            // GET requests are typically faster, POST requests slower
            const avgTime =
              api.method === "GET"
                ? this.randomLatency(80, 600, api.method + api.url)
                : this.randomLatency(150, 1200, api.method + api.url);
            row.push(avgTime.toString());
            break;
          case "all_sessions":
            // Unique sessions (15-35% of total requests)
            // More sessions for GET requests (more users browsing)
            const total =
              api.method === "GET"
                ? this.randomCount(5000, 80000, api.method + api.url)
                : this.randomCount(1000, 25000, api.method + api.url);
            const sessionRate =
              api.method === "GET"
                ? 0.15 + Math.random() * 0.25 // 15-40% for GET
                : 0.1 + Math.random() * 0.2; // 10-30% for POST
            row.push(Math.floor(total * sessionRate).toString());
            break;
          case "p50":
            // P50 latency (50-70% of average response time)
            const avgResponseTime = this.randomLatency(
              100,
              1000,
              api.method + api.url,
            );
            const p50Value = Math.floor(
              avgResponseTime * (0.5 + Math.random() * 0.2),
            );
            row.push(p50Value.toString());
            break;
          case "p95":
            // P95 latency (180-280% of average response time)
            const avgResponseTimeP95 = this.randomLatency(
              100,
              1000,
              api.method + api.url,
            );
            const p95Value = Math.floor(
              avgResponseTimeP95 * (1.8 + Math.random() * 1.0),
            );
            row.push(p95Value.toString());
            break;
          case "p99":
            // P99 latency (250-450% of average response time)
            const avgResponseTimeP99 = this.randomLatency(
              100,
              1000,
              api.method + api.url,
            );
            const p99Value = Math.floor(
              avgResponseTimeP99 * (2.5 + Math.random() * 2.0),
            );
            row.push(p99Value.toString());
            break;
          case "status_code":
            // Most common status codes: 200, 201, 400, 404, 500
            const statusCodes = ["200", "201", "400", "404", "500"];
            row.push(
              statusCodes[Math.floor(Math.random() * statusCodes.length)],
            );
            break;
          default:
            // For other fields, use the standard function generator
            row.push(
              this.generateValueForFunction(
                selectField.function,
                null,
                filters,
                undefined,
                selectField.param,
              ),
            );
        }
      });

      return row;
    });

    // Apply orderBy if specified (e.g., order by total_requests DESC)
    if (requestBody.orderBy && requestBody.orderBy.length > 0) {
      const orderBy = requestBody.orderBy[0];
      const fieldIndex = fields.indexOf(orderBy.field);
      if (fieldIndex >= 0) {
        rows.sort((a, b) => {
          const aVal = parseFloat(a[fieldIndex]) || 0;
          const bVal = parseFloat(b[fieldIndex]) || 0;
          return orderBy.direction === "DESC" ? bVal - aVal : aVal - bVal;
        });
      }
    }

    // Apply limit if specified
    const limitedRows = requestBody.limit
      ? rows.slice(0, requestBody.limit)
      : rows;

    console.log(
      "[DataQueryMockV2] Generated network response with",
      limitedRows.length,
      "rows",
    );

    return { fields, rows: limitedRows };
  }

  /**
   * Generate network error breakdown response (groups by status_code and error_type)
   * Used by ErrorBreakdown component
   */
  private generateNetworkErrorBreakdownResponse(
    requestBody: DataQueryRequestBody,
  ): DataQueryResponse {
    const { select, filters, groupBy } = requestBody;

    // Extract fields
    const fields: string[] = select.map((s) => s.alias);

    // Determine error type from SpanType filter (4xx or 5xx)
    const spanTypeFilter = filters?.find(
      (f) => f.field === "SpanType" && f.operator === "LIKE",
    );
    const filterValues = spanTypeFilter?.value
      ? Array.isArray(spanTypeFilter.value)
        ? spanTypeFilter.value.map((v) => String(v))
        : [String(spanTypeFilter.value)]
      : [];
    const is4xx = filterValues.some((v: string) => v.includes("network.4"));
    const is5xx = filterValues.some((v: string) => v.includes("network.5"));

    // Map status codes to realistic error types (following OpenTelemetry conventions)
    const statusCodeToErrorTypes: Record<number, string[]> = {
      400: ["bad_request", "validation_error", "invalid_request"],
      401: ["unauthorized", "authentication_failed", "invalid_credentials"],
      403: ["forbidden", "access_denied", "permission_denied"],
      404: ["not_found", "resource_not_found", "endpoint_not_found"],
      422: ["validation_error", "unprocessable_entity", "invalid_payload"],
      429: ["rate_limit_exceeded", "too_many_requests", "throttled"],
      500: ["internal_server_error", "server_error", "application_error"],
      502: ["bad_gateway", "gateway_error", "upstream_error"],
      503: ["service_unavailable", "unavailable", "maintenance"],
      504: ["gateway_timeout", "timeout", "upstream_timeout"],
      507: ["insufficient_storage", "storage_error"],
    };

    // Generate realistic status codes based on type
    const statusCodes = is4xx
      ? [404, 401, 403, 400, 422, 429] // 4xx errors
      : is5xx
        ? [500, 502, 503, 504, 507] // 5xx errors
        : [];

    // Generate error counts with realistic distribution
    // Most common errors should have higher counts
    const rows: string[][] = [];

    statusCodes.forEach((statusCode, statusIndex) => {
      const errorTypes = statusCodeToErrorTypes[statusCode] || ["_OTHER"];

      // If grouping by both status_code and error_type, create a row for each error type
      // Otherwise, create one row per status code with the most common error type
      const errorTypesToUse = groupBy?.includes("error_type")
        ? errorTypes
        : [errorTypes[0]]; // Use first (most common) error type if not grouping by error_type

      errorTypesToUse.forEach((errorType, errorIndex) => {
        const row: string[] = [];

        // Higher count for first few status codes (most common)
        const baseCount = is4xx
          ? [850, 620, 480, 320, 180, 95][statusIndex] || 50
          : [420, 280, 195, 120, 65][statusIndex] || 30;

        // Distribute count across error types if grouping by error_type
        const countMultiplier = groupBy?.includes("error_type")
          ? [0.5, 0.3, 0.2][errorIndex] || 0.1 // First error type gets 50%, second 30%, etc.
          : 1.0; // Use full count if not grouping by error_type

        // Add some variance
        const count = Math.round(
          baseCount * countMultiplier +
            Math.floor(Math.random() * baseCount * countMultiplier * 0.3),
        );

        select.forEach((selectField) => {
          const alias = selectField.alias;

          switch (alias) {
            case "error_count":
              row.push(count.toString());
              break;
            case "status_code":
              row.push(statusCode.toString());
              break;
            case "error_type":
              row.push(errorType);
              break;
            default:
              row.push(
                this.generateValueForFunction(
                  selectField.function,
                  null,
                  filters,
                  undefined,
                  selectField.param,
                ),
              );
          }
        });

        rows.push(row);
      });
    });

    // Apply orderBy if specified (should be by error_count DESC)
    if (requestBody.orderBy && requestBody.orderBy.length > 0) {
      const orderBy = requestBody.orderBy[0];
      const fieldIndex = fields.indexOf(orderBy.field);
      if (fieldIndex >= 0) {
        rows.sort((a, b) => {
          const aVal = parseFloat(a[fieldIndex]) || 0;
          const bVal = parseFloat(b[fieldIndex]) || 0;
          return orderBy.direction === "DESC" ? bVal - aVal : aVal - bVal;
        });
      }
    }

    // Apply limit if specified
    const limitedRows = requestBody.limit
      ? rows.slice(0, requestBody.limit)
      : rows;

    return { fields, rows: limitedRows };
  }

  /**
   * Generate network issues by provider response
   * Used by NetworkIssuesByProvider component
   */
  private generateNetworkIssuesByProviderResponse(
    requestBody: DataQueryRequestBody,
  ): DataQueryResponse {
    const { select, filters } = requestBody;

    // Extract fields
    const fields: string[] = select.map((s) => s.alias);

    // Determine error type from SpanType filter
    const spanTypeFilter = filters?.find(
      (f) =>
        f.field === "SpanType" &&
        (f.operator === "EQ" || f.operator === "LIKE"),
    );
    const filterValues = spanTypeFilter?.value
      ? Array.isArray(spanTypeFilter.value)
        ? spanTypeFilter.value.map((v) => String(v))
        : [String(spanTypeFilter.value)]
      : [];
    const isConnectionError = filterValues.some(
      (v: string) => v === "network.0",
    );
    const is4xx = filterValues.some((v: string) => v.includes("network.4"));
    const is5xx = filterValues.some((v: string) => v.includes("network.5"));

    // Indian network providers with realistic distribution
    const networkProviders = [
      "Jio",
      "Airtel",
      "Vi (Vodafone Idea)",
      "BSNL",
      "Aircel",
    ];

    // Generate rows for each network provider
    const rows: string[][] = networkProviders.map((provider, index) => {
      const row: string[] = [];

      // Generate realistic error counts based on provider and error type
      // Jio and Airtel typically have better networks, so fewer errors
      // Vi and BSNL might have more issues
      let baseCount: number;
      if (isConnectionError) {
        // Connection errors (network.0)
        baseCount = [15, 18, 35, 42, 28][index] || 20;
      } else if (is4xx) {
        // 4xx errors
        baseCount = [120, 145, 280, 320, 195][index] || 150;
      } else if (is5xx) {
        // 5xx errors
        baseCount = [45, 55, 95, 120, 75][index] || 60;
      } else {
        baseCount = 50;
      }

      // Add variance (±25%)
      const variance = Math.floor(baseCount * 0.25);
      const count =
        baseCount + Math.floor(Math.random() * variance * 2) - variance;

      select.forEach((selectField) => {
        const alias = selectField.alias;

        switch (alias) {
          case "conn_error":
          case "4xx":
          case "5xx":
            row.push(Math.max(0, count).toString());
            break;
          case "network_provider":
            row.push(provider);
            break;
          default:
            row.push(
              this.generateValueForFunction(
                selectField.function,
                null,
                filters,
                undefined,
                selectField.param,
              ),
            );
        }
      });

      return row;
    });

    // Filter out rows with zero errors
    const filteredRows = rows.filter((row) => {
      const errorIndex = fields.findIndex((f) =>
        ["conn_error", "4xx", "5xx"].includes(f),
      );
      if (errorIndex >= 0) {
        return parseFloat(row[errorIndex]) > 0;
      }
      return true;
    });

    // Apply orderBy if specified (should be by error count DESC)
    if (requestBody.orderBy && requestBody.orderBy.length > 0) {
      const orderBy = requestBody.orderBy[0];
      const fieldIndex = fields.indexOf(orderBy.field);
      if (fieldIndex >= 0) {
        filteredRows.sort((a, b) => {
          const aVal = parseFloat(a[fieldIndex]) || 0;
          const bVal = parseFloat(b[fieldIndex]) || 0;
          return orderBy.direction === "DESC" ? bVal - aVal : aVal - bVal;
        });
      }
    }

    // Apply limit if specified
    const limitedRows = requestBody.limit
      ? filteredRows.slice(0, requestBody.limit)
      : filteredRows;

    return { fields, rows: limitedRows };
  }

  /**
   * Generate exceptions list response (grouped by GroupId)
   */
  private generateExceptionsListResponse(
    requestBody: DataQueryRequestBody,
  ): DataQueryResponse {
    const { select, filters, limit } = requestBody;

    // Extract fields
    const fields: string[] = select.map((s) => s.alias);

    // Generate realistic GroupIds and error messages
    const numRows = limit && limit > 0 ? Math.min(limit, 100) : 100;
    const errorMessages = [
      "NullPointerException at MainActivity.onCreate",
      "OutOfMemoryError: Failed to allocate bitmap",
      "IllegalStateException: Fragment already added",
      "IndexOutOfBoundsException: Invalid array index",
      "ClassCastException: Cannot cast to View",
      "SQLiteException: database locked",
      "NetworkOnMainThreadException",
      "FileNotFoundException: File not found",
      "IllegalArgumentException: Invalid parameter",
      "RuntimeException: Unexpected error",
    ];

    const rows: string[][] = Array.from({ length: numRows }, (_, index) => {
      const row: string[] = [];
      const errorMessage =
        errorMessages[index % errorMessages.length] || `Error ${index + 1}`;
      const groupId = `group-${btoa(errorMessage).replace(/[+/=]/g, "").substring(0, 16)}-${index}`;
      const occurrences = Math.floor(Math.random() * 200) + 50; // 50-250
      const affectedUsers = Math.floor(occurrences * 0.7);
      const appVersions = ["2.4.0", "2.3.5", "2.3.0"].join(", ");
      const title = errorMessage.split(" at ")[0] || errorMessage;

      select.forEach((selectField) => {
        const alias = selectField.alias;
        switch (alias) {
          case "group_id":
            row.push(groupId);
            break;
          case "title":
            row.push(title);
            break;
          case "error_message":
            row.push(errorMessage);
            break;
          case "app_versions":
            row.push(appVersions);
            break;
          case "occurrences":
            row.push(occurrences.toString());
            break;
          case "affected_users":
            row.push(affectedUsers.toString());
            break;
          default:
            row.push(
              this.generateValueForFunction(
                selectField.function,
                null,
                filters,
                undefined,
                selectField.param,
              ),
            );
        }
      });

      return row;
    });

    return { fields, rows };
  }

  /**
   * Generate issue summary response (top 10 crashes for GroupId)
   */
  private generateIssueSummaryResponse(
    requestBody: DataQueryRequestBody,
  ): DataQueryResponse {
    const { select, filters, limit } = requestBody;

    // Extract fields
    const fields: string[] = select.map((s) => s.alias);

    // Get GroupId from filter
    const groupIdFilter = filters?.find(
      (f) => f.field === "GroupId" && f.operator === "EQ",
    );
    const groupId = groupIdFilter?.value
      ? Array.isArray(groupIdFilter.value)
        ? String(groupIdFilter.value[0])
        : String(groupIdFilter.value)
      : "";

    // Generate top 10 crashes for this GroupId
    // Each crash is a different ExceptionMessage/ExceptionType combination
    const numRows = limit && limit > 0 ? Math.min(limit, 10) : 10;
    const errorMessages = [
      "NullPointerException at MainActivity.onCreate",
      "OutOfMemoryError: Failed to allocate bitmap",
      "IllegalStateException: Fragment already added",
      "IndexOutOfBoundsException: Invalid array index",
      "ClassCastException: Cannot cast to View",
      "SQLiteException: database locked",
      "NetworkOnMainThreadException",
      "FileNotFoundException: File not found",
      "IllegalArgumentException: Invalid parameter",
      "RuntimeException: Unexpected error",
    ];
    const errorTypes = [
      "NullPointerException",
      "OutOfMemoryError",
      "IllegalStateException",
      "IndexOutOfBoundsException",
      "ClassCastException",
      "SQLiteException",
      "NetworkOnMainThreadException",
      "FileNotFoundException",
      "IllegalArgumentException",
      "RuntimeException",
    ];

    const rows: string[][] = Array.from({ length: numRows }, (_, index) => {
      const row: string[] = [];
      const errorMessage = errorMessages[index] || `Error ${index + 1}`;
      const errorType = errorTypes[index] || "Exception";
      const occurrences = Math.floor(Math.random() * 50) + 10; // 10-60 (decreasing)
      const affectedUsers = Math.floor(occurrences * 0.7);
      const appVersions = ["2.4.0", "2.3.5", "2.3.0"].join(", ");
      const title = errorMessage.split(" at ")[0] || errorMessage;

      select.forEach((selectField) => {
        const alias = selectField.alias;
        switch (alias) {
          case "group_id":
            row.push(groupId);
            break;
          case "title":
            row.push(title);
            break;
          case "error_message":
            row.push(errorMessage);
            break;
          case "error_type":
            row.push(errorType);
            break;
          case "app_versions":
            row.push(appVersions);
            break;
          case "occurrences":
            row.push(occurrences.toString());
            break;
          case "affected_users":
            row.push(affectedUsers.toString());
            break;
          default:
            row.push(
              this.generateValueForFunction(
                selectField.function,
                null,
                filters,
                undefined,
                selectField.param,
              ),
            );
        }
      });

      return row;
    });

    return { fields, rows };
  }

  /**
   * Generate stack traces response (individual exception entries for GroupId)
   */
  private generateIssueStackTracesResponse(
    requestBody: DataQueryRequestBody,
  ): DataQueryResponse {
    const { select, filters, limit } = requestBody;

    // Extract fields
    const fields: string[] = select.map((s) => s.alias);

    // Generate realistic stack trace
    const baseStackTrace = `at com.example.app.MainActivity.onCreate(MainActivity.java:45)
at android.app.Activity.performCreate(Activity.java:8051)
at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:3265)
at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:3409)
at android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:83)
at android.app.servertransaction.TransactionExecutor.executeCallbacks(TransactionExecutor.java:135)
at android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:95)
at android.app.ActivityThread$H.handleMessage(ActivityThread.java:2016)
at android.os.Handler.dispatchMessage(Handler.java:107)
at android.os.Looper.loop(Looper.java:214)
at android.app.ActivityThread.main(ActivityThread.java:7356)`;

    // Generate multiple occurrences
    const numRows = limit && limit > 0 ? Math.min(limit, 20) : 10;
    const devices = [
      "Samsung Galaxy S23",
      "Google Pixel 7",
      "OnePlus 9",
      "Xiaomi Redmi Note 12",
      "Realme 10 Pro",
    ];
    const osVersions = ["Android 13", "Android 12", "Android 11"];
    const appVersions = ["2.4.0", "2.3.5", "2.3.0"];

    const now = new Date();
    const rows: string[][] = Array.from({ length: numRows }, (_, index) => {
      const row: string[] = [];
      const timestamp = new Date(
        now.getTime() - (numRows - index) * 2 * 60 * 60 * 1000,
      ).toISOString(); // Spread over last few hours
      const traceId = `trace-${Date.now()}-${index}`;
      const spanId = `span-${Date.now()}-${index}`
        .padEnd(16, "0")
        .substring(0, 16);

      select.forEach((selectField) => {
        const alias = selectField.alias;
        switch (alias) {
          case "trace_id":
            row.push(traceId);
            break;
          case "span_id":
            row.push(spanId);
            break;
          case "timestamp":
            row.push(timestamp);
            break;
          case "device":
            row.push(devices[index % devices.length]);
            break;
          case "os_version":
            row.push(osVersions[index % osVersions.length]);
            break;
          case "app_version":
            row.push(appVersions[index % appVersions.length]);
            break;
          case "stacktrace":
            row.push(baseStackTrace);
            break;
          case "error_message":
            row.push("NullPointerException at MainActivity.onCreate");
            break;
          case "error_type":
            row.push("NullPointerException");
            break;
          default:
            row.push(
              this.generateValueForFunction(
                selectField.function,
                null,
                filters,
                undefined,
                selectField.param,
              ),
            );
        }
      });

      return row;
    });

    return { fields, rows };
  }

  /**
   * Generate screen breakdown response (grouped by screen_name for GroupId)
   */
  private generateIssueScreenBreakdownResponse(
    requestBody: DataQueryRequestBody,
  ): DataQueryResponse {
    const { select, filters } = requestBody;

    // Extract fields
    const fields: string[] = select.map((s) => s.alias);

    // Generate realistic screen breakdown
    const screens = [
      { name: "MainActivity", percentage: 35 },
      { name: "ProfileScreen", percentage: 25 },
      { name: "SettingsActivity", percentage: 18 },
      { name: "CheckoutScreen", percentage: 12 },
      { name: "LoginActivity", percentage: 10 },
    ];

    // Estimate total occurrences (will be used to calculate individual counts)
    const totalOccurrences = 150;

    const rows: string[][] = screens.map((screen) => {
      const row: string[] = [];
      const occurrences = Math.floor(
        (totalOccurrences * screen.percentage) / 100,
      );

      select.forEach((selectField) => {
        const alias = selectField.alias;
        switch (alias) {
          case "screen_name":
            row.push(screen.name);
            break;
          case "occurrences":
            row.push(occurrences.toString());
            break;
          default:
            row.push(
              this.generateValueForFunction(
                selectField.function,
                null,
                filters,
                undefined,
                selectField.param,
              ),
            );
        }
      });

      return row;
    });

    return { fields, rows };
  }

  /**
   * Generate exception timestamps response (for firstSeen/lastSeen queries)
   * Groups by GroupId and returns min/max timestamps
   */
  private generateExceptionTimestampsResponse(
    requestBody: DataQueryRequestBody,
  ): DataQueryResponse {
    const { select, filters } = requestBody;

    // Extract fields
    const fields: string[] = select.map((s) => s.alias);

    // Get GroupIds from IN filter
    const groupIdFilter = filters?.find(
      (f) => f.field === "GroupId" && f.operator === "IN",
    );
    const groupIds = groupIdFilter?.value
      ? Array.isArray(groupIdFilter.value)
        ? groupIdFilter.value.map((v) => String(v))
        : [String(groupIdFilter.value)]
      : [];

    // Calculate 6 months ago
    const now = new Date();
    const sixMonthsAgo = new Date(now.getTime() - 6 * 30 * 24 * 60 * 60 * 1000);

    // Generate rows for each GroupId
    const rows: string[][] = groupIds.map((groupId) => {
      const row: string[] = [];

      // Generate realistic timestamps (spread over 6 months)
      const daysAgo = Math.floor(Math.random() * 180); // 0-180 days
      const firstSeen = new Date(
        sixMonthsAgo.getTime() + daysAgo * 24 * 60 * 60 * 1000,
      ).toISOString();
      const lastSeen = new Date(
        now.getTime() - Math.floor(Math.random() * 7) * 24 * 60 * 60 * 1000,
      ).toISOString(); // Within last 7 days

      select.forEach((selectField) => {
        const alias = selectField.alias;
        switch (alias) {
          case "group_id":
            row.push(groupId);
            break;
          case "first_seen":
            row.push(firstSeen);
            break;
          case "last_seen":
            row.push(lastSeen);
            break;
          default:
            row.push(
              this.generateValueForFunction(
                selectField.function,
                null,
                filters,
                undefined,
                selectField.param,
              ),
            );
        }
      });

      return row;
    });

    return { fields, rows };
  }
}

/**
 * Main export function
 */
export function generateDataQueryMockResponseV2(
  requestBody: DataQueryRequestBody,
): DataQueryResponse {
  const generator = DataQueryMockGeneratorV2.getInstance();
  return generator.generateResponse(requestBody);
}
