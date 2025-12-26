import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  GetSpanDetailsParams,
  SpanDetailsResponse,
  LogDetailsResponse,
} from "./useGetSpanDetails.interface";
import { makeRequest } from "../../helpers/makeRequest";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

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

  if (!response?.data?.rows?.[0]) {
    return {
      resourceAttributes: {},
      spanAttributes: {},
      events: [],
      links: [],
    };
  }

  const row = response.data.rows[0];
  const fields = response.data.fields;
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
  // Use a tighter time range for logs
  const startTime = ts.subtract(1, "second").toISOString();
  const endTime = ts.add(1, "second").toISOString();

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

  if (!response?.data?.rows?.[0]) {
    return {
      resourceAttributes: {},
      logAttributes: {},
      scopeAttributes: {},
      body: "",
      severityText: "",
      severityNumber: 0,
    };
  }

  const row = response.data.rows[0];
  const fields = response.data.fields;
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
 * Parse JSON string to map object
 */
function parseJsonMap(value: unknown): Record<string, string> {
  if (!value) return {};
  
  // If it's already an object
  if (typeof value === "object" && !Array.isArray(value)) {
    return value as Record<string, string>;
  }
  
  // If it's a JSON string
  if (typeof value === "string") {
    try {
      const parsed = JSON.parse(value);
      if (typeof parsed === "object" && !Array.isArray(parsed)) {
        return parsed as Record<string, string>;
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
  enabled = true,
}: GetSpanDetailsParams) => {
  return useQuery({
    queryKey: ["spanDetails", dataType, traceId, spanId, timestamp],
    queryFn: async () => {
      if (dataType === "LOGS") {
        return fetchLogDetails(traceId, spanId, timestamp);
      }
      return fetchSpanDetails(traceId, spanId, timestamp);
    },
    refetchOnWindowFocus: false,
    // For logs, we only need traceId; for spans, we need both traceId and spanId
    enabled: enabled && !!traceId && (dataType === "LOGS" || !!spanId),
    staleTime: Infinity, // Cache forever since details don't change
  });
};
