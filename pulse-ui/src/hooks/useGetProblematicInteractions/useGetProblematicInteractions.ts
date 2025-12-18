import { useMemo } from "react";
import dayjs from "dayjs";
import { useGetDataQuery, DataQueryRequestBody, FilterField } from "../useGetDataQuery";
import { COLUMN_NAME, SpanType } from "../../constants/PulseOtelSemcov";
import {
  UseGetProblematicInteractionsParams,
  UseGetProblematicInteractionsReturn,
  EventTypeFilter,
  ProblematicInteractionData,
  InteractionEventType,
} from "./useGetProblematicInteractions.interface";
import { EVENT_TYPE, FILTER_MAPPING } from "../hooks.interface";

export const useGetProblematicInteractions = ({
  interactionName,
  startTime,
  endTime,
  eventTypeFilters = [],
  enabled = true,
  dashboardFilters,
}: UseGetProblematicInteractionsParams): UseGetProblematicInteractionsReturn => {

  const requestFilters: FilterField[] = useMemo(() => {
    const baseFilters: FilterField[] = [
      { field: "SpanType", operator: "EQ", value: [SpanType.INTERACTION] },
    ];

    if (interactionName) {
      baseFilters.push({ field: "SpanName", operator: "EQ", value: [interactionName] });
    }


    Object.entries(FILTER_MAPPING).forEach(([filterKey, fieldName]) => {
      const value = dashboardFilters?.[filterKey as keyof typeof dashboardFilters];
      if (value) {
        baseFilters.push({ field: fieldName, operator: "EQ", value: [value] });
      }
    });

    // Event type filters
    if (eventTypeFilters.length > 0) {
      // Separate event-based filters from StatusCode-based filters
      const eventBasedFilters: string[] = [];
      const statusCodeFilters: string[] = [];

      const eventTypeMap: Partial<Record<EventTypeFilter, string>> = {
        crash: `has(Events.Name, '${EVENT_TYPE.CRASH}')`,
        anr: `has(Events.Name, '${EVENT_TYPE.ANR}')`,
        frozenFrame: `has(Events.Name, '${EVENT_TYPE.FROZEN_FRAME}')`,
        nonFatal: `has(Events.Name, '${EVENT_TYPE.NON_FATAL}')`,
      };

      eventTypeFilters.forEach((type) => {
        if (type === "error") {
          // Error interactions: StatusCode = 'Error'
          statusCodeFilters.push("StatusCode = 'Error'");
        } else if (type === "completed") {
          // Completed interactions: StatusCode != 'Error'
          statusCodeFilters.push("StatusCode != 'Error'");
        } else if (eventTypeMap[type]) {
          eventBasedFilters.push(eventTypeMap[type]!);
        }
      });

      // Combine all filter conditions with OR logic
      const allConditions: string[] = [];

      if (eventBasedFilters.length > 0) {
        allConditions.push(`(${eventBasedFilters.join(" + ")}) > 0`);
      }

      if (statusCodeFilters.length > 0) {
        // If both error and completed are selected, combine with OR
        allConditions.push(`(${statusCodeFilters.join(" OR ")})`);
      }

      if (allConditions.length > 0) {
        const combinedFilter = allConditions.length === 1 
          ? allConditions[0] 
          : `(${allConditions.join(" OR ")})`;
        
        baseFilters.push({
          field: "",
          operator: "ADDITIONAL",
          value: [combinedFilter],
        });
      }
    }

    return baseFilters;
  }, [interactionName, dashboardFilters, eventTypeFilters]);

  const requestBody = useMemo((): DataQueryRequestBody => {
    return {
      dataType: "TRACES",
      timeRange: {
        start: dayjs.utc(startTime).toISOString(),
        end: dayjs.utc(endTime).toISOString(),
      },
      select: [
        {
          function: "CUSTOM",
          param: {
            expression:
              `arrayStringConcat(arrayMap(x -> toString(x), ${COLUMN_NAME.EVENTS_NAME}), ',')`,
          },
          alias: "event_names",
        },
        {
          function: "CUSTOM",
          param: {
            expression:
              `arrayStringConcat(arrayMap(x -> toString(x), ${COLUMN_NAME.EVENTS_TIMESTAMP}), ',')`,
          },
          alias: "event_timestamps",
        },
        {
          function: "COL",
          param: { field: COLUMN_NAME.TIMESTAMP },
          alias: "interaction_timestamp",
        },
        {
          function: "COL",
          param: { field: COLUMN_NAME.SPAN_ID },
          alias: "spanid",
        },
        {
          function: "COL",
          param: { field: COLUMN_NAME.TRACE_ID },
          alias: "traceid",
        },
        {
          function: "COL",
          param: { field: COLUMN_NAME.SESSION_ID },
          alias: "sessionid",
        },
        {
          function: "COL",
          param: { field: COLUMN_NAME.DEVICE_MODEL },
          alias: "device",
        },
        {
          function: "COL",
          param: { field: COLUMN_NAME.DURATION },
          alias: "duration",
        },
        {
          function: "COL",
          param: { field: COLUMN_NAME.USER_ID },
          alias: "userid",
        },
        {
          function: "COL",
          param: { field: `ResourceAttributes['${COLUMN_NAME.DEVICE_MANUFACTURER}']` },
          alias: "manufacturer",
        },
        {
          function: "COL",
          param: { field: COLUMN_NAME.PLATFORM },
          alias: "os_name",
        },
        {
          function: "COL",
          param: { field: `ResourceAttributes['${COLUMN_NAME.OS_TYPE}']` },
          alias: "os_type",
        },
        {
          function: "COL",
          param: { field: COLUMN_NAME.OS_VERSION },
          alias: "os_version",
        },
        {
          function: "COL",
          param: { field: `ResourceAttributes['${COLUMN_NAME.OS_DESCRIPTION}']` },
          alias: "os_description",
        },
        {
          function: "COL",
          param: { field: COLUMN_NAME.STATE },
          alias: "state",
        },
        {
          function: "COL",
          param: { field: COLUMN_NAME.COUNTRY },
          alias: "country",
        },
        {
          function: "CUSTOM",
          param: {
            expression:
              `toFloat64OrZero(SpanAttributes['${COLUMN_NAME.FROZEN_FRAME_COUNT}'])`,
          },
          alias: "frozen_frame",
        },
        {
          function: "COL",
          param: { field: `SpanAttributes['${COLUMN_NAME.IS_ERROR}']` },
          alias: "is_error",
        },
        {
          function: "COL",
          param: { field: "StatusCode" },
          alias: "status_code",
        }
      ],
      filters: requestFilters,
      orderBy: [
        {
          field: "interaction_timestamp",
          direction: "DESC",
        },
      ],
      limit: 10,
    };
  }, [startTime, endTime, requestFilters]);

  const { data, isLoading, isFetching, error } = useGetDataQuery({
    requestBody,
    enabled: enabled && !!interactionName && !!startTime && !!endTime,
  });

  const getEventType = (
    eventNames: string | undefined | null,
    statusCode: string | undefined | null,
  ): InteractionEventType => {
    // First check for specific event types from Events.Name
    if (eventNames && eventNames.trim() !== "") {
      const events = eventNames
        .split(",")
        .map((e) => e.trim())
        .filter((e) => e.length > 0);

      for (const event of events) {
        const eventLower = event.toLowerCase();

        if (eventLower === "device.crash") {
          return "crash";
        }

        if (eventLower === "device.anr") {
          return "anr";
        }

        if (eventLower === "non_fatal") {
          return "nonFatal";
        }

        if (eventLower === "app.jank.frozen") {
          return "frozenFrame";
        }
      }
    }

    // Determine based on StatusCode
    if (statusCode === "Error") {
      return "error";
    }

    return "completed";
  };

  const interactions = useMemo(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return [];
    }

    const fields = responseData.fields;
    const eventNamesIndex = fields.indexOf("event_names");
    const sessionIdIndex = fields.indexOf("sessionid");
    const traceIdIndex = fields.indexOf("traceid");
    const deviceIndex = fields.indexOf("device");
    const durationIndex = fields.indexOf("duration");
    const userIdIndex = fields.indexOf("userid");
    const osVersionIndex = fields.indexOf("os_version");
    const interactionTimestampIndex = fields.indexOf("interaction_timestamp");
    const statusCodeIndex = fields.indexOf("status_code");

    const transformedInteractions: ProblematicInteractionData[] = [];

    responseData.rows.forEach((row) => {
      const traceId = String(row[traceIdIndex] || "");
      const eventNames = String(row[eventNamesIndex] || "");
      const device = String(row[deviceIndex] || "unknown");
      const durationMs = parseFloat(String(row[durationIndex] || "0"));
      const userId = String(row[userIdIndex] || "");
      const osVersion = String(row[osVersionIndex] || "unknown");
      const timestamp = String(row[interactionTimestampIndex] || "");
      const sessionId = String(row[sessionIdIndex] || "");
      const statusCode = String(row[statusCodeIndex] || "");

      if (!traceId) return;

      transformedInteractions.push({
        trace_id: traceId,
        sessionId: sessionId,
        user_id: userId,
        phone_number: "",
        device: device,
        os_version: osVersion,
        start_time: timestamp || new Date().toISOString(),
        duration_ms: durationMs,
        event_count: 0,
        screen_count: 0,
        event_type: getEventType(eventNames, statusCode),
        event_names: eventNames,
        interaction_name: interactionName,
        screens_visited: "",
      });
    });

    return transformedInteractions;
  }, [data, interactionName]);

  // Return empty interactions while fetching to prevent showing stale data
  const finalInteractions = isFetching ? [] : interactions;

  return {
    interactions: finalInteractions,
    isLoading: isLoading || isFetching,
    isError: !!error || !!data?.error,
    error: error || data?.error || null,
  };
};

