import { DataQueryRequestBody } from "../../../hooks";
import { TimeRange } from "../../../hooks/useGetDataQuery/useGetDataQuery.interface";

export type Scenario = "sessionId" | "sessionId+traceId" | "sessionId+interactionTraceId";

export interface QueryParams {
  sessionId: string;
  traceId?: string;
  interactionTraceId?: string;
  timeRange: TimeRange;
}

/**
 * Build query for TRACES data type based on scenario
 */
export function buildTracesQuery(params: QueryParams): DataQueryRequestBody {
  const { sessionId, traceId, interactionTraceId, timeRange } = params;

  const filters: DataQueryRequestBody["filters"] = [
    {
      field: "SessionId",
      operator: "EQ",
      value: [sessionId],
    },
  ];

  // Scenario 2: Filter by traceId
  if (traceId) {
    filters.push({
      field: "TraceId",
      operator: "EQ",
      value: [traceId],
    });
  }

  // Scenario 3: Filter by interactionTraceId in SpanAttributes
  if (interactionTraceId) {
    filters.push({
      field: "",
      operator: "ADDITIONAL",
      // eslint-disable-next-line no-template-curly-in-string
      value: [`SpanAttributes['pulse.interaction.ids'] LIKE '%${interactionTraceId}%' OR TraceId = '${interactionTraceId}'`],
    });
  }

  return {
    dataType: "TRACES",
    timeRange,
    select: [
      {
        function: "COL",
        param: { field: "TraceId" },
        alias: "traceid",
      },
      {
        function: "COL",
        param: { field: "SpanId" },
        alias: "spanid",
      },
      {
        function: "COL",
        param: { field: "ParentSpanId" },
        alias: "parentspanid",
      },
      {
        function: "COL",
        param: { field: "SpanName" },
        alias: "spanname",
      },
      {
        function: "COL",
        param: { field: "Timestamp" },
        alias: "timestamp",
      },
      {
        function: "COL",
        param: { field: "Duration" },
        alias: "duration",
      },
      {
        function: "COL",
        param: { field: "DeviceModel" },
        alias: "device",
      },
      {
        function: "COL",
        param: { field: "OsVersion" },
        alias: "os_version",
      },
      {
        function: "COL",
        param: { field: "Platform" },
        alias: "os_name",
      },
      {
        function: "COL",
        param: { field: "GeoState" },
        alias: "state",
      },
      {
        function: "COL",
        param: { field: "StatusCode" },
        alias: "statuscode",
      },
      {
        function: "COL",
        param: { field: "StatusMessage" },
        alias: "statusmessage",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "toFloat64OrZero(SpanAttributes['app.interaction.frozen_frame_count'])",
        },
        alias: "frozen_frame",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "toFloat64OrZero(SpanAttributes['app.interaction.analysed_frame_count'])",
        },
        alias: "analysed_frame",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "toFloat64OrZero(SpanAttributes['app.interaction.unanalysed_frame_count'])",
        },
        alias: "unanalysed_frame",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "(arrayCount(x -> x LIKE '%device.anr%', Events.Name))",
        },
        alias: "anr",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "(arrayCount(x -> x LIKE '%device.crash%', Events.Name))",
        },
        alias: "crash",
      },
      // Events data
      {
        function: "CUSTOM",
        param: {
          expression: "arrayStringConcat(arrayMap(x -> toString(x), Events.Timestamp), ',')",
        },
        alias: "events_timestamp",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "arrayStringConcat(arrayMap(x -> toString(x), Events.Name), ',')",
        },
        alias: "events_name",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "arrayStringConcat(arrayMap(x -> toString(x), Events.Attributes), '|')",
        },
        alias: "events_attributes",
      },
      // Links data
      {
        function: "CUSTOM",
        param: {
          expression: "arrayStringConcat(arrayMap(x -> toString(x), Links.TraceId), ',')",
        },
        alias: "links_traceid",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "arrayStringConcat(arrayMap(x -> toString(x), Links.SpanId), ',')",
        },
        alias: "links_spanid",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "arrayStringConcat(arrayMap(x -> toString(x), Links.TraceState), ',')",
        },
        alias: "links_tracestate",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "arrayStringConcat(arrayMap(x -> toString(x), Links.Attributes), '|')",
        },
        alias: "links_attributes",
      },
      // ResourceAttributes - extract common ones
      {
        function: "CUSTOM",
        param: {
          expression: "ResourceAttributes['app.build_id']",
        },
        alias: "app_build_id",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "ResourceAttributes['rum.sdk.version']",
        },
        alias: "sdk_version",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "ResourceAttributes['geo.country.iso_code']",
        },
        alias: "geo_country",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "ResourceAttributes['network.carrier.name']",
        },
        alias: "network_provider",
      },
      // SpanAttributes - extract common ones
      {
        function: "CUSTOM",
        param: {
          expression: "SpanAttributes['user.id']",
        },
        alias: "user_id",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "SpanAttributes['error.type']",
        },
        alias: "error_type",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "SpanAttributes['error.message']",
        },
        alias: "error_message",
      },
    ],
    filters,
    orderBy: [
      { field: "timestamp", direction: "ASC" },
    ],
  };
}

