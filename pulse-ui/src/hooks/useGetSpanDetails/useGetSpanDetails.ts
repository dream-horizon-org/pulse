import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  GetSpanDetailsParams,
  SpanDetailsResponse,
  LogDetailsResponse,
  ExceptionDetailsResponse,
  ExceptionStackTraceNode,
  PlatformType,
  ExceptionEventName,
} from "./useGetSpanDetails.interface";
import { makeRequest } from "../../helpers/makeRequest";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import type { AttributeValue } from "../../types/attributes";

dayjs.extend(utc);

/**
 * Fetches detailed attributes for a specific span
 */
const fetchSpanDetails = async (
  traceId: string,
  spanId: string,
  timestamp: string
): Promise<SpanDetailsResponse> => {
  const ts = dayjs.utc(timestamp);
  const startTime = ts.subtract(1, "hour").toISOString();
  const endTime = ts.add(1, "hour").toISOString();

  const requestBody = {
    dataType: "TRACES",
    timeRange: { start: startTime, end: endTime },
    select: [
      // Map types - convert to JSON string
      {
        function: "CUSTOM",
        param: { expression: "toJSONString(ResourceAttributes)" },
        alias: "resourceAttributes",
      },
      {
        function: "CUSTOM",
        param: { expression: "toJSONString(SpanAttributes)" },
        alias: "spanAttributes",
      },
      // Array types - convert to delimited strings
      {
        function: "CUSTOM",
        param: {
          expression: `arrayStringConcat(arrayMap(x -> toString(x), \`Events.Timestamp\`), '|||')`,
        },
        alias: "eventsTimestamp",
      },
      {
        function: "CUSTOM",
        param: {
          expression: `arrayStringConcat(arrayMap(x -> toString(x), \`Events.Name\`), '|||')`,
        },
        alias: "eventsName",
      },
      {
        function: "CUSTOM",
        param: {
          expression: `arrayStringConcat(arrayMap(x -> toJSONString(x), \`Events.Attributes\`), '|||')`,
        },
        alias: "eventsAttributes",
      },
      {
        function: "CUSTOM",
        param: {
          expression: `arrayStringConcat(arrayMap(x -> toString(x), \`Links.TraceId\`), '|||')`,
        },
        alias: "linksTraceId",
      },
      {
        function: "CUSTOM",
        param: {
          expression: `arrayStringConcat(arrayMap(x -> toString(x), \`Links.SpanId\`), '|||')`,
        },
        alias: "linksSpanId",
      },
      {
        function: "CUSTOM",
        param: {
          expression: `arrayStringConcat(arrayMap(x -> toJSONString(x), \`Links.Attributes\`), '|||')`,
        },
        alias: "linksAttributes",
      },
    ],
    filters: [
      { field: "TraceId", operator: "EQ", value: [traceId] },
      { field: "SpanId", operator: "EQ", value: [spanId] },
    ],
    limit: 1,
  };

  const dataQuery = API_ROUTES.DATA_QUERY;
  
  const response = await makeRequest<{ fields: string[]; rows: any[][] }>({
    url: `${API_BASE_URL}${dataQuery.apiPath}`,
    init: {
      method: dataQuery.method,
      body: JSON.stringify(requestBody),
    },
  });

  const rows = response?.data?.rows ?? [];
  if (!rows[0]) {
    return {
      resourceAttributes: {},
      spanAttributes: {},
      events: [],
      links: [],
    };
  }

  const row = rows[0];
  const fields = response?.data?.fields ?? [];
  if (fields.length === 0) {
    return {
      resourceAttributes: {},
      spanAttributes: {},
      events: [],
      links: [],
    };
  }
  const getField = (name: string) => {
    const index = fields.findIndex((f: string) => f.toLowerCase() === name.toLowerCase());
    return index >= 0 ? row[index] : null;
  };

  // Parse Map attributes from JSON string
  const resourceAttributes = parseJsonMap(getField("resourceAttributes"));
  const spanAttributes = parseJsonMap(getField("spanAttributes"));

  // Parse events from delimited strings
  const eventsTimestampStr = String(getField("eventsTimestamp") || "");
  const eventsNameStr = String(getField("eventsName") || "");
  const eventsAttributesStr = String(getField("eventsAttributes") || "");

  const eventsTimestamp = eventsTimestampStr ? eventsTimestampStr.split("|||") : [];
  const eventsName = eventsNameStr ? eventsNameStr.split("|||") : [];
  const eventsAttributes = eventsAttributesStr ? eventsAttributesStr.split("|||") : [];

  const events = eventsName
    .filter((name) => name) // Filter out empty strings
    .map((name, i) => ({
      timestamp: eventsTimestamp[i] || "",
      name: name,
      attributes: parseJsonMap(eventsAttributes[i]),
    }));

  // Parse links from delimited strings
  const linksTraceIdStr = String(getField("linksTraceId") || "");
  const linksSpanIdStr = String(getField("linksSpanId") || "");
  const linksAttributesStr = String(getField("linksAttributes") || "");

  const linksTraceId = linksTraceIdStr ? linksTraceIdStr.split("|||") : [];
  const linksSpanId = linksSpanIdStr ? linksSpanIdStr.split("|||") : [];
  const linksAttributes = linksAttributesStr ? linksAttributesStr.split("|||") : [];

  const links = linksTraceId
    .filter((id) => id) // Filter out empty strings
    .map((tid, i) => ({
      traceId: tid,
      spanId: linksSpanId[i] || "",
      attributes: parseJsonMap(linksAttributes[i]),
    }));

  return {
    resourceAttributes,
    spanAttributes,
    events,
    links,
  };
};

