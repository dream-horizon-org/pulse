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
import { PulseType } from "../../constants/PulseOtelSemcov";

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
      dataType: requestBody.dataType,
      filters: filters?.map((f) => ({ field: f.field, operator: f.operator })),
    });

    // Check if this is a time-series query (has TIME_BUCKET)
    const hasTimeBucket = select.some((s) => s.function === "TIME_BUCKET");

    // Check if this is grouped by other fields
    const hasGroupBy = groupBy && groupBy.length > 0 && !groupBy.includes("t1");

    // Check if this is a session records query (has event_names, traceid, spanid)
    // Use case-insensitive matching for aliases
    const hasEventNames = select.some((s) => s.alias === "event_names");
    const hasTraceId = select.some((s) => s.alias?.toLowerCase() === "traceid");
    const hasSpanId = select.some((s) => s.alias?.toLowerCase() === "spanid");

    // Check if this is a Session Timeline query (has SessionId filter)
    const sessionIdFilter = filters?.find(
      (f) => f.field === "SessionId" && f.operator === "EQ",
    );
    const isSessionTimelineQuery = !!sessionIdFilter && !hasGroupBy && !hasTimeBucket;

    // Check if this is a Span/Log Details query (TraceId + SpanId filter with toJSONString attributes)
    const traceIdFilter = filters?.find(
      (f) => f.field === "TraceId" && f.operator === "EQ",
    );
    const spanIdFilter = filters?.find(
      (f) => f.field === "SpanId" && f.operator === "EQ",
    );
    const hasJsonAttributes = select.some(
      (s) => s.function === "CUSTOM" && 
        s.param?.expression?.includes("toJSONString")
    );
    const isSpanDetailsQuery = !!traceIdFilter && !!spanIdFilter && hasJsonAttributes && limit === 1;
    const isLogDetailsQuery = requestBody.dataType === "LOGS" && !!traceIdFilter && hasJsonAttributes && limit === 1;

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
    } else if (isSpanDetailsQuery) {
      // Span Details query - return detailed attributes for a specific span
      const response = this.generateSpanDetailsResponse(requestBody);
      console.log(
        "[DataQueryMockV2] Generated span details response:",
        response.rows.length,
        "rows",
      );
      return response;
    } else if (isLogDetailsQuery) {
      // Log Details query - return detailed attributes for a specific log
      const response = this.generateLogDetailsResponse(requestBody);
      console.log(
        "[DataQueryMockV2] Generated log details response:",
        response.rows.length,
        "rows",
      );
      return response;
    } else if (isSessionTimelineQuery) {
      // Session Timeline query - return traces/logs/exceptions for a specific session
      const response = this.generateSessionTimelineResponse(requestBody);
      console.log(
        "[DataQueryMockV2] Generated session timeline response:",
        response.rows.length,
        "rows for dataType:",
        requestBody.dataType,
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
      // Check if this is a network query (has network filter)
      const hasNetworkFilter = filters?.some(
        (f) => {
          const matches = f.field === "PulseType" &&
            f.operator === "LIKE" &&
            Array.isArray(f.value) &&
            f.value
              .map((v) => String(v).toLowerCase())
              .some((v: string) => v.includes("network"));
          if (f.field === "PulseType") {
            console.log("[DataQueryMockV2] PulseType filter check:", {
              field: f.field,
              operator: f.operator,
              value: f.value,
              matches,
            });
          }
          return matches;
        },
      );

      // Check if this is a network ERROR BREAKDOWN query (groups by status_code/error_type)
      // This must be checked FIRST because it also has method/url filters
      const isNetworkErrorBreakdownQuery =
        hasNetworkFilter &&
        (groupBy?.includes("status_code") || groupBy?.includes("error_type")) &&
        !groupBy?.includes("method") &&
        !groupBy?.includes("url");

      // Check if this is a network DETAIL query (has specific method and url filters, groups by method/url)
      const methodFilter = filters?.find(
        (f) => f.field === "SpanAttributes['http.method']" && f.operator === "EQ",
      );
      const urlFilter = filters?.find(
        (f) => f.field === "SpanAttributes['http.url']" && f.operator === "EQ",
      );
      const isNetworkDetailQuery =
        hasNetworkFilter &&
        methodFilter &&
        urlFilter &&
        groupBy?.includes("method") &&
        groupBy?.includes("url");

      // Check if this is a network LIST query (groups by method and url, no specific filters)
      const isNetworkListQuery =
        hasNetworkFilter &&
        groupBy?.includes("method") &&
        groupBy?.includes("url") &&
        !isNetworkDetailQuery; // Exclude detail queries

      console.log("[DataQueryMockV2] Network detection:", {
        hasNetworkFilter,
        methodFilter: !!methodFilter,
        urlFilter: !!urlFilter,
        isNetworkErrorBreakdownQuery,
        isNetworkDetailQuery,
        isNetworkListQuery,
        groupBy,
      });

      // Handle network error breakdown query FIRST (has method/url filters but groups by status_code)
      if (isNetworkErrorBreakdownQuery) {
        const response = this.generateNetworkErrorBreakdownResponse(requestBody);
        console.log(
          "[DataQueryMockV2] Generated network error breakdown response:",
          response.rows.length,
          "rows",
        );
        return response;
      }

      if (isNetworkDetailQuery) {
        const response = this.generateNetworkResponse(requestBody, true);
        console.log(
          "[DataQueryMockV2] Generated network detail response:",
          response.rows.length,
          "rows",
          "fields:",
          response.fields,
          "first row sample:",
          response.rows[0]?.slice(0, 5),
        );
        return response;
      }

      if (isNetworkListQuery) {
        const response = this.generateNetworkResponse(requestBody, false);
        console.log(
          "[DataQueryMockV2] Generated network list response:",
          response.rows.length,
          "rows",
        );
        return response;
      }

      // Check if this is a network issues by provider query (groups by network_provider)
      const isNetworkIssuesByProvider =
        groupBy?.includes("network_provider") &&
        filters?.some(
          (f) =>
            f.field === "PulseType" &&
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

      if (isNetworkIssuesByProvider) {
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
    // Filter value is "%network%" (SQL LIKE pattern) so we check for "network" substring
    const isNetworkQuery =
      filters?.some(
        (f) =>
          f.field === "PulseType" &&
          f.operator === "LIKE" &&
          Array.isArray(f.value) &&
          f.value
            .map((v) => String(v).toLowerCase())
            .some((v: string) => v.includes("network")),
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
   * Generate Session Timeline response for traces, logs, or exceptions filtered by SessionId
   * This handles the waterfall/flame chart view for a single session
   */
  private generateSessionTimelineResponse(
    requestBody: DataQueryRequestBody,
  ): DataQueryResponse {
    const { select, filters, dataType } = requestBody;

    // Extract SessionId from filter
    const sessionIdFilter = filters?.find(
      (f) => f.field === "SessionId" && f.operator === "EQ",
    );
    const sessionId = Array.isArray(sessionIdFilter?.value)
      ? String(sessionIdFilter.value[0])
      : String(sessionIdFilter?.value || "mock-session-123");

    console.log("[DataQueryMockV2] Generating session timeline response:", {
      sessionId,
      dataType,
      fieldCount: select.length,
    });

    // Extract fields (aliases)
    const fields: string[] = select.map((s) => s.alias);

    // Use a fixed time window for better visualization (last 5 minutes of activity)
    const now = dayjs().utc();
    const startTime = now.subtract(5, "minute");
    const sessionDurationMs = 5 * 60 * 1000; // 5 minutes

    const rows: string[][] = [];

    if (dataType === "TRACES") {
      // Generate trace spans for session timeline - create a realistic span hierarchy
      const traceSpans = this.generateSessionTraceSpans(sessionId, startTime, sessionDurationMs);
      
      console.log("[DataQueryMockV2] Generated", traceSpans.length, "trace spans");
      
      for (const span of traceSpans) {
        const row: string[] = select.map((selectField) => {
          const alias = selectField.alias?.toLowerCase() || selectField.alias;
          
          switch (alias) {
            case "traceid":
              return span.traceId;
            case "spanid":
              return span.spanId;
            case "parentspanid":
              return span.parentSpanId;
            case "spanname":
              return span.spanName;
            case "spankind":
              return span.spanKind;
            case "servicename":
              return span.serviceName;
            case "timestamp":
              return span.timestamp;
            case "duration":
              return span.duration.toString();
            case "statuscode":
              return span.statusCode;
            case "pulsetype":
              return span.pulseType;
            default:
              return "";
          }
        });
        rows.push(row);
      }
    } else if (dataType === "LOGS") {
      // Generate more logs for the session - 25-40 logs
      const numLogs = 25 + Math.floor(Math.random() * 15);
      
      const logBodies = [
        "Application initialized",
        "User session started",
        "Fetching user preferences from cache",
        "Cache hit: user_preferences",
        "Loading HomeScreen",
        "HomeScreen rendered successfully",
        "Network request: GET /api/v1/user/profile",
        "Response received: 200 OK (234ms)",
        "Updating UI with profile data",
        "User tapped: 'Settings' button",
        "Navigating to SettingsScreen",
        "SettingsScreen loaded",
        "Fetching notification settings",
        "Push token registered",
        "Analytics event: screen_view (SettingsScreen)",
        "User changed theme preference",
        "Saving preferences to local storage",
        "Database write completed (12ms)",
        "Background sync initiated",
        "Syncing 3 pending items",
        "Sync completed successfully",
        "Memory usage: 128MB (normal)",
        "Location permission requested",
        "Location permission granted",
        "GPS coordinates received",
        "Map tiles loaded (8 tiles)",
        "Image loaded: profile_photo.jpg",
        "Image cached successfully",
        "Checking for app updates",
        "App is up to date",
        "Session heartbeat sent",
        "WebSocket connection established",
        "Real-time updates enabled",
        "User returned to HomeScreen",
        "Refreshing home feed",
        "Feed updated with 5 new items",
      ];

      const severities = ["INFO", "DEBUG", "INFO", "DEBUG", "INFO", "WARN", "INFO", "DEBUG"];

      for (let i = 0; i < numLogs; i++) {
        const progress = i / (numLogs - 1);
        const logTime = startTime.add(progress * sessionDurationMs * 0.95, "millisecond");
        
        const row: string[] = select.map((selectField) => {
          const alias = selectField.alias?.toLowerCase() || selectField.alias;
          
          switch (alias) {
            case "traceid":
              return `${sessionId}-trace-${Math.floor(i / 3)}`;
            case "spanid":
              return this.generateHexString(16).toUpperCase();
            case "timestamp":
              return logTime.toISOString();
            case "severitytext":
              return severities[i % severities.length];
            case "body":
              return logBodies[i % logBodies.length];
            case "pulsetype":
              return "log";
            default:
              return "";
          }
        });
        rows.push(row);
      }
    } else if (dataType === "EXCEPTIONS") {
      // Always generate 1-2 exceptions for demo visualization
      const numExceptions = 1 + Math.floor(Math.random() * 2);
      
      const exceptionDetails = [
        { type: "non_fatal", title: "NetworkTimeoutException", message: "Connection timed out after 30000ms", screen: "HomeScreen" },
        { type: "crash", title: "NullPointerException", message: "Cannot invoke method 'getString()' on null object reference", screen: "ProfileScreen" },
        { type: "anr", title: "Application Not Responding", message: "Input dispatching timed out (Waiting to send non-key event)", screen: "CheckoutScreen" },
        { type: "non_fatal", title: "OutOfMemoryError", message: "Failed to allocate 4194304 bytes", screen: "ImageGalleryScreen" },
      ];

      for (let i = 0; i < numExceptions; i++) {
        const progress = 0.3 + (i * 0.3); // Space exceptions throughout session
        const exceptionTime = startTime.add(progress * sessionDurationMs, "millisecond");
        const exception = exceptionDetails[i % exceptionDetails.length];
        
        const row: string[] = select.map((selectField) => {
          const alias = selectField.alias?.toLowerCase() || selectField.alias;
          
          switch (alias) {
            case "timestamp":
              return exceptionTime.toISOString();
            case "pulsetype":
              return `device.${exception.type}`;
            case "title":
              return exception.title;
            case "exceptionmessage":
              return exception.message;
            case "exceptiontype":
              return exception.type;
            case "screenname":
              return exception.screen;
            case "traceid":
              return `${sessionId}-exception-trace-${i}`;
            case "spanid":
              return this.generateHexString(16).toUpperCase();
            case "groupid":
              return `group-${exception.type}-${Date.now()}-${i}`;
            default:
              return "";
          }
        });
        rows.push(row);
      }
    }

    console.log("[DataQueryMockV2] Session timeline generated:", {
      dataType,
      rowCount: rows.length,
      fields,
    });

    return { fields, rows };
  }

  /**
   * Generate realistic trace spans for a session with proper parent-child hierarchy
   * Creates a rich hierarchy for flame chart visualization
   */
  private generateSessionTraceSpans(
    sessionId: string,
    startTime: dayjs.Dayjs,
    sessionDurationMs: number,
  ): Array<{
    traceId: string;
    spanId: string;
    parentSpanId: string;
    spanName: string;
    spanKind: string;
    serviceName: string;
    timestamp: string;
    duration: number;
    statusCode: string;
    pulseType: string;
  }> {
    const spans: Array<{
      traceId: string;
      spanId: string;
      parentSpanId: string;
      spanName: string;
      spanKind: string;
      serviceName: string;
      timestamp: string;
      duration: number;
      statusCode: string;
      pulseType: string;
    }> = [];

    // Screen names for realistic navigation flow
    const screens = ["HomeScreen", "ProfileScreen", "SettingsScreen", "SearchScreen", "DetailsScreen", "CheckoutScreen"];
    
    // Define rich interaction templates with nested operations
    const interactionTemplates = [
      {
        name: "AppStart",
        pulseType: PulseType.APP_START,
        baseDuration: 2500,
        children: [
          { name: "SDK Initialization", type: "internal", duration: 150, children: [
            { name: "Config Load", type: "internal", duration: 30 },
            { name: "Analytics Init", type: "internal", duration: 50 },
            { name: "Crash Reporter Init", type: "internal", duration: 40 },
          ]},
          { name: "Database Migration", type: "database", duration: 200, children: [
            { name: "Schema Check", type: "database", duration: 50 },
            { name: "Run Migrations", type: "database", duration: 120 },
          ]},
          { name: "User Session Restore", type: "internal", duration: 300, children: [
            { name: "Token Validation", type: "network", duration: 180 },
            { name: "Profile Fetch", type: "network", duration: 100 },
          ]},
          { name: "Initial Screen Render", type: "render", duration: 400, children: [
            { name: "Layout Inflation", type: "render", duration: 80 },
            { name: "View Binding", type: "render", duration: 60 },
            { name: "Data Binding", type: "render", duration: 100 },
          ]},
        ],
      },
      {
        name: "ScreenLoad",
        pulseType: PulseType.SCREEN_LOAD,
        baseDuration: 800,
        screenName: true,
        children: [
          { name: "View Creation", type: "render", duration: 100 },
          { name: "Data Fetch", type: "network", duration: 350, children: [
            { name: "API Request", type: "network", duration: 280 },
            { name: "Response Parse", type: "internal", duration: 50 },
          ]},
          { name: "Render Content", type: "render", duration: 200, children: [
            { name: "RecyclerView Bind", type: "render", duration: 120 },
            { name: "Image Decode", type: "internal", duration: 60 },
          ]},
        ],
      },
      {
        name: "UserInteraction",
        pulseType: PulseType.INTERACTION,
        baseDuration: 400,
        children: [
          { name: "Click Handler", type: "internal", duration: 20 },
          { name: "State Update", type: "internal", duration: 50 },
          { name: "API Call", type: "network", duration: 250, children: [
            { name: "Request Prepare", type: "internal", duration: 15 },
            { name: "Network Request", type: "network", duration: 200 },
            { name: "Response Handle", type: "internal", duration: 30 },
          ]},
          { name: "UI Update", type: "render", duration: 80 },
        ],
      },
      {
        name: "Navigation",
        pulseType: PulseType.NAVIGATION,
        baseDuration: 600,
        children: [
          { name: "Route Resolve", type: "internal", duration: 30 },
          { name: "Screen Transition", type: "render", duration: 250, children: [
            { name: "Exit Animation", type: "render", duration: 100 },
            { name: "Enter Animation", type: "render", duration: 120 },
          ]},
          { name: "New Screen Init", type: "internal", duration: 150 },
          { name: "Data Prefetch", type: "network", duration: 170 },
        ],
      },
      {
        name: "NetworkRequest",
        pulseType: "network.request",
        baseDuration: 500,
        children: [
          { name: "DNS Lookup", type: "network", duration: 20 },
          { name: "TCP Connect", type: "network", duration: 40 },
          { name: "TLS Handshake", type: "network", duration: 60 },
          { name: "Request Send", type: "network", duration: 30 },
          { name: "Server Processing", type: "network", duration: 200 },
          { name: "Response Receive", type: "network", duration: 100 },
          { name: "Body Parse", type: "internal", duration: 50 },
        ],
      },
    ];

    // Generate 8-12 traces distributed across the session
    const numTraces = 8 + Math.floor(Math.random() * 5);
    const timeSlotSize = sessionDurationMs / numTraces;
    
    for (let t = 0; t < numTraces; t++) {
      const traceId = this.generateHexString(32).toUpperCase();
      const template = interactionTemplates[t % interactionTemplates.length];
      
      // Calculate trace start time with some randomness within the slot
      const slotStart = t * timeSlotSize;
      const randomOffset = Math.random() * (timeSlotSize * 0.3);
      const traceStartTime = startTime.add(slotStart + randomOffset, "millisecond");
      
      // Duration variation: 70-130% of base
      const durationMultiplier = 0.7 + Math.random() * 0.6;
      const traceDuration = Math.floor(template.baseDuration * durationMultiplier);
      
      // Determine span name (add screen name for screen-related spans)
      let spanName = template.name;
      if (template.screenName) {
        spanName = `${screens[t % screens.length]} Load`;
      }
      
      // Create root span
      const rootSpanId = this.generateHexString(16).toUpperCase();
      spans.push({
        traceId,
        spanId: rootSpanId,
        parentSpanId: "",
        spanName,
        spanKind: "INTERNAL",
        serviceName: "pulse-mobile-app",
        timestamp: traceStartTime.toISOString(),
        duration: traceDuration * 1_000_000, // Convert ms to nanoseconds
        statusCode: Math.random() < 0.95 ? "OK" : "ERROR",
        pulseType: template.pulseType,
      });
      
      // Create child spans recursively
      let childOffset = 5; // Start 5ms after parent
      
      for (const child of template.children) {
        const childDuration = Math.floor(child.duration * durationMultiplier);
        this.addSpanWithChildren(
          spans,
          traceId,
          rootSpanId,
          child,
          traceStartTime.add(childOffset, "millisecond"),
          durationMultiplier,
        );
        childOffset += childDuration + 5;
      }
    }

    // Sort spans by timestamp for proper visualization
    spans.sort((a, b) => dayjs(a.timestamp).valueOf() - dayjs(b.timestamp).valueOf());

    return spans;
  }

  /**
   * Recursively add a span and its children
   */
  private addSpanWithChildren(
    spans: Array<{
      traceId: string;
      spanId: string;
      parentSpanId: string;
      spanName: string;
      spanKind: string;
      serviceName: string;
      timestamp: string;
      duration: number;
      statusCode: string;
      pulseType: string;
    }>,
    traceId: string,
    parentSpanId: string,
    spanDef: { name: string; type: string; duration: number; children?: Array<{ name: string; type: string; duration: number; children?: any[] }> },
    startTime: dayjs.Dayjs,
    durationMultiplier: number,
  ): void {
    const spanId = this.generateHexString(16).toUpperCase();
    const duration = Math.floor(spanDef.duration * durationMultiplier);
    
    // Map type to spanKind
    const spanKindMap: Record<string, string> = {
      "internal": "INTERNAL",
      "network": "CLIENT",
      "database": "CLIENT",
      "render": "INTERNAL",
      "cache": "INTERNAL",
    };
    
    // Map type to pulseType
    const pulseTypeMap: Record<string, string> = {
      "internal": "internal",
      "network": "network.request",
      "database": "database.query",
      "render": "ui.render",
      "cache": "cache.operation",
    };
    
    spans.push({
      traceId,
      spanId,
      parentSpanId,
      spanName: spanDef.name,
      spanKind: spanKindMap[spanDef.type] || "INTERNAL",
      serviceName: "pulse-mobile-app",
      timestamp: startTime.toISOString(),
      duration: duration * 1_000_000, // Convert ms to nanoseconds
      statusCode: Math.random() < 0.92 ? "OK" : "ERROR",
      pulseType: pulseTypeMap[spanDef.type] || "internal",
    });
    
    // Add children if present
    if (spanDef.children && spanDef.children.length > 0) {
      let childOffset = 3; // Small offset for child spans
      for (const child of spanDef.children) {
        const childDuration = Math.floor(child.duration * durationMultiplier);
        this.addSpanWithChildren(
          spans,
          traceId,
          spanId,
          child,
          startTime.add(childOffset, "millisecond"),
          durationMultiplier,
        );
        childOffset += childDuration + 2;
      }
    }
  }

  /**
   * Generate Span Details response with rich attribute data
   * Returns resourceAttributes, spanAttributes, events, and links as JSON strings
   */
  private generateSpanDetailsResponse(
    requestBody: DataQueryRequestBody,
  ): DataQueryResponse {
    const { select } = requestBody;

    // Extract fields (aliases)
    const fields: string[] = select.map((s) => s.alias);

    // Generate rich mock data for span details
    const resourceAttributes = {
      "service.name": "pulse-mobile-app",
      "service.version": "3.2.1",
      "telemetry.sdk.name": "pulse-otel-sdk",
      "telemetry.sdk.version": "1.4.0",
      "device.id": "ABC123XYZ789",
      "device.model.name": "iPhone 14 Pro",
      "device.manufacturer": "Apple",
      "os.type": "iOS",
      "os.version": "17.2",
      "os.name": "iOS",
      "deployment.environment": "production",
      "host.arch": "arm64",
      "process.runtime.name": "iOS",
      "process.runtime.version": "17.2",
    };

    const spanAttributes = {
      "pulse.type": "screen_load",
      "pulse.screen.name": "HomeScreen",
      "pulse.session.id": "session-abc-123",
      "http.method": "GET",
      "http.url": "https://api.example.com/v1/user/profile",
      "http.status_code": "200",
      "http.response_content_length": "2458",
      "http.request_content_length": "0",
      "net.peer.name": "api.example.com",
      "net.peer.port": "443",
      "user_agent.original": "PulseApp/3.2.1 (iOS 17.2; iPhone14,2)",
      "thread.id": "1",
      "thread.name": "main",
      "code.function": "loadHomeScreen",
      "code.filepath": "HomeViewController.swift",
      "code.lineno": "142",
      "enduser.id": "user-12345",
      "app.screen.previous": "SplashScreen",
      "app.navigation.type": "push",
    };

    // Generate some events
    const now = dayjs().utc();
    const events = [
      {
        timestamp: now.subtract(100, "millisecond").toISOString(),
        name: "view.created",
        attributes: { "view.class": "HomeViewController", "view.id": "home_root" },
      },
      {
        timestamp: now.subtract(50, "millisecond").toISOString(),
        name: "data.loaded",
        attributes: { "data.source": "cache", "data.items": "12" },
      },
      {
        timestamp: now.toISOString(),
        name: "view.rendered",
        attributes: { "view.frame_time": "16.7ms", "view.dropped_frames": "0" },
      },
    ];

    // Generate some links
    const links = [
      {
        traceId: this.generateHexString(32).toUpperCase(),
        spanId: this.generateHexString(16).toUpperCase(),
        attributes: { "link.type": "parent_request", "link.source": "api_gateway" },
      },
    ];

    // Build the row based on select fields
    const row: string[] = select.map((selectField) => {
      const alias = selectField.alias?.toLowerCase() || selectField.alias;
      
      switch (alias) {
        case "resourceattributes":
          return JSON.stringify(resourceAttributes);
        case "spanattributes":
          return JSON.stringify(spanAttributes);
        case "eventstimestamp":
          return events.map((e) => e.timestamp).join("|||");
        case "eventsname":
          return events.map((e) => e.name).join("|||");
        case "eventsattributes":
          return events.map((e) => JSON.stringify(e.attributes)).join("|||");
        case "linkstraceid":
          return links.map((l) => l.traceId).join("|||");
        case "linksspanid":
          return links.map((l) => l.spanId).join("|||");
        case "linksattributes":
          return links.map((l) => JSON.stringify(l.attributes)).join("|||");
        default:
          return "";
      }
    });

    return { fields, rows: [row] };
  }

  /**
   * Generate Log Details response with rich attribute data
   * Returns resourceAttributes, logAttributes, scopeAttributes, body, severityText, severityNumber
   */
  private generateLogDetailsResponse(
    requestBody: DataQueryRequestBody,
  ): DataQueryResponse {
    const { select } = requestBody;

    // Extract fields (aliases)
    const fields: string[] = select.map((s) => s.alias);

    // Generate rich mock data for log details
    const resourceAttributes = {
      "service.name": "pulse-mobile-app",
      "service.version": "3.2.1",
      "telemetry.sdk.name": "pulse-otel-sdk",
      "telemetry.sdk.version": "1.4.0",
      "device.id": "ABC123XYZ789",
      "device.model.name": "iPhone 14 Pro",
      "device.manufacturer": "Apple",
      "os.type": "iOS",
      "os.version": "17.2",
      "deployment.environment": "production",
    };

    const logAttributes = {
      "log.file.name": "AppLogger.swift",
      "log.file.path": "/Sources/Logging/AppLogger.swift",
      "log.record.uid": "log-rec-" + this.generateHexString(8),
      "code.function": "logEvent",
      "code.lineno": "87",
      "thread.id": "1",
      "thread.name": "main",
      "event.domain": "app",
      "event.name": "user_action",
      "user.id": "user-12345",
      "screen.name": "HomeScreen",
      "session.id": "session-abc-123",
      "app.state": "foreground",
      "network.type": "wifi",
    };

    const scopeAttributes = {
      "scope.name": "com.pulse.app.logger",
      "scope.version": "1.0.0",
    };

    const logBodies = [
      "User completed profile update successfully",
      "Network request completed: GET /api/v1/user/profile (200 OK)",
      "Screen transition: HomeScreen -> ProfileScreen",
      "Cache hit for user preferences data",
      "Background sync completed with 3 items",
    ];

    const severities = ["INFO", "DEBUG", "WARN"];
    const severityNumbers = [9, 5, 13]; // INFO=9, DEBUG=5, WARN=13

    const randomIndex = Math.floor(Math.random() * logBodies.length);

    // Build the row based on select fields
    const row: string[] = select.map((selectField) => {
      const alias = selectField.alias?.toLowerCase() || selectField.alias;
      
      switch (alias) {
        case "resourceattributes":
          return JSON.stringify(resourceAttributes);
        case "logattributes":
          return JSON.stringify(logAttributes);
        case "scopeattributes":
          return JSON.stringify(scopeAttributes);
        case "body":
          return logBodies[randomIndex];
        case "severitytext":
          return severities[randomIndex % severities.length];
        case "severitynumber":
          return severityNumbers[randomIndex % severityNumbers.length].toString();
        default:
          return "";
      }
    });

    return { fields, rows: [row] };
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
        // Return latency in milliseconds (backend query already divides Duration by 1e6)
        // P50: 100-400ms
        return this.randomLatency(100, 400, interactionName, groupValue).toString();

      case "DURATION_P95":
        // Return latency in milliseconds (backend query already divides Duration by 1e6)
        // P95: 400-1200ms
        return this.randomLatency(400, 1200, interactionName, groupValue).toString();

      case "DURATION_P99":
        // Return latency in milliseconds (backend query already divides Duration by 1e6)
        // P99: 800-2500ms
        return this.randomLatency(800, 2500, interactionName, groupValue).toString();

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

        // Handle sumIf(Duration, PulseType = 'screen_session') - total time spent on screen
        if (
          expression.includes("sumIf(Duration") &&
          expression.includes("screen_session")
        ) {
          // Generate realistic total time spent (in nanoseconds)
          // Hook calculates: avgTimeSpent = totalTimeSpent / screenCount (in ns)
          // Component then converts: nanoseconds / 1,000,000 = milliseconds
          // Target: 30-180 seconds avg per session
          // 30s = 30,000,000,000 ns, 180s = 180,000,000,000 ns
          // With ~100 sessions avg: total = avg * 100
          // 30s * 100 = 3 trillion, 180s * 100 = 18 trillion
          const totalNs = this.randomCount(
            3000000000000, // 3 trillion ns = 30s avg * 100 sessions
            18000000000000, // 18 trillion ns = 180s avg * 100 sessions
            interactionName,
            groupValue,
          );
          return totalNs.toString();
        }

        // Handle sumIf(Duration, PulseType = 'screen_load') - total screen load time
        if (
          expression.includes("sumIf(Duration") &&
          expression.includes("screen_load")
        ) {
          // Generate realistic total load time (in nanoseconds)
          // Hook calculates: avgLoadTime = totalLoadTime / screenCount (in ns)
          // Component then converts: nanoseconds / 1,000,000 = milliseconds
          // Target: 300ms - 3s avg per load
          // 300ms = 300,000,000 ns, 3s = 3,000,000,000 ns
          // With ~100 loads avg: total = avg * 100
          // 300ms * 100 = 30B, 3s * 100 = 300B
          const totalNs = this.randomCount(
            30000000000, // 30 billion ns = 300ms avg * 100 loads
            300000000000, // 300 billion ns = 3s avg * 100 loads
            interactionName,
            groupValue,
          );
          return totalNs.toString();
        }

        // Handle countIf(PulseType = 'screen_session') - session count
        if (
          expression.includes("countIf") &&
          expression.includes("screen_session")
        ) {
          // Session count should align with total_time_spent
          // total_time_spent = 3-18 trillion ns (based on ~100 sessions)
          // avg = total / count → 30-180 seconds
          return this.randomCount(
            80,
            120,
            interactionName,
            groupValue,
          ).toString();
        }

        // Handle countIf(PulseType = 'screen_load') - load count
        if (
          expression.includes("countIf") &&
          expression.includes("screen_load")
        ) {
          // Load count should align with total_load_time
          // total_load_time = 30-300 billion ns (based on ~100 loads)
          // avg = total / count → 300ms-3s
          return this.randomCount(
            80,
            120,
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
              // Screen counts should be ~100 to align with time totals
              // total_time_spent / screen_count = avg time per session
              // E.g., 10 trillion / 100 = 100 billion ns = 100 seconds avg
              return this.randomCount(
                80,
                120,
                interactionName,
                groupValue,
              ).toString();
            } else {
              // Interactions get moderate counts
              return this.randomCount(
                200,
                400,
                interactionName,
                groupValue,
              ).toString();
            }
          }
        }

        // Handle uniqCombinedIf for crash metrics (CrashMetricsStats component)
        // These must come BEFORE uniqCombined handlers since they are more specific
        if (expression.includes("uniqCombinedIf") && expression.includes("device.crash")) {
          if (expression.includes("UserId")) {
            // Crash users: 1-2% of total users (80-240 out of 8000-12000)
            // This gives ~98-99% crash-free users rate
            return this.randomCount(
              80,
              240,
              "crash_users",
              groupValue,
            ).toString();
          }
          if (expression.includes("SessionId")) {
            // Crash sessions: 0.5-1% of total sessions (125-500 out of 25000-50000)
            // This gives ~99-99.5% crash-free sessions rate
            return this.randomCount(
              125,
              500,
              "crash_sessions",
              groupValue,
            ).toString();
          }
        }

        // Handle uniqCombined(UserId) - total unique users (for crash metrics)
        if (expression.includes("uniqCombined(UserId)") && !expression.includes("If")) {
          // Total unique users: 8000-12000
          // When used with crash metrics, this aligns with crash_users for ~98-99% crash-free rate
          return this.randomCount(
            8000,
            12000,
            "all_users",
            groupValue,
          ).toString();
        }

        // Handle uniqCombined(SessionId) - total sessions (for crash metrics)
        if (expression.includes("uniqCombined(SessionId)") && !expression.includes("If")) {
          // Total sessions: 25000-50000
          // When used with crash metrics, this aligns with crash_sessions for ~99-99.5% crash-free rate
          return this.randomCount(
            25000,
            50000,
            "all_sessions",
            groupValue,
          ).toString();
        }

        // Handle countIf(StatusCode = 'ERROR') - error count for screens
        if (expression.includes("countIf") && expression.includes("StatusCode")) {
          // Realistic error count: 0-5% crash rate of ~100 sessions = 0-5 errors
          return this.randomCount(
            0,
            5,
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
    if (normalizedField.includes(`spanattributes['${PulseType.SCREEN_NAME}']`)) {
      normalizedField = "screen_name";
    }
    if (normalizedField.includes("spanattributes['http.url']")) {
      normalizedField = "url";
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
        "JoinContestButtonClick",
        "SaveTeamButtonClick",
        "PlayerSelectTap",
        "ContestListAPIFetch",
        "PaymentSubmitClick",
        "WalletBalanceFetch",
        "MatchScheduleAPICall",
        "LeaderboardRefreshTap",
        "ProfileSaveClick",
        "NotificationTap",
        "FilterApplyTap",
        "LiveScoreRefresh",
      ],
      interactionname: [
        "JoinContestButtonClick",
        "SaveTeamButtonClick",
        "PlayerSelectTap",
        "ContestListAPIFetch",
        "PaymentSubmitClick",
        "WalletBalanceFetch",
        "MatchScheduleAPICall",
        "LeaderboardRefreshTap",
        "ProfileSaveClick",
        "NotificationTap",
        "FilterApplyTap",
        "LiveScoreRefresh",
      ],
      interaction_name: [
        "JoinContestButtonClick",
        "SaveTeamButtonClick",
        "PlayerSelectTap",
        "ContestListAPIFetch",
        "PaymentSubmitClick",
        "WalletBalanceFetch",
        "MatchScheduleAPICall",
        "LeaderboardRefreshTap",
        "ProfileSaveClick",
        "NotificationTap",
        "FilterApplyTap",
        "LiveScoreRefresh",
      ],
      screen_name: [
        "ContestHomeScreen",
        "TeamCreationScreen",
        "MatchListScreen",
        "ContestDetailsScreen",
        "LeaderboardScreen",
        "AddCashScreen",
        "WalletScreen",
        "ProfileScreen",
        "MyContestsScreen",
        "LiveScoreScreen",
        "PlayerStatsScreen",
        "WithdrawScreen",
        "TransactionHistoryScreen",
        "SettingsScreen",
        "NotificationsScreen",
        "ProductDetailScreen",
        "CheckoutFormScreen",
        "PaymentScreen",
        "ProfileScreen",
        "SearchResultsScreen",
        "OrderListScreen",
      ],
      url: [
        "https://api.example.com/v1/users",
        "https://api.example.com/v1/products",
        "https://api.example.com/v1/orders",
        "https://api.example.com/v1/payments",
        "https://api.example.com/v1/auth/login",
        "https://api.example.com/v1/cart",
        "https://api.example.com/v1/search",
        "https://api.example.com/v1/notifications",
        "https://api.example.com/v1/analytics",
        "https://api.example.com/v1/profile",
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
    // Ensure we never go below the minimum value
    return Math.max(min, base + variance);
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

    // Fantasy sports specific network APIs
    const networkApis = [
      { method: "GET", url: "/api/v1/contests/live" },
      { method: "GET", url: "/api/v1/contests/upcoming" },
      { method: "POST", url: "/api/v1/contests/join" },
      { method: "GET", url: "/api/v1/teams/my-teams" },
      { method: "POST", url: "/api/v1/teams/create" },
      { method: "PUT", url: "/api/v1/teams/update" },
      { method: "GET", url: "/api/v1/players/list" },
      { method: "GET", url: "/api/v1/players/stats" },
      { method: "GET", url: "/api/v1/matches/live-score" },
      { method: "GET", url: "/api/v1/matches/schedule" },
      { method: "GET", url: "/api/v1/user/profile" },
      { method: "GET", url: "/api/v1/user/wallet" },
      { method: "POST", url: "/api/v1/wallet/deposit" },
      { method: "POST", url: "/api/v1/wallet/withdraw" },
      { method: "GET", url: "/api/v1/leaderboard/global" },
      { method: "GET", url: "/api/v1/leaderboard/contest" },
      { method: "GET", url: "/api/v1/notifications/list" },
      { method: "POST", url: "/api/v1/auth/refresh" },
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

      console.log("[DataQueryMockV2] Network detail query:", {
        targetMethod,
        targetUrl,
        methodFilter: methodFilter?.value,
        urlFilter: urlFilter?.value,
      });

      filteredApis = networkApis.filter(
        (api) => api.method === targetMethod && api.url === targetUrl,
      );

      // If not found, create a single entry with the requested method and url
      if (filteredApis.length === 0 && targetMethod && targetUrl) {
        console.log("[DataQueryMockV2] Creating fallback API entry:", targetMethod, targetUrl);
        filteredApis = [{ method: targetMethod, url: targetUrl }];
      }

      console.log("[DataQueryMockV2] Filtered APIs count:", filteredApis.length);
    }

    // For list query, we might need to filter by screen name if provided
    if (!isDetailQuery) {
      const screenNameFilter = filters?.find(
        (f) =>
          f.field === `SpanAttributes['${PulseType.SCREEN_NAME}']` && f.operator === "EQ",
      );
      if (screenNameFilter) {
        // In a real scenario, we'd filter by screen name, but for mock we'll return all
        // The screen name filter is handled by the backend in real queries
      }
    }

    // For demo purposes, show aggregated data per API (not split by status code)
    // This gives realistic success rates like 96-99%

    // Generate rows - one per API with aggregated metrics
    const rows: string[][] = filteredApis.map((api) => {
      const row: string[] = [];

      // Pre-calculate values ONCE per API to ensure consistency
      const apiSeed = api.method + api.url;
      const baseRequestCount =
        api.method === "GET"
          ? this.randomCount(5000, 80000, apiSeed)
          : this.randomCount(1000, 25000, apiSeed);
      
      // Success rate: 96-99.5% (realistic for production APIs)
      const successRate = 0.96 + (this.hashString(apiSeed) % 35) / 1000;
      const successCount = Math.floor(baseRequestCount * successRate);
      
      // Response time in milliseconds
      const responseTimeMs =
        api.method === "GET"
          ? this.randomLatency(80, 400, apiSeed)
          : this.randomLatency(150, 800, apiSeed);
      
      // Sessions: 15-40% of requests
      const sessionRate = 0.15 + (this.hashString(apiSeed + "session") % 25) / 100;
      const sessionCount = Math.floor(baseRequestCount * sessionRate);

      console.log("[DataQueryMockV2] Generating row for API:", {
        method: api.method,
        url: api.url,
        responseTimeMs,
        baseRequestCount,
      });

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
            row.push(baseRequestCount.toString());
            break;
          case "success_requests":
            row.push(successCount.toString());
            break;
          case "response_time":
            // Convert ms to nanoseconds for Duration field
            const avgTimeNs = responseTimeMs * 1_000_000;
            row.push(avgTimeNs.toString());
            break;
          case "all_sessions":
            row.push(Math.max(1, sessionCount).toString());
            break;
          case "p50":
            // P50 latency in milliseconds (backend already converts Duration/1e6)
            // P50 is typically 50-70% of average response time
            const p50Ms = Math.floor(responseTimeMs * (0.5 + (this.hashString(apiSeed + "p50") % 20) / 100));
            row.push(p50Ms.toString());
            break;
          case "p95":
            // P95 latency in milliseconds (backend already converts Duration/1e6)
            // P95 is typically 180-280% of average response time
            const p95Ms = Math.floor(responseTimeMs * (1.8 + (this.hashString(apiSeed + "p95") % 100) / 100));
            row.push(p95Ms.toString());
            break;
          case "p99":
            // P99 latency in milliseconds (backend already converts Duration/1e6)
            // P99 is typically 250-450% of average response time
            const p99Ms = Math.floor(responseTimeMs * (2.5 + (this.hashString(apiSeed + "p99") % 200) / 100));
            row.push(p99Ms.toString());
            break;
          case "status_code":
            // For aggregated view, show "200" as the primary status code
            row.push("200");
            break;
          case "screen_name":
            // Return the screen name if filtering by it, otherwise empty
            const screenFilter = filters?.find(
              (f) => f.field?.includes("screen") && f.operator === "EQ",
            );
            let screenValue = "";
            if (screenFilter?.value) {
              if (Array.isArray(screenFilter.value)) {
                screenValue = String(screenFilter.value[0] || "");
              } else {
                screenValue = String(screenFilter.value);
              }
            }
            row.push(screenValue);
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

    // Determine error type from PulseType filter (4xx or 5xx)
    const spanTypeFilter = filters?.find(
      (f) => f.field === "PulseType" && f.operator === "LIKE",
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

    // Determine error type from PulseType filter
    const spanTypeFilter = filters?.find(
      (f) =>
        f.field === "PulseType" &&
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

      // Add variance (±25%) - ensure count is never negative
      const variance = Math.floor(baseCount * 0.25);
      const randomVariance = Math.floor(Math.random() * variance * 2) - variance;
      const count = Math.max(0, baseCount + randomVariance);

      select.forEach((selectField) => {
        const alias = selectField.alias;

        switch (alias) {
          case "conn_error":
          case "4xx":
          case "5xx":
            row.push(count.toString());
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

    // Generate realistic GroupIds and error messages (fantasy sports app context)
    const numRows = limit && limit > 0 ? Math.min(limit, 100) : 100;
    const errorMessages = [
      "NullPointerException at ContestListFragment.onViewCreated",
      "OutOfMemoryError: Failed to allocate bitmap in TeamCreationActivity",
      "IllegalStateException: Fragment already added in MatchDetailsFragment",
      "IndexOutOfBoundsException: Invalid player list index in TeamSelectionAdapter",
      "ClassCastException: Cannot cast ContestModel to ContestDetailModel",
      "SQLiteException: database locked while saving team data",
      "NetworkOnMainThreadException in PaymentGatewayService",
      "FileNotFoundException: Player image resource not found",
      "IllegalArgumentException: Invalid contest ID in JoinContestHandler",
      "RuntimeException: Unexpected error in LiveScoreRefreshHandler",
      "TimeoutException: Contest API request timed out",
      "JSONException: Malformed response from leaderboard endpoint",
      "IllegalStateException: Cannot join contest - already started",
      "OutOfMemoryError: Loading too many match images in MatchListFragment",
      "NullPointerException: User session expired in ProfileActivity",
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
      "NullPointerException at ContestListFragment.onViewCreated",
      "OutOfMemoryError: Failed to allocate bitmap in TeamCreationActivity",
      "IllegalStateException: Fragment already added in MatchDetailsFragment",
      "IndexOutOfBoundsException: Invalid player list index",
      "ClassCastException: Cannot cast ContestModel",
      "SQLiteException: database locked while saving team",
      "NetworkOnMainThreadException in PaymentGatewayService",
      "FileNotFoundException: Player image resource",
      "IllegalArgumentException: Invalid contest ID",
      "RuntimeException: Unexpected error in LiveScoreRefreshHandler",
    ];
    const errorTypes = [
      "NullPointerException",
      "OutOfMemoryError",
      "IllegalStateException",
      "IndexOutOfBoundsException",
      "NetworkOnMainThreadException",
      "TimeoutException",
      "JSONException",
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