/**
 * Build query for LOGS data type based on scenario
 */
export function buildLogsQuery(params: QueryParams): DataQueryRequestBody {
  const { sessionId, traceId, interactionTraceId, timeRange } = params;

  const filters: DataQueryRequestBody["filters"] = [
    {
      field: "SessionId",
      operator: "EQ",
      value: [sessionId],
    },
  ];

  // Scenario 2: Filter by traceId
  if (traceId) {
    filters.push({
      field: "TraceId",
      operator: "EQ",
      value: [traceId],
    });
  }

  // Scenario 3: Filter by interactionTraceId in LogAttributes or TraceId
  if (interactionTraceId) {
    filters.push({
      field: "",
      operator: "ADDITIONAL",
      // eslint-disable-next-line no-template-curly-in-string
      value: [`LogAttributes['pulse.interaction.ids'] LIKE '%${interactionTraceId}%' OR TraceId = '${interactionTraceId}'`],
    });
  }

  return {
    dataType: "LOGS",
    timeRange,
    select: [
      {
        function: "COL",
        param: { field: "TraceId" },
        alias: "traceid",
      },
      {
        function: "COL",
        param: { field: "SpanId" },
        alias: "spanid",
      },
      {
        function: "COL",
        param: { field: "Timestamp" },
        alias: "timestamp",
      },
      {
        function: "COL",
        param: { field: "SeverityText" },
        alias: "severity",
      },
      {
        function: "COL",
        param: { field: "Body" },
        alias: "body",
      },
      {
        function: "COL",
        param: { field: "DeviceModel" },
        alias: "device",
      },
      {
        function: "COL",
        param: { field: "OsVersion" },
        alias: "os_version",
      },
      {
        function: "COL",
        param: { field: "Platform" },
        alias: "os_name",
      },
      {
        function: "COL",
        param: { field: "GeoState" },
        alias: "state",
      },
      // ResourceAttributes
      {
        function: "CUSTOM",
        param: {
          expression: "ResourceAttributes['app.build_id']",
        },
        alias: "app_build_id",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "ResourceAttributes['rum.sdk.version']",
        },
        alias: "sdk_version",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "ResourceAttributes['geo.country.iso_code']",
        },
        alias: "geo_country",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "ResourceAttributes['network.carrier.name']",
        },
        alias: "network_provider",
      },
    ],
    filters,
    orderBy: [
      { field: "timestamp", direction: "ASC" },
    ],
  };
}

/**
 * Determine scenario from query parameters
 */
export function determineScenario(
  sessionId: string | undefined,
  traceId: string | null,
  interactionTraceId: string | null,
): Scenario | null {
  if (!sessionId) return null;
  
  if (interactionTraceId) {
    return "sessionId+interactionTraceId";
  }
  
  if (traceId) {
    return "sessionId+traceId";
  }
  
  return "sessionId";
}

