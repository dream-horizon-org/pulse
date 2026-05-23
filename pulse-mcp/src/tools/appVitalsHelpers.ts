import axios from "axios";
import { getClient } from "../client.js";
import { COLUMN_NAME } from "./appVitalsConstants.js";

export type ExceptionKind = "crash" | "anr" | "nonfatal";

export interface FilterField {
  field: string;
  operator:
    | "EQ"
    | "IN"
    | "NE"
    | "GT"
    | "LT"
    | "GTE"
    | "LTE"
    | "LIKE"
    | "ADDITIONAL";
  value: string | string[] | number | number[] | boolean | boolean[];
}

export interface SelectField {
  function: string;
  alias: string;
  param?: Record<string, unknown>;
}

export interface DistributionRequestBody {
  dataType: "TRACES" | "EVENTS" | "METRICS" | "LOGS" | "EXCEPTIONS";
  timeRange: { start: string; end: string };
  select: SelectField[];
  groupBy?: string[];
  filters?: FilterField[];
  orderBy?: { field: string; direction: "ASC" | "DESC" }[];
  limit?: number;
}

const MAX_LIST_LIMIT = 100;
const DEFAULT_LIST_LIMIT = 10;

/** Parse UI-style times to UTC ISO (mirrors pulse-ui useGetDataQuery.tryFormatTimeToIso). */
export function tryFormatTimeToIso(time: string): string | null {
  const trimmed = typeof time === "string" ? time.trim() : "";
  if (!trimmed) return null;
  if (trimmed.includes("T") || trimmed.includes("Z")) {
    const d = new Date(trimmed);
    return Number.isNaN(d.getTime()) ? null : d.toISOString();
  }
  const m = trimmed.match(
    /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})/,
  );
  if (m) {
    const d = Date.UTC(
      Number(m[1]),
      Number(m[2]) - 1,
      Number(m[3]),
      Number(m[4]),
      Number(m[5]),
      Number(m[6]),
    );
    if (!Number.isNaN(d)) return new Date(d).toISOString();
  }
  const loose = new Date(trimmed);
  return Number.isNaN(loose.getTime()) ? null : loose.toISOString();
}

export function resolveTimeRange(
  startTime?: string,
  endTime?: string,
): { start: string; end: string } {
  if (startTime && endTime) {
    const s = tryFormatTimeToIso(startTime);
    const e = tryFormatTimeToIso(endTime);
    if (s && e) return { start: s, end: e };
  }
  const end = new Date();
  const start = new Date(end.getTime() - 7 * 24 * 60 * 60 * 1000);
  return { start: start.toISOString(), end: end.toISOString() };
}

export function buildCommonFilters(
  appVersion?: string,
  osVersion?: string,
  device?: string,
  platform?: string,
  networkProvider?: string,
  state?: string,
): FilterField[] {
  const filterArray: FilterField[] = [];
  if (appVersion && appVersion !== "all") {
    filterArray.push({
      field: COLUMN_NAME.APP_VERSION,
      operator: "EQ",
      value: [appVersion],
    });
  }
  if (osVersion && osVersion !== "all") {
    filterArray.push({
      field: COLUMN_NAME.OS_VERSION,
      operator: "EQ",
      value: [osVersion],
    });
  }
  if (device && device !== "all") {
    filterArray.push({
      field: COLUMN_NAME.DEVICE_MODEL,
      operator: "EQ",
      value: [device],
    });
  }
  if (platform && platform !== "all") {
    filterArray.push({
      field: COLUMN_NAME.PLATFORM,
      operator: "EQ",
      value: [platform],
    });
  }
  if (networkProvider && networkProvider !== "all") {
    filterArray.push({
      field: COLUMN_NAME.NETWORK_PROVIDER,
      operator: "EQ",
      value: [networkProvider],
    });
  }
  if (state && state !== "all") {
    filterArray.push({
      field: COLUMN_NAME.STATE,
      operator: "EQ",
      value: [state],
    });
  }
  return filterArray;
}

export function pulseTypeFilter(kind: ExceptionKind): FilterField {
  if (kind === "crash") {
    return { field: "PulseType", operator: "EQ", value: ["device.crash"] };
  }
  if (kind === "anr") {
    return { field: "PulseType", operator: "EQ", value: ["device.anr"] };
  }
  return { field: "PulseType", operator: "EQ", value: ["non_fatal"] };
}

