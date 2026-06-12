/**
 * Row parsers for converting API response rows to typed objects
 */

import { RawSpanRow, RawLogRow, RawExceptionRow } from "./types";

/**
 * Helper to safely get a field value from a row by field name
 */
function createFieldGetter(fields: string[], row: (string | number | null)[]) {
  return (name: string): string | number | null => {
    const index = fields.findIndex((f) => f.toLowerCase() === name.toLowerCase());
    return index >= 0 ? row[index] : null;
  };
}

/**
 * Parse a trace/span row from API response
 * 
 * @param fields - Array of field names from API response
 * @param row - Array of values
 * @returns Typed RawSpanRow object
 */
export function parseTraceRow(
  fields: string[],
  row: (string | number | null)[]
): RawSpanRow {
  const getField = createFieldGetter(fields, row);

  return {
    traceId: String(getField("traceid") || ""),
    spanId: String(getField("spanid") || ""),
    parentSpanId: String(getField("parentspanid") || ""),
    spanName: String(getField("spanname") || ""),
    spanKind: String(getField("spankind") || ""),
    serviceName: String(getField("servicename") || ""),
    timestamp: String(getField("timestamp") || ""),
    duration: Number(getField("duration") || 0),
    statusCode: String(getField("statuscode") || ""),
    statusMessage: String(getField("statusmessage") || ""),
    pulseType: String(getField("pulsetype") || ""),
  };
}

/**
 * Parse a log row from API response
 * 
 * @param fields - Array of field names from API response
 * @param row - Array of values
 * @returns Typed RawLogRow object
 */
export function parseLogRow(
  fields: string[],
  row: (string | number | null)[]
): RawLogRow {
  const getField = createFieldGetter(fields, row);

  return {
    traceId: String(getField("traceid") || ""),
    spanId: String(getField("spanid") || ""),
    timestamp: String(getField("timestamp") || ""),
    severityText: String(getField("severitytext") || ""),
    severityNumber: Number(getField("severitynumber") || 0),
    body: String(getField("body") || ""),
    eventName: String(getField("eventname") || ""),
    pulseType: String(getField("pulsetype") || ""),
    serviceName: String(getField("servicename") || ""),
    scopeName: String(getField("scopename") || ""),
    logAttributesJson: String(getField("logattributesjson") || "{}"),
    resourceAttributesJson: String(getField("resourceattributesjson") || "{}"),
  };
}

/**
 * Parse an exception row from API response
 * 
 * @param fields - Array of field names from API response
 * @param row - Array of values
 * @returns Typed RawExceptionRow object
 */
export function parseExceptionRow(
  fields: string[],
  row: (string | number | null)[]
): RawExceptionRow {
  const getField = createFieldGetter(fields, row);

  return {
    timestamp: String(getField("timestamp") || ""),
    eventName: String(getField("eventname") || ""),
    title: String(getField("title") || ""),
    exceptionMessage: String(getField("exceptionmessage") || ""),
    exceptionType: String(getField("exceptiontype") || ""),
    screenName: String(getField("screenname") || ""),
    traceId: String(getField("traceid") || ""),
    spanId: String(getField("spanid") || ""),
    groupId: String(getField("groupid") || ""),
    pulseType: String(getField("pulsetype") || ""),
  };
}

/**
 * Parse JSON string to object safely
 * 
 * @param jsonString - JSON string to parse
 * @returns Parsed object or empty object on error
 */
export function parseJsonSafe<T = Record<string, unknown>>(
  jsonString: string | undefined | null
): T | null {
  if (!jsonString) return null;
  try {
    return JSON.parse(jsonString) as T;
  } catch {
    return null;
  }
}

/**
 * Check if a span ID represents an empty/null parent
 * Handles ClickHouse FixedString(16) empty values
 * 
 * @param spanId - The span ID to check
 * @returns True if the span ID is empty
 */
export function isEmptySpanId(spanId: string): boolean {
  if (!spanId) return true;
  const trimmed = spanId.trim();
  return (
    trimmed === "" ||
    trimmed ===
      "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" ||
    /^0+$/.test(trimmed)
  );
}
