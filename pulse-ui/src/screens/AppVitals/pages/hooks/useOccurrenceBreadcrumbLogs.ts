import { useMemo } from "react";
import { useInfiniteQuery, type InfiniteData } from "@tanstack/react-query";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { API_BASE_URL, API_ROUTES } from "../../../../constants/Constants";
import { makeRequest } from "../../../../helpers/makeRequest";
import type { ApiResponse } from "../../../../helpers/makeRequest/makeRequest.interface";
import { useProjectQueryEnabled } from "../../../../hooks/useProjectQueryEnabled";
import type {
  DataQueryRequestBody,
  DataQueryResponse,
  FilterField,
} from "../../../../hooks/useGetDataQuery/useGetDataQuery.interface";
import {
  COLUMN_NAME,
  LogDataQueryAlias,
} from "../../../../constants/PulseOtelSemcov";
import { getPulseTypeLabel } from "../../../../constants/pulseTypeLabels";
import { getDateFromUTCTimeString } from "../../../../utils/DateUtil";
import {
  getErrorMessage,
  classifyError,
} from "../../../../utils/errorHandling";

dayjs.extend(utc);

const PAGE_SIZE = 50;

type BreadcrumbLogsPage = ApiResponse<DataQueryResponse>;
type BreadcrumbLogsInfiniteData = InfiniteData<BreadcrumbLogsPage, number>;

/** Earliest bound for timeRange — data-query requires Timestamp bounds; session filter narrows rows. */
const SESSION_LOGS_TIME_START_UTC = "1970-01-01 00:00:00";

export interface BreadcrumbItem {
  id: string;
  eventName: string;
  screenName: string;
  timestamp: Date;
  relativeMs: number;
  /** Loaded on expand via `fetchBreadcrumbLogAttributes`; empty until then. */
  props: Record<string, unknown>;
  /** Raw `Timestamp` cell from ClickHouse (for detail query EQ). */
  timestampRaw: string;
  spanId: string;
  bodyRaw: string;
  eventNameRaw: string;
  pulseTypeRaw: string;
  /** From list query only; when false, no attributes expander. */
  hasLogAttributes: boolean;
}

/** Same as useGetDataQuery — ISO time range for the distribution API. */
function tryFormatTimeToIso(time: string): string | null {
  const trimmed = typeof time === "string" ? time.trim() : "";
  if (!trimmed) return null;
  if (trimmed.includes("T") || trimmed.includes("Z")) {
    const d = dayjs.utc(trimmed);
    return d.isValid() ? d.toISOString() : null;
  }
  const withFormat = dayjs.utc(trimmed, "YYYY-MM-DD HH:mm:ss");
  if (withFormat.isValid()) return withFormat.toISOString();
  const loose = dayjs.utc(trimmed);
  return loose.isValid() ? loose.toISOString() : null;
}

function buildOccurrenceLogsQuery(
  sessionId: string,
  offset: number,
): DataQueryRequestBody {
  const end = dayjs.utc().format("YYYY-MM-DD HH:mm:ss");

  return {
    dataType: "LOGS",
    timeRange: { start: SESSION_LOGS_TIME_START_UTC, end },
    select: [
      {
        function: "COL",
        param: { field: COLUMN_NAME.TIMESTAMP },
        alias: LogDataQueryAlias.TIMESTAMP,
      },
      {
        function: "COL",
        param: { field: COLUMN_NAME.BODY },
        alias: LogDataQueryAlias.BODY,
      },
      {
        function: "COL",
        param: { field: COLUMN_NAME.PULSE_TYPE },
        alias: LogDataQueryAlias.PULSE_TYPE,
      },
      {
        function: "COL",
        param: { field: COLUMN_NAME.EVENT_NAME },
        alias: LogDataQueryAlias.EVENT_NAME,
      },
      {
        function: "COL",
        param: { field: COLUMN_NAME.SPAN_ID },
        alias: LogDataQueryAlias.SPAN_ID,
      },
      {
        function: "CUSTOM",
        param: {
          expression: `toUInt8(length(mapKeys(${COLUMN_NAME.LOG_ATTRIBUTES})) > 0)`,
        },
        alias: LogDataQueryAlias.HAS_LOG_ATTRIBUTES,
      },
    ],
    filters: [
      {
        field: COLUMN_NAME.SESSION_ID,
        operator: "EQ",
        value: [sessionId],
      },
    ],
    orderBy: [{ field: COLUMN_NAME.TIMESTAMP, direction: "DESC" }],
    limit: PAGE_SIZE,
    offset,
  };
}

function withIsoTimeRange(body: DataQueryRequestBody): DataQueryRequestBody {
  const formattedStartTime = tryFormatTimeToIso(body.timeRange.start);
  const formattedEndTime = tryFormatTimeToIso(body.timeRange.end);
  return {
    ...body,
    timeRange: {
      start: formattedStartTime ?? "",
      end: formattedEndTime ?? "",
    },
  };
}