export function buildExceptionListBody(args: {
  kind: ExceptionKind;
  startTime: string;
  endTime: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
  platform?: string;
  networkProvider?: string;
  state?: string;
  screenName?: string;
  limit: number;
}): DistributionRequestBody {
  const { start, end } = resolveTimeRange(args.startTime, args.endTime);
  const filterArray: FilterField[] = [pulseTypeFilter(args.kind)];
  if (args.screenName) {
    filterArray.push({
      field: "ScreenName",
      operator: "EQ",
      value: [args.screenName],
    });
  }
  filterArray.push(
    ...buildCommonFilters(
      args.appVersion,
      args.osVersion,
      args.device,
      args.platform,
      args.networkProvider,
      args.state,
    ),
  );

  const select: SelectField[] = [
    { function: "COL", param: { field: "GroupId" }, alias: "group_id" },
    { function: "COL", param: { field: "Title" }, alias: "title" },
    {
      function: "COL",
      param: { field: COLUMN_NAME.EXCEPTION_TYPE },
      alias: "error_type",
    },
    {
      function: "CUSTOM",
      param: {
        expression: `arrayStringConcat(groupUniqArray(${COLUMN_NAME.APP_VERSION}), ', ')`,
      },
      alias: "app_versions",
    },
    {
      function: "CUSTOM",
      param: { expression: "count()" },
      alias: "occurrences",
    },
    {
      function: "CUSTOM",
      param: {
        expression: `uniqCombined64(nullIf(${COLUMN_NAME.USER_ID}, ''))`,
      },
      alias: "affected_users",
    },
  ];

  const limit = Math.min(Math.max(1, args.limit), MAX_LIST_LIMIT);

  return {
    dataType: "EXCEPTIONS",
    timeRange: { start, end },
    filters: filterArray,
    select,
    groupBy: ["group_id", "title", "error_type"],
    orderBy: [{ field: "occurrences", direction: "DESC" }],
    limit,
  };
}

export type DistributionData = { fields: string[]; rows: string[][] };

export async function postDistribution(
  projectId: string,
  body: DistributionRequestBody,
): Promise<DistributionData> {
  return getClient().post<DistributionData>(
    "/v1/interactions/performance-metric/distribution",
    body,
    projectId,
  );
}

export function formatDistributionJson(
  data: DistributionData,
  emptyHint: string,
): string {
  if (!data.rows || data.rows.length === 0) {
    return JSON.stringify(
      {
        ok: true,
        empty: true,
        hint: emptyHint,
        fields: data.fields ?? [],
        rows: [],
      },
      null,
      2,
    );
  }
  return JSON.stringify({ ok: true, empty: false, ...data }, null, 2);
}

export function formatToolError(e: unknown): string {
  if (axios.isAxiosError(e)) {
    const status = e.response?.status;
    const data = e.response?.data as
      | { message?: string; error?: string }
      | undefined;
    const msg =
      (typeof data?.message === "string" && data.message) ||
      (typeof data?.error === "string" && data.error) ||
      e.message;
    if (status === 403) {
      return `HTTP 403 Forbidden: ${msg}. The API key user may lack project permission (e.g. can_view) for performance-metric/distribution.`;
    }
    if (status === 401) {
      return `HTTP 401 Unauthorized: ${msg}. Token may be expired; pulse-mcp should re-exchange on next request.`;
    }
    if (status === 400) {
      return `HTTP 400 Bad Request: ${msg}`;
    }
    return `HTTP ${status ?? "?"}: ${msg}`;
  }
  if (e instanceof Error) return e.message;
  return String(e);
}

export async function runDistribution(
  projectId: string,
  body: DistributionRequestBody,
  emptyHint: string,
): Promise<{ content: { type: "text"; text: string }[] }> {
  try {
    const data = await postDistribution(projectId, body);
    return {
      content: [
        { type: "text", text: formatDistributionJson(data, emptyHint) },
      ],
    };
  } catch (e) {
    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(
            { ok: false, error: formatToolError(e) },
            null,
            2,
          ),
        },
      ],
    };
  }
}

export { MAX_LIST_LIMIT, DEFAULT_LIST_LIMIT };