/**
 * Fetches detailed attributes for a specific log
 */
const fetchLogDetails = async (
  traceId: string,
  spanId: string,
  timestamp: string
): Promise<LogDetailsResponse> => {
  const ts = dayjs.utc(timestamp);
  // Use a wider time range for logs to ensure we find the record
  // (timestamp precision might vary between storage and query)
  const startTime = ts.subtract(1, "minute").toISOString();
  const endTime = ts.add(1, "minute").toISOString();

  // Build filters - always filter by TraceId
  const filters: any[] = [
    { field: "TraceId", operator: "EQ", value: [traceId] },
  ];
  
  // Only add SpanId filter if it's valid
  if (spanId && spanId !== "" && !spanId.startsWith("0000000")) {
    filters.push({ field: "SpanId", operator: "EQ", value: [spanId] });
  }

  const requestBody = {
    dataType: "LOGS",
    timeRange: { start: startTime, end: endTime },
    select: [
      {
        function: "CUSTOM",
        param: { expression: "toJSONString(ResourceAttributes)" },
        alias: "resourceAttributes",
      },
      {
        function: "CUSTOM",
        param: { expression: "toJSONString(LogAttributes)" },
        alias: "logAttributes",
      },
      {
        function: "CUSTOM",
        param: { expression: "toJSONString(ScopeAttributes)" },
        alias: "scopeAttributes",
      },
      { function: "COL", param: { field: "Body" }, alias: "body" },
      { function: "COL", param: { field: "SeverityText" }, alias: "severityText" },
      { function: "COL", param: { field: "SeverityNumber" }, alias: "severityNumber" },
    ],
    filters,
    limit: 1,
  };

  const dataQuery = API_ROUTES.DATA_QUERY;

  const response = await makeRequest<{ fields: string[]; rows: any[][] }>({
    url: `${API_BASE_URL}${dataQuery.apiPath}`,
    init: {
      method: dataQuery.method,
      body: JSON.stringify(requestBody),
    },
  });

  const rows = response?.data?.rows ?? [];
  if (!rows[0]) {
    return {
      resourceAttributes: {},
      logAttributes: {},
      scopeAttributes: {},
      body: "",
      severityText: "",
      severityNumber: 0,
    };
  }

  const row = rows[0];
  const fields = response?.data?.fields ?? [];
  if (fields.length === 0) {
    return {
      resourceAttributes: {},
      logAttributes: {},
      scopeAttributes: {},
      body: "",
      severityText: "",
      severityNumber: 0,
    };
  }
  const getField = (name: string) => {
    const index = fields.findIndex((f: string) => f.toLowerCase() === name.toLowerCase());
    return index >= 0 ? row[index] : null;
  };

  return {
    resourceAttributes: parseJsonMap(getField("resourceAttributes")),
    logAttributes: parseJsonMap(getField("logAttributes")),
    scopeAttributes: parseJsonMap(getField("scopeAttributes")),
    body: String(getField("body") || ""),
    severityText: String(getField("severityText") || ""),
    severityNumber: Number(getField("severityNumber") || 0),
  };
};

