import {
  SessionTimelineEvent,
  SessionSummary,
} from "../SessionTimeline.interface";
import dayjs from "dayjs";

interface ApiResponse {
  fields: string[];
  rows: (string | number | null)[][];
}

/**
 * Parse Events data from comma-separated strings
 */
function parseEvents(
  eventsTimestampStr: string | null | undefined,
  eventsNameStr: string | null | undefined,
  eventsAttributesStr: string | null | undefined,
): Array<{ timestamp: number; name: string; attributes?: Record<string, any> }> {
  if (!eventsTimestampStr || !eventsNameStr) return [];

  const timestamps = eventsTimestampStr.split(",").filter(Boolean);
  const names = eventsNameStr.split(",").filter(Boolean);
  const attributesArray = eventsAttributesStr
    ? eventsAttributesStr.split("|").filter(Boolean)
    : [];

  return timestamps.map((ts, index) => {
    const parsed = dayjs(ts.trim());
    const eventAttributes: Record<string, any> = {};
    let hasAttributes = false;
    
    // Parse attributes if available (ClickHouse Map toString format: {'key1':'value1','key2':'value2'})
    if (attributesArray[index]) {
      try {
        const mapStr = attributesArray[index].trim();
        // ClickHouse Map toString format: {'key':'value','key2':'value2'}
        if (mapStr.startsWith("{") && mapStr.endsWith("}")) {
          // Remove outer braces
          const content = mapStr.slice(1, -1);
          // Split by comma, but be careful with commas inside values
          const pairs = content.split(/','/);
          pairs.forEach((pair, i) => {
            // Handle first and last pairs differently
            let cleanPair = pair;
            if (i === 0) {
              cleanPair = pair.replace(/^'/, "");
            }
            if (i === pairs.length - 1) {
              cleanPair = cleanPair.replace(/'$/, "");
            }
            
            const colonIndex = cleanPair.indexOf("':");
            if (colonIndex > 0) {
              const key = cleanPair.substring(0, colonIndex).replace(/^'/, "").replace(/'$/, "");
              const value = cleanPair.substring(colonIndex + 2).replace(/^'/, "").replace(/'$/, "");
              if (key) {
                eventAttributes[key] = value;
                hasAttributes = true;
              }
            }
          });
        }
      } catch (e) {
        // If parsing fails, skip attributes
        console.warn("Failed to parse event attributes:", e);
      }
    }

    const result: { timestamp: number; name: string; attributes?: Record<string, any> } = {
      timestamp: parsed.isValid() ? parsed.valueOf() : 0,
      name: names[index]?.trim() || "",
    };
    
    if (hasAttributes) {
      result.attributes = eventAttributes;
    }
    
    return result;
  });
}

/**
 * Parse Links data from comma-separated strings
 */
function parseLinks(
  linksTraceIdStr: string | null | undefined,
  linksSpanIdStr: string | null | undefined,
  linksTraceStateStr: string | null | undefined,
  linksAttributesStr: string | null | undefined,
): Array<{
  traceId: string;
  spanId: string;
  traceState?: string;
  attributes?: Record<string, any>;
}> {
  if (!linksTraceIdStr || !linksSpanIdStr) return [];

  const traceIds = linksTraceIdStr.split(",").filter(Boolean);
  const spanIds = linksSpanIdStr.split(",").filter(Boolean);
  const traceStates = linksTraceStateStr
    ? linksTraceStateStr.split(",").filter(Boolean)
    : [];
  const attributesArray = linksAttributesStr
    ? linksAttributesStr.split("|").filter(Boolean)
    : [];

  return traceIds.map((traceId, index) => {
    const linkAttributes: Record<string, any> = {};
    let hasAttributes = false;
    
    // Parse attributes if available (ClickHouse Map toString format: {'key1':'value1','key2':'value2'})
    if (attributesArray[index]) {
      try {
        const mapStr = attributesArray[index].trim();
        // ClickHouse Map toString format: {'key':'value','key2':'value2'}
        if (mapStr.startsWith("{") && mapStr.endsWith("}")) {
          // Remove outer braces
          const content = mapStr.slice(1, -1);
          // Split by comma, but be careful with commas inside values
          const pairs = content.split(/','/);
          pairs.forEach((pair, i) => {
            // Handle first and last pairs differently
            let cleanPair = pair;
            if (i === 0) {
              cleanPair = pair.replace(/^'/, "");
            }
            if (i === pairs.length - 1) {
              cleanPair = cleanPair.replace(/'$/, "");
            }
            
            const colonIndex = cleanPair.indexOf("':");
            if (colonIndex > 0) {
              const key = cleanPair.substring(0, colonIndex).replace(/^'/, "").replace(/'$/, "");
              const value = cleanPair.substring(colonIndex + 2).replace(/^'/, "").replace(/'$/, "");
              if (key) {
                linkAttributes[key] = value;
                hasAttributes = true;
              }
            }
          });
        }
      } catch (e) {
        // If parsing fails, skip attributes
        console.warn("Failed to parse link attributes:", e);
      }
    }

    const result: {
      traceId: string;
      spanId: string;
      traceState?: string;
      attributes?: Record<string, any>;
    } = {
      traceId: traceId.trim(),
      spanId: spanIds[index]?.trim() || "",
    };
    
    if (traceStates[index]?.trim()) {
      result.traceState = traceStates[index]?.trim();
    }
    
    if (hasAttributes) {
      result.attributes = linkAttributes;
    }
    
    return result;
  });
}

/**
 * Transform TRACES API response to SessionTimelineEvent[]
 */
export function transformTracesResponse(
  apiData: ApiResponse | null | undefined,
  sessionId: string,
): {
  summary: SessionSummary;
  events: SessionTimelineEvent[];
} {
  if (!apiData || !apiData.rows || apiData.rows.length === 0) {
    return {
      summary: {
        sessionId,
        platform: "unknown",
        status: "completed",
        duration: 0,
        crashes: 0,
        anrs: 0,
        frozenFrames: 0,
        totalEvents: 0,
      },
      events: [],
    };
  }

  // Find field indices
  const traceIdIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "traceid",
  );
  const spanIdIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "spanid",
  );
  const parentSpanIdIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "parentspanid",
  );
  const spanNameIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "spanname",
  );
  const timestampIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "timestamp",
  );
  const durationIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "duration",
  );
  const deviceIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "device" || f.toLowerCase() === "devicemodel",
  );
  const osVersionIndex = apiData.fields.findIndex(
    (f) =>
      f.toLowerCase() === "os_version" ||
      f.toLowerCase().includes("os.version"),
  );
  const osNameIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "os_name" || f.toLowerCase().includes("os.name"),
  );
  const stateIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "state",
  );
  const statusCodeIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "statuscode",
  );
  const statusMessageIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "statusmessage",
  );
  const frozenFrameIndex = apiData.fields.findIndex(
    (f) =>
      f.toLowerCase() === "frozen_frame" || f.toLowerCase() === "frozenframe",
  );
  const anrIndex = apiData.fields.findIndex((f) => f.toLowerCase() === "anr");
  const crashIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "crash",
  );
  const eventsTimestampIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "events_timestamp",
  );
  const eventsNameIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "events_name",
  );
  const linksTraceIdIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "links_traceid",
  );
  const linksSpanIdIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "links_spanid",
  );
  const linksTraceStateIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "links_tracestate",
  );
  const eventsAttributesIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "events_attributes",
  );
  const linksAttributesIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "links_attributes",
  );
  const appBuildIdIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "app_build_id",
  );
  const sdkVersionIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "sdk_version",
  );
  const geoCountryIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "geo_country",
  );
  const networkProviderIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "network_provider",
  );
  const userIdIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "user_id",
  );
  const errorTypeIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "error_type",
  );
  const errorMessageIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "error_message",
  );

  const firstRow = apiData.rows[0];
  if (!firstRow) {
    return {
      summary: {
        sessionId,
        platform: "unknown",
        status: "completed",
        duration: 0,
        crashes: 0,
        anrs: 0,
        frozenFrames: 0,
        totalEvents: 0,
      },
      events: [],
    };
  }

  // Extract resource attributes from first row (should be consistent across all spans)
  const resourceAttrs: Record<string, string> = {};
  if (deviceIndex !== -1 && firstRow[deviceIndex]) {
    const deviceValue = String(firstRow[deviceIndex]).trim();
    if (deviceValue) {
      resourceAttrs["device.model"] = deviceValue;
    }
  }
  if (osVersionIndex !== -1 && firstRow[osVersionIndex]) {
    const osVersionValue = String(firstRow[osVersionIndex]).trim();
    if (osVersionValue) {
      resourceAttrs["os.version"] = osVersionValue;
    }
  }
  if (osNameIndex !== -1 && firstRow[osNameIndex]) {
    const osNameValue = String(firstRow[osNameIndex]).trim();
    if (osNameValue) {
      resourceAttrs["os.name"] = osNameValue;
    }
  }
  if (stateIndex !== -1 && firstRow[stateIndex]) {
    const stateValue = String(firstRow[stateIndex]).trim();
    if (stateValue) {
      resourceAttrs["geo.region.iso_code"] = stateValue;
    }
  }
  if (appBuildIdIndex !== -1 && firstRow[appBuildIdIndex]) {
    const appBuildIdValue = String(firstRow[appBuildIdIndex]).trim();
    if (appBuildIdValue) {
      resourceAttrs["app.build_id"] = appBuildIdValue;
    }
  }
  if (sdkVersionIndex !== -1 && firstRow[sdkVersionIndex]) {
    const sdkVersionValue = String(firstRow[sdkVersionIndex]).trim();
    if (sdkVersionValue) {
      resourceAttrs["rum.sdk.version"] = sdkVersionValue;
    }
  }
  if (geoCountryIndex !== -1 && firstRow[geoCountryIndex]) {
    const geoCountryValue = String(firstRow[geoCountryIndex]).trim();
    if (geoCountryValue) {
      resourceAttrs["geo.country.iso_code"] = geoCountryValue;
    }
  }
  if (networkProviderIndex !== -1 && firstRow[networkProviderIndex]) {
    const networkProviderValue = String(firstRow[networkProviderIndex]).trim();
    if (networkProviderValue) {
      resourceAttrs["network.carrier.name"] = networkProviderValue;
    }
  }

  // Determine platform from OS name
  const osName = resourceAttrs["os.name"]?.toLowerCase() || "";
  const platform =
    osName.includes("android") || osName === "androidfull"
      ? "android"
      : osName.includes("ios")
        ? "ios"
        : osName.includes("web")
          ? "web"
          : "unknown";

  // Extract spanName from first row (interaction name)
  const firstSpanName =
    spanNameIndex !== -1 && firstRow[spanNameIndex]
      ? String(firstRow[spanNameIndex]).trim()
      : undefined;

  // Transform rows to events
  const events: SessionTimelineEvent[] = [];
  let sessionStartTime: number | null = null;
  let maxTimestamp = 0;
  let crashes = 0;
  let anrs = 0;
  let frozenFrames = 0;

  apiData.rows.forEach((row, index) => {
    // Extract ANR and CRASH values from each row and sum them
    if (
      crashIndex !== -1 &&
      row[crashIndex] !== null &&
      row[crashIndex] !== undefined
    ) {
      crashes += parseFloat(String(row[crashIndex] || "0")) || 0;
    }
    if (
      anrIndex !== -1 &&
      row[anrIndex] !== null &&
      row[anrIndex] !== undefined
    ) {
      anrs += parseFloat(String(row[anrIndex] || "0")) || 0;
    }

    const traceId =
      traceIdIndex !== -1 ? String(row[traceIdIndex] || "") : "";
    const spanId = spanIdIndex !== -1 ? String(row[spanIdIndex] || "") : "";
    const parentSpanId =
      parentSpanIdIndex !== -1 ? String(row[parentSpanIdIndex] || "") : "";
    const spanName =
      spanNameIndex !== -1 ? String(row[spanNameIndex] || "") : "Unknown";
    const timestampStr =
      timestampIndex !== -1 ? String(row[timestampIndex] || "") : "";
    const durationStr =
      durationIndex !== -1 ? String(row[durationIndex] || "") : "0";
    const statusCode =
      statusCodeIndex !== -1 ? String(row[statusCodeIndex] || "") : "";
    const statusMessage =
      statusMessageIndex !== -1 ? String(row[statusMessageIndex] || "") : "";
    const errorType =
      errorTypeIndex !== -1 ? String(row[errorTypeIndex] || "") : "";
    const errorMessage =
      errorMessageIndex !== -1 ? String(row[errorMessageIndex] || "") : "";
    const frozenFrame =
      frozenFrameIndex !== -1
        ? parseFloat(String(row[frozenFrameIndex] || "0"))
        : 0;

    // Parse timestamp
    let absoluteTimestamp = 0;
    if (timestampStr) {
      const parsed = dayjs(timestampStr);
      if (parsed.isValid()) {
        absoluteTimestamp = parsed.valueOf();
      }
    }

    // Set session start time from first span
    if (sessionStartTime === null && absoluteTimestamp > 0) {
      sessionStartTime = absoluteTimestamp;
    }

    // Calculate relative timestamp
    const relativeTimestamp = sessionStartTime
      ? absoluteTimestamp - sessionStartTime
      : 0;

    // Update max timestamp
    if (absoluteTimestamp > maxTimestamp) {
      maxTimestamp = absoluteTimestamp;
    }

    // Parse duration (assuming nanoseconds, convert to milliseconds)
    const durationNs = parseFloat(durationStr) || 0;
    const durationMs = durationNs / 1000000; // Convert nanoseconds to milliseconds

    // Determine event type based on span name and error fields
    let eventType: SessionTimelineEvent["type"] = "span";
    if (statusCode === "Error" || errorType) {
      if (errorType.toLowerCase().includes("crash")) {
        eventType = "crash";
        crashes++;
      } else if (errorType.toLowerCase().includes("anr")) {
        eventType = "anr";
        anrs++;
      } else {
        eventType = "span";
      }
    } else if (frozenFrame > 0) {
      eventType = "frozen_frame";
      frozenFrames++;
    } else if (
      spanName.toLowerCase().includes("trace") ||
      spanName.toLowerCase().includes("request")
    ) {
      eventType = "trace";
    } else {
      eventType = "span";
    }

    // Parse Events and Links
    const eventsData = parseEvents(
      eventsTimestampIndex !== -1
        ? String(row[eventsTimestampIndex] || "")
        : null,
      eventsNameIndex !== -1 ? String(row[eventsNameIndex] || "") : null,
      eventsAttributesIndex !== -1
        ? String(row[eventsAttributesIndex] || "")
        : null,
    );

    const linksData = parseLinks(
      linksTraceIdIndex !== -1
        ? String(row[linksTraceIdIndex] || "")
        : null,
      linksSpanIdIndex !== -1 ? String(row[linksSpanIdIndex] || "") : null,
      linksTraceStateIndex !== -1
        ? String(row[linksTraceStateIndex] || "")
        : null,
      linksAttributesIndex !== -1
        ? String(row[linksAttributesIndex] || "")
        : null,
    );

    // Build attributes
    const spanAttrs: Record<string, any> = {};
    if (statusCode) {
      spanAttrs["status.code"] = statusCode;
    }
    if (statusMessage) {
      spanAttrs["status.message"] = statusMessage;
    }
    if (errorType) {
      spanAttrs["error.type"] = errorType;
    }
    if (errorMessage) {
      spanAttrs["error.message"] = errorMessage;
    }
    if (userIdIndex !== -1 && row[userIdIndex]) {
      spanAttrs["user.id"] = String(row[userIdIndex]);
    }
    if (frozenFrame > 0) {
      spanAttrs["app.interaction.frozen_frame_count"] = frozenFrame;
    }

    const attributes: Record<string, any> = {
      resource: resourceAttrs,
      span: spanAttrs,
      events: eventsData,
      links: linksData,
      traceId,
      parentSpanId: parentSpanId || undefined,
    };

    events.push({
      id: spanId || `span-${index}`,
      name: spanName,
      type: eventType,
      timestamp: Math.max(0, relativeTimestamp),
      absoluteTimestamp: absoluteTimestamp > 0 ? absoluteTimestamp : undefined,
      duration: durationMs > 0 ? durationMs : undefined,
      attributes,
    });
  });

  // Calculate session duration
  const sessionDuration = sessionStartTime
    ? maxTimestamp - sessionStartTime
    : 0;

  // Determine status
  const status: SessionSummary["status"] =
    crashes > 0 ? "crashed" : sessionDuration > 0 ? "completed" : "active";

  const summary: SessionSummary = {
    sessionId,
    platform,
    status,
    duration: sessionDuration,
    crashes,
    anrs,
    frozenFrames,
    totalEvents: events.length,
    spanName: firstSpanName,
  };

  // Sort events by timestamp
  events.sort((a, b) => a.timestamp - b.timestamp);

  return { summary, events };
}