/** Parse API/CH timestamp as UTC wall-clock → `Date` for correct local display (`BreadcrumbTimeline` uses local getters). */
function parseUtcResponseTimestamp(raw: string): Date {
  const s = raw?.trim() ?? "";
  if (!s) return new Date(NaN);
  const value = getDateFromUTCTimeString(s) as Date;
  return Number.isNaN(value.getTime()) ? new Date(NaN) : value;
}

function pickEventLabel(
  body: string,
  eventName: string,
  pulseType: string,
): string {
  const b = body?.trim();
  if (b) return b;
  const e = eventName?.trim();
  if (e) return e;
  const p = pulseType?.trim();
  if (p) return getPulseTypeLabel(p);
  return "Log";
}

function mapRowsToBreadcrumbs(
  fields: string[],
  rows: string[][],
  errorTimestamp: Date,
): BreadcrumbItem[] {
  const ix = (name: string) => fields.indexOf(name);
  const tsI = ix(LogDataQueryAlias.TIMESTAMP);
  const bodyI = ix(LogDataQueryAlias.BODY);
  const pulseI = ix(LogDataQueryAlias.PULSE_TYPE);
  const eventI = ix(LogDataQueryAlias.EVENT_NAME);
  const spanI = ix(LogDataQueryAlias.SPAN_ID);
  const hasAttrI = ix(LogDataQueryAlias.HAS_LOG_ATTRIBUTES);
  const errorMs = errorTimestamp.getTime();

  return rows.map((row, rowIndex) => {
    const body = row[bodyI] ?? "";
    const eventNameCol = row[eventI] ?? "";
    const pulseType = row[pulseI] ?? "";
    const label = pickEventLabel(body, eventNameCol, pulseType);
    const ts = parseUtcResponseTimestamp(row[tsI] ?? "");
    const tsRaw = (row[tsI] ?? "").trim();
    const spanId = (row[spanI] ?? "").replace(/\0/g, "").trim();
    const hasAttrRaw = hasAttrI >= 0 ? (row[hasAttrI] ?? "").trim() : "0";
    const hasLogAttributes =
      hasAttrRaw === "1" ||
      hasAttrRaw.toLowerCase() === "true" ||
      hasAttrRaw.toLowerCase() === "yes";
    // `rowIndex` disambiguates duplicate timestamps / truncated bodies across the merged timeline (React keys + detail cache).
    const id = `log:${rowIndex}:${tsRaw}:${spanId || "na"}:${pulseType}:${eventNameCol}:${body.slice(0, 64)}`;
    return {
      id,
      eventName: label,
      screenName: "",
      timestamp: ts,
      relativeMs: ts.getTime() - errorMs,
      props: {},
      timestampRaw: tsRaw,
      spanId,
      bodyRaw: body,
      eventNameRaw: eventNameCol,
      pulseTypeRaw: pulseType,
      hasLogAttributes,
    };
  });
}

function buildLogAttributesDetailQuery(
  sessionId: string,
  item: Pick<
    BreadcrumbItem,
    "timestampRaw" | "spanId" | "bodyRaw" | "eventNameRaw" | "pulseTypeRaw"
  >,
): DataQueryRequestBody {
  const end = dayjs.utc().format("YYYY-MM-DD HH:mm:ss");
  const filters: FilterField[] = [
    {
      field: COLUMN_NAME.SESSION_ID,
      operator: "EQ",
      value: [sessionId],
    },
    {
      field: COLUMN_NAME.TIMESTAMP,
      operator: "EQ",
      value: [item.timestampRaw],
    },
  ];
  if (item.spanId) {
    filters.push({
      field: COLUMN_NAME.SPAN_ID,
      operator: "EQ",
      value: [item.spanId],
    });
  } else {
    filters.push(
      {
        field: COLUMN_NAME.BODY,
        operator: "EQ",
        value: [item.bodyRaw],
      },
      {
        field: COLUMN_NAME.EVENT_NAME,
        operator: "EQ",
        value: [item.eventNameRaw],
      },
      {
        field: COLUMN_NAME.PULSE_TYPE,
        operator: "EQ",
        value: [item.pulseTypeRaw],
      },
    );
  }

  return {
    dataType: "LOGS",
    timeRange: { start: SESSION_LOGS_TIME_START_UTC, end },
    select: [
      {
        function: "CUSTOM",
        param: {
          expression: `toJSONString(${COLUMN_NAME.LOG_ATTRIBUTES})`,
        },
        alias: LogDataQueryAlias.LOG_ATTRIBUTES_JSON,
      },
    ],
    filters,
    limit: 3,
  };
}

/**
 * Fetches `LogAttributes` for one log row (used when the user expands details).
 */