/**
 * Fetches detailed attributes for a specific exception from stack_trace_events table
 */
const fetchExceptionDetails = async (
  traceId: string,
  timestamp: string,
  groupId?: string
): Promise<ExceptionDetailsResponse | null> => {
  const ts = dayjs.utc(timestamp);
  // Use a wider time range for exceptions
  const startTime = ts.subtract(1, "minute").toISOString();
  const endTime = ts.add(1, "minute").toISOString();

  // Build filters
  const filters: any[] = [];
  
  // Prefer groupId + timestamp for exact match, fallback to traceId
  if (groupId) {
    filters.push({ field: "GroupId", operator: "EQ", value: [groupId] });
  } else if (traceId) {
    filters.push({ field: "TraceId", operator: "EQ", value: [traceId] });
  }

  const requestBody = {
    dataType: "EXCEPTIONS",
    timeRange: { start: startTime, end: endTime },
    select: [
      // Attributes as JSON
      {
        function: "CUSTOM",
        param: { expression: "toJSONString(ResourceAttributes)" },
        alias: "resourceAttributes",
      },
      {
        function: "CUSTOM",
        param: { expression: "toJSONString(LogAttributes)" },
        alias: "logAttributes",
      },
      {
        function: "CUSTOM",
        param: { expression: "toJSONString(ScopeAttributes)" },
        alias: "scopeAttributes",
      },
      // Exception details
      { function: "COL", param: { field: "ExceptionStackTrace" }, alias: "exceptionStackTrace" },
      { function: "COL", param: { field: "ExceptionStackTraceRaw" }, alias: "exceptionStackTraceRaw" },
      { function: "COL", param: { field: "ExceptionMessage" }, alias: "exceptionMessage" },
      { function: "COL", param: { field: "ExceptionType" }, alias: "exceptionType" },
      { function: "COL", param: { field: "Title" }, alias: "title" },
      { function: "COL", param: { field: "EventName" }, alias: "eventName" },
      // Context
      { function: "COL", param: { field: "ScreenName" }, alias: "screenName" },
      { function: "COL", param: { field: "Interactions" }, alias: "interactions" },
      { function: "COL", param: { field: "UserId" }, alias: "userId" },
      // Device/app info
      { function: "COL", param: { field: "Platform" }, alias: "platform" },
      { function: "COL", param: { field: "OsVersion" }, alias: "osVersion" },
      { function: "COL", param: { field: "DeviceModel" }, alias: "deviceModel" },
      { function: "COL", param: { field: "AppVersion" }, alias: "appVersion" },
      { function: "COL", param: { field: "AppVersionCode" }, alias: "appVersionCode" },
      { function: "COL", param: { field: "SdkVersion" }, alias: "sdkVersion" },
      { function: "COL", param: { field: "BundleId" }, alias: "bundleId" },
      // Grouping
      { function: "COL", param: { field: "GroupId" }, alias: "groupId" },
      { function: "COL", param: { field: "Signature" }, alias: "signature" },
      { function: "COL", param: { field: "Fingerprint" }, alias: "fingerprint" },
    ],
    filters,
    limit: 1,
  };

  const dataQuery = API_ROUTES.DATA_QUERY;

  const response = await makeRequest<{ fields: string[]; rows: any[][] }>({
    url: `${API_BASE_URL}${dataQuery.apiPath}`,
    init: {
      method: dataQuery.method,
      body: JSON.stringify(requestBody),
    },
  });

  const rows = response?.data?.rows ?? [];
  if (!rows[0]) {
    return null;
  }

  const row = rows[0];
  const fields = response?.data?.fields ?? [];
  if (fields.length === 0) {
    return null;
  }
  const getField = (name: string) => {
    const index = fields.findIndex((f: string) => f.toLowerCase() === name.toLowerCase());
    return index >= 0 ? row[index] : null;
  };

  // Parse interactions array
  let interactionIds: string[] = [];
  const interactionsRaw = getField("interactions");
  if (Array.isArray(interactionsRaw)) {
    interactionIds = interactionsRaw.map(String);
  } else if (typeof interactionsRaw === "string") {
    try {
      const parsed = JSON.parse(interactionsRaw);
      if (Array.isArray(parsed)) {
        interactionIds = parsed.map(String);
      }
    } catch (error) {
      console.warn("[useGetSpanDetails] Failed to parse interactions", {
        interactionsRaw,
        error,
      });
    }
  }

  const getNullableString = (name: string) => {
    const value = getField(name);
    if (value === null || value === undefined || value === "") return null;
    return String(value);
  };

  const stringFieldNames = [
    "exceptionMessage",
    "exceptionType",
    "title",
    "eventName",
    "screenName",
    "userId",
    "platform",
    "osVersion",
    "deviceModel",
    "appVersion",
    "appVersionCode",
    "sdkVersion",
    "bundleId",
    "groupId",
    "signature",
    "fingerprint",
  ] as const;

  const stringFields = Object.fromEntries(
    stringFieldNames.map((name) => [name, getNullableString(name)])
  ) as Record<(typeof stringFieldNames)[number], string | null>;

  const exceptionStackTrace = getNullableString("exceptionStackTrace");
  const exceptionStackTraceRaw = getNullableString("exceptionStackTraceRaw");

  let structuredStackTrace: ExceptionStackTraceNode[] | null = null;
  if (exceptionStackTraceRaw) {
    try {
      const parsed = JSON.parse(exceptionStackTraceRaw);
      if (Array.isArray(parsed)) {
        structuredStackTrace = parsed as ExceptionStackTraceNode[];
      }
    } catch (error) {
      console.warn("[useGetSpanDetails] Failed to parse exception stack trace", {
        exceptionStackTraceRaw,
        error,
      });
    }
  }

  return {
    resourceAttributes: parseJsonMap(getField("resourceAttributes")),
    logAttributes: parseJsonMap(getField("logAttributes")),
    scopeAttributes: parseJsonMap(getField("scopeAttributes")),
    exceptionStackTrace: structuredStackTrace ?? exceptionStackTrace,
    exceptionStackTraceRaw,
    exceptionMessage: stringFields.exceptionMessage,
    exceptionType: stringFields.exceptionType,
    title: stringFields.title,
    eventName: stringFields.eventName as ExceptionEventName | null,
    screenName: stringFields.screenName,
    interactionIds,
    interactions: interactionIds,
    userId: stringFields.userId,
    platform: (stringFields.platform as PlatformType | null) ?? null,
    osVersion: stringFields.osVersion,
    deviceModel: stringFields.deviceModel,
    appVersion: stringFields.appVersion,
    appVersionCode: stringFields.appVersionCode,
    sdkVersion: stringFields.sdkVersion,
    bundleId: stringFields.bundleId,
    groupId: stringFields.groupId,
    signature: stringFields.signature,
    fingerprint: stringFields.fingerprint,
  };
};

