import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import type {
  DataQueryRequestBody,
  DataQueryResponse,
  FilterField,
  SelectField,
} from "../../../../../hooks/useGetDataQuery/useGetDataQuery.interface";
import type {
  ANRIssue,
  CrashIssue,
  NonFatalIssue,
} from "../../../AppVitals.interface";
import { COLUMN_NAME } from "../../../../../constants/PulseOtelSemcov";
import { buildCommonFilters } from "../../TrendGraphWithData/helpers/trendDataHelpers";
import { EXCEPTION_LIST_PAGE_SIZE } from "../exceptionList.constants";

dayjs.extend(utc);

export type ExceptionType = "crash" | "anr" | "nonfatal";

export type ExceptionIssue = CrashIssue | ANRIssue | NonFatalIssue;

export interface ExceptionListFilterParams {
  startTime: string;
  endTime: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
  platform?: string;
  networkProvider?: string;
  state?: string;
  screenName?: string;
  exceptionType: ExceptionType;
  /** Debounced; matches Title or AppVersion (OR) via data-query */
  searchQuery?: string;
}

/** Escape single quotes for ADDITIONAL filter SQL literals */
export function escapeExceptionSearchLiteral(term: string): string {
  return term.replace(/'/g, "''");
}

/**
 * OR filter: crash/ANR/non-fatal row matches if Title or AppVersion contains the term
 * (case-insensitive via ClickHouse ILIKE). Applied before groupBy on raw EXCEPTIONS rows.
 */
export function buildExceptionTitleOrVersionSearchFilter(
  searchQuery?: string,
): FilterField | null {
  const term = searchQuery?.trim();
  if (!term) return null;

  const pattern = `%${escapeExceptionSearchLiteral(term)}%`;
  return {
    field: "Additional",
    operator: "ADDITIONAL",
    value: [
      `(Title ILIKE '${pattern}' OR ${COLUMN_NAME.APP_VERSION} ILIKE '${pattern}')`,
    ],
  };
}

export function tryFormatTimeToIso(time: string): string | null {
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

export function withIsoTimeRange(
  body: DataQueryRequestBody,
): DataQueryRequestBody {
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

export function buildExceptionListFilters({
  appVersion = "all",
  osVersion = "all",
  device = "all",
  platform = "all",
  networkProvider = "all",
  state = "all",
  screenName,
  exceptionType,
  searchQuery,
}: Pick<
  ExceptionListFilterParams,
  | "appVersion"
  | "osVersion"
  | "device"
  | "platform"
  | "networkProvider"
  | "state"
  | "screenName"
  | "exceptionType"
  | "searchQuery"
>): FilterField[] | undefined {
  const filterArray: FilterField[] = [];

  if (exceptionType === "crash") {
    filterArray.push({
      field: "PulseType",
      operator: "EQ",
      value: ["device.crash"],
    });
  } else if (exceptionType === "anr") {
    filterArray.push({
      field: "PulseType",
      operator: "EQ",
      value: ["device.anr"],
    });
  } else if (exceptionType === "nonfatal") {
    filterArray.push({
      field: "PulseType",
      operator: "EQ",
      value: ["non_fatal"],
    });
  }

  if (screenName) {
    filterArray.push({
      field: "ScreenName",
      operator: "EQ",
      value: [screenName],
    });
  }

  filterArray.push(
    ...buildCommonFilters(
      appVersion,
      osVersion,
      device,
      platform,
      networkProvider,
      state,
    ),
  );

  const searchFilter = buildExceptionTitleOrVersionSearchFilter(searchQuery);
  if (searchFilter) {
    filterArray.push(searchFilter);
  }

  return filterArray.length > 0 ? filterArray : undefined;
}

export function buildExceptionListSelectFields(): SelectField[] {
  return [
    {
      function: "COL",
      param: { field: "GroupId" },
      alias: "group_id",
    },
    {
      function: "COL",
      param: { field: "Title" },
      alias: "title",
    },
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
        expression: `uniq(nullIf(${COLUMN_NAME.INSTALLATION_ID}, ''))`,
      },
      alias: "affected_users",
    },
  ];
}

export function buildExceptionListRequestBody(
  params: ExceptionListFilterParams,
  offset: number,
  limit: number = EXCEPTION_LIST_PAGE_SIZE,
): DataQueryRequestBody {
  return {
    dataType: "EXCEPTIONS",
    timeRange: {
      start: params.startTime,
      end: params.endTime,
    },
    filters: buildExceptionListFilters(params),
    select: buildExceptionListSelectFields(),
    groupBy: ["group_id", "title", "error_type"],
    orderBy: [{ field: "occurrences", direction: "DESC" }],
    limit,
    offset,
  };
}

export function buildExceptionCountRequestBody(
  params: ExceptionListFilterParams,
): DataQueryRequestBody {
  return {
    dataType: "EXCEPTIONS",
    timeRange: {
      start: params.startTime,
      end: params.endTime,
    },
    filters: buildExceptionListFilters(params),
    select: [
      {
        function: "CUSTOM",
        param: { expression: "uniq(GroupId)" },
        alias: "issue_count",
      },
    ],
  };
}

export function extractGroupIdsFromResponse(
  data: DataQueryResponse | null | undefined,
): string[] {
  if (!data?.rows?.length || !data.fields?.length) {
    return [];
  }
  const groupIdIndex = data.fields.indexOf("group_id");
  if (groupIdIndex < 0) return [];
  const ids = data.rows
    .map((row) => row[groupIdIndex] || "")
    .filter((id) => id.length > 0);
  return [...ids].sort();
}

export function getEventNameForTimestamps(
  exceptionType: ExceptionType,
): string | undefined {
  if (exceptionType === "crash") return "device.crash";
  if (exceptionType === "anr") return "device.anr";
  return undefined;
}

export function mapExceptionRowsToIssues(
  rows: string[][],
  fields: string[],
  exceptionType: ExceptionType,
  timestampsMap: Map<string, { firstSeen: string; lastSeen: string }>,
): ExceptionIssue[] {
  if (!rows.length) return [];

  const groupIdIndex = fields.indexOf("group_id");
  const appVersionsIndex = fields.indexOf("app_versions");
  const occurrencesIndex = fields.indexOf("occurrences");
  const affectedUsersIndex = fields.indexOf("affected_users");
  const titleIndex = fields.indexOf("title");
  const errorTypeIndex =
    exceptionType === "nonfatal" ? fields.indexOf("error_type") : -1;

  return rows.map((row, index) => {
    const groupId = row[groupIdIndex] || "";
    const appVersions = row[appVersionsIndex] || "";
    const occurrences = parseFloat(row[occurrencesIndex]) || 0;
    const affectedUsers = parseFloat(row[affectedUsersIndex]) || 0;
    const title = row[titleIndex] || "";

    const id =
      groupId ||
      `${exceptionType}-${btoa(title || `exception-${index}`)
        .replace(/[+/=]/g, "")
        .substring(0, 16)}-${index}`;

    const timestamps = timestampsMap.get(groupId);
    const firstSeen = timestamps?.firstSeen || "";
    const lastSeen = timestamps?.lastSeen || "";

    if (exceptionType === "crash") {
      return {
        id,
        title,
        message: title,
        errorMessage: title,
        stackTrace: "",
        affectedUsers: Math.round(affectedUsers),
        occurrences: Math.round(occurrences),
        firstSeen,
        lastSeen,
        appVersion: appVersions,
        osVersion: "Various",
        device: "Various",
        trend: [],
      } as CrashIssue;
    }

    if (exceptionType === "anr") {
      return {
        id,
        title,
        message: title,
        anrMessage: title,
        affectedUsers: Math.round(affectedUsers),
        occurrences: Math.round(occurrences),
        trend: [],
        firstSeen: firstSeen || "-",
        lastSeen: lastSeen || "-",
        appVersion: appVersions || "Unknown",
        osVersion: "Unknown",
        device: "Unknown",
      } as ANRIssue;
    }

    const errorType =
      errorTypeIndex >= 0 ? row[errorTypeIndex] || "Unknown" : "Unknown";
    return {
      id,
      title,
      message: title,
      errorMessage: title,
      type: errorType,
      issueType: errorType,
      affectedUsers: Math.round(affectedUsers),
      occurrences: Math.round(occurrences),
      trend: [],
      firstSeen: firstSeen || "-",
      lastSeen: lastSeen || "-",
      appVersion: appVersions || "Unknown",
      osVersion: "Unknown",
      device: "Various",
    } as NonFatalIssue;
  });
}

export function flattenExceptionListPages(
  pages: Array<{
    data?: DataQueryResponse | null;
    error?: unknown;
  }>,
): { fields: string[]; rows: string[][] } {
  const firstOk = pages.find((p) => p.data?.fields?.length && !p.error);
  const fields = firstOk?.data?.fields ?? [];
  const rows = pages.flatMap((p) =>
    !p.error && p.data?.rows?.length ? p.data.rows : [],
  );
  return { fields, rows };
}
