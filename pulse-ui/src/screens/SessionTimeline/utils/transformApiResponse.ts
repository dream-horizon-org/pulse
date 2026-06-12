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
 * Transform API response from useGetDataQuery to SessionSummary and SessionTimelineEvent[]
 */
export function transformApiResponse(
  apiData: ApiResponse | null | undefined,
  traceId: string,
): {
  summary: SessionSummary;
  events: SessionTimelineEvent[];
} {
  if (!apiData || !apiData.rows || apiData.rows.length === 0) {
    return {
      summary: {
        sessionId: traceId,
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
  const spanIdIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "spanid",
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
  const errorIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "error",
  );
  const errorTypeIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "error_type" || f.toLowerCase() === "error.type",
  );
  const errorMessageIndex = apiData.fields.findIndex(
    (f) =>
      f.toLowerCase() === "error_message" ||
      f.toLowerCase() === "error.message",
  );
  const frozenFrameIndex = apiData.fields.findIndex(
    (f) =>
      f.toLowerCase() === "frozen_frame" || f.toLowerCase() === "frozenframe",
  );
  const anrIndex = apiData.fields.findIndex((f) => f.toLowerCase() === "anr");
  const crashIndex = apiData.fields.findIndex(
    (f) => f.toLowerCase() === "crash",
  );

  const firstRow = apiData.rows[0];
  if (!firstRow) {
    return {
      summary: {
        sessionId: traceId,
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
      resourceAttrs["geo.state"] = stateValue;
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
    const spanId = spanIdIndex !== -1 ? String(row[spanIdIndex] || "") : "";
    const spanName =
      spanNameIndex !== -1 ? String(row[spanNameIndex] || "") : "Unknown";
    const timestampStr =
      timestampIndex !== -1 ? String(row[timestampIndex] || "") : "";
    const durationStr =
      durationIndex !== -1 ? String(row[durationIndex] || "") : "0";
    const error =
      errorIndex !== -1 ? String(row[errorIndex] || "").toLowerCase() : "";
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
    if (error === "true" || errorType) {
      if (errorType.toLowerCase().includes("crash")) {
        eventType = "crash";
        crashes++;
      } else if (errorType.toLowerCase().includes("anr")) {
        eventType = "anr";
        anrs++;
      } else {
        eventType = "log";
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

    // Build attributes
    const attributes: Record<string, any> = {
      resource: resourceAttrs,
      span: {},
    };

    if (errorType) {
      attributes.span["error.type"] = errorType;
    }
    if (errorMessage) {
      attributes.span["error.message"] = errorMessage;
    }

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

  // Determine status (use ANR/CRASH function values if available, otherwise use counted values)
  const status: SessionSummary["status"] =
    crashes > 0 ? "crashed" : sessionDuration > 0 ? "completed" : "active";

  const summary: SessionSummary = {
    sessionId: traceId,
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