/**
 * Parse JSON string to map object
 */
function parseJsonMap(value: unknown): Record<string, AttributeValue> {
  if (!value) return {};
  
  // If it's already an object
  if (typeof value === "object" && !Array.isArray(value)) {
    return value as Record<string, AttributeValue>;
  }
  
  // If it's a JSON string, parse safely (no eval).
  if (typeof value === "string") {
    try {
      const parsed = JSON.parse(value);
      if (typeof parsed === "object" && !Array.isArray(parsed)) {
        return parsed as Record<string, AttributeValue>;
      }
    } catch {
      // Not valid JSON, return empty
    }
  }
  
  return {};
}

export const useGetSpanDetails = ({
  dataType,
  traceId,
  spanId,
  timestamp,
  groupId,
  enabled = true,
}: GetSpanDetailsParams) => {
  return useQuery({
    queryKey: ["spanDetails", dataType, traceId, spanId, timestamp, groupId],
    queryFn: async () => {
      switch (dataType) {
        case "EXCEPTIONS":
          return fetchExceptionDetails(traceId, timestamp, groupId);
        case "LOGS":
          return fetchLogDetails(traceId, spanId, timestamp);
        case "TRACES":
          return fetchSpanDetails(traceId, spanId, timestamp);
        default: {
          const unreachable: never = dataType;
          throw new Error(`Unhandled dataType: ${unreachable}`);
        }
      }
    },
    refetchOnWindowFocus: false,
    // For logs/exceptions, we only need traceId or groupId; for spans, we need both traceId and spanId
    enabled: enabled && (!!traceId || !!groupId) && (dataType === "LOGS" || dataType === "EXCEPTIONS" || !!spanId),
    staleTime: Infinity, // Cache forever since details don't change
  });
};