export async function fetchBreadcrumbLogAttributes(
  sessionId: string,
  item: BreadcrumbItem,
): Promise<Record<string, unknown>> {
  const body = withIsoTimeRange(buildLogAttributesDetailQuery(sessionId, item));
  const res = await makeRequest<DataQueryResponse>({
    url: `${API_BASE_URL}${API_ROUTES.DATA_QUERY.apiPath}`,
    init: {
      method: API_ROUTES.DATA_QUERY.method,
      body: JSON.stringify(body),
    },
  });
  if (res.error) {
    const msg = getErrorMessage(classifyError(res, res.status));
    throw new Error(msg || "Failed to load log attributes");
  }
  const fields = res.data?.fields ?? [];
  const rows = res.data?.rows ?? [];
  const ix = (name: string) => fields.indexOf(name);
  const attrsI = ix(LogDataQueryAlias.LOG_ATTRIBUTES_JSON);
  const first = rows[0];
  if (!first) return {};
  const rawAttrs = first[attrsI] ?? "";
  if (!rawAttrs) return {};
  try {
    return JSON.parse(rawAttrs) as Record<string, unknown>;
  } catch {
    return {};
  }
}

interface UseOccurrenceBreadcrumbLogsParams {
  sessionId: string;
  errorTimestamp: Date | null;
  enabled?: boolean;
}

/**
 * Paginated otel_logs for the session (DESC by time, 50 per page). Merged list is chronological ASC for the timeline.
 */
export function useOccurrenceBreadcrumbLogs({
  sessionId,
  errorTimestamp,
  enabled = true,
}: UseOccurrenceBreadcrumbLogsParams) {
  const logsEndWall = dayjs.utc().format("YYYY-MM-DD HH:mm:ss");
  const formattedStart = tryFormatTimeToIso(SESSION_LOGS_TIME_START_UTC);
  const formattedEnd = tryFormatTimeToIso(logsEndWall);
  const hasValidTimeRange = formattedStart != null && formattedEnd != null;
  const isProjectReady = useProjectQueryEnabled(
    enabled && !!sessionId && !!errorTimestamp && hasValidTimeRange,
  );

  const errorTimeKey = errorTimestamp?.toISOString() ?? "";

  const infinite = useInfiniteQuery<BreadcrumbLogsPage, Error>({
    queryKey: ["SESSION_BREADCRUMB_LOGS", sessionId, errorTimeKey, PAGE_SIZE],
    initialPageParam: 0,
    enabled: isProjectReady,
    queryFn: async ({ pageParam }): Promise<BreadcrumbLogsPage> => {
      const offset = pageParam as number;
      const body = withIsoTimeRange(
        buildOccurrenceLogsQuery(sessionId, offset),
      );
      return makeRequest<DataQueryResponse>({
        url: `${API_BASE_URL}${API_ROUTES.DATA_QUERY.apiPath}`,
        init: {
          method: API_ROUTES.DATA_QUERY.method,
          body: JSON.stringify(body),
        },
      });
    },
    getNextPageParam: (lastPage, _allPages, lastPageParam) => {
      if (lastPage.error || !lastPage.data?.rows) return undefined;
      const n = lastPage.data.rows.length;
      if (n < PAGE_SIZE) return undefined;
      return (lastPageParam as number) + PAGE_SIZE;
    },
    staleTime: 10000,
  });

  const breadcrumbs = useMemo((): BreadcrumbItem[] => {
    const infiniteData = infinite.data as
      | BreadcrumbLogsInfiniteData
      | undefined;
    if (!errorTimestamp || !infiniteData?.pages?.length) return [];
    const pages = infiniteData.pages;
    const firstOk = pages.find((p) => p.data?.fields?.length && !p.error);
    if (!firstOk?.data?.fields) return [];
    const fields = firstOk.data.fields;
    const rowsDesc = pages.flatMap((p) =>
      !p.error && p.data?.rows?.length ? p.data.rows : [],
    );
    const rowsAsc = [...rowsDesc].reverse();
    return mapRowsToBreadcrumbs(fields, rowsAsc, errorTimestamp);
  }, [infinite.data, errorTimestamp]);

  const queryState = useMemo(() => {
    const infiniteData = infinite.data as
      | BreadcrumbLogsInfiniteData
      | undefined;
    const firstPage = infiniteData?.pages[0];
    const isLoading = infinite.isPending && !infiniteData?.pages?.length;
    let isError = false;
    let errorMessage: string | undefined;
    if (firstPage?.error) {
      isError = true;
      errorMessage = getErrorMessage(
        classifyError(firstPage, firstPage.status),
      );
    } else if (infinite.isError && infinite.error) {
      isError = true;
      errorMessage = getErrorMessage(classifyError(infinite.error, undefined));
    }
    return { isLoading, isError, errorMessage };
  }, [infinite.isPending, infinite.data, infinite.isError, infinite.error]);

  return {
    breadcrumbs,
    queryState: {
      isLoading: queryState.isLoading,
      isError: queryState.isError,
      errorMessage: queryState.errorMessage,
      isLoadingMore: infinite.isFetchingNextPage,
    },
    hasMore: !!infinite.hasNextPage,
    fetchNextPage: () => {
      void infinite.fetchNextPage();
    },
  };
}
