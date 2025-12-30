import { useMemo } from "react";
import { useGetDataQuery } from "../../../../../hooks";
import { useQueryError } from "../../../../../hooks/useQueryError";
import type {
  DataQueryResponse,
  SelectField,
} from "../../../../../hooks/useGetDataQuery/useGetDataQuery.interface";
import type {
  CrashIssue,
  ANRIssue,
  NonFatalIssue,
} from "../../../AppVitals.interface";
import { useExceptionTimestamps } from "./useExceptionTimestamps";
import { COLUMN_NAME } from "../../../../../constants/PulseOtelSemcov";
export type ExceptionType = "crash" | "anr" | "nonfatal";

interface UseExceptionListDataParams {
  startTime: string;
  endTime: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
  screenName?: string;
  exceptionType: ExceptionType;
}

type ExceptionIssue = CrashIssue | ANRIssue | NonFatalIssue;

export function useExceptionListData({
  startTime,
  endTime,
  appVersion = "all",
  osVersion = "all",
  device = "all",
  screenName,
  exceptionType,
}: UseExceptionListDataParams) {
  // Build filters array for API request
  const filters = useMemo(() => {
    const filterArray = [];

    // Add event type filter based on exception type
    if (exceptionType === "crash") {
      filterArray.push({
        field: "PulseType",
        operator: "EQ" as const,
        value: ["device.crash"],
      });
    } else if (exceptionType === "anr") {
      filterArray.push({
        field: "PulseType",
        operator: "EQ" as const,
        value: ["device.anr"],
      });
    } else if (exceptionType === "nonfatal") {
      filterArray.push({
        field: "PulseType",
        operator: "EQ" as const,
        value: ["non_fatal"],
      });
    }

    // Add screen name filter if provided
    if (screenName) {
      filterArray.push({
        field: "ScreenName",
        operator: "EQ" as const,
        value: [screenName],
      });
    }

    // Add other filters
    if (appVersion && appVersion !== "all") {
      filterArray.push({
        field: COLUMN_NAME.APP_VERSION,
        operator: "EQ" as const,
        value: [appVersion],
      });
    }

    if (osVersion && osVersion !== "all") {
      filterArray.push({
        field: "OsVersion",
        operator: "EQ" as const,
        value: [osVersion],
      });
    }

    if (device && device !== "all") {
      filterArray.push({
        field: "DeviceModel",
        operator: "EQ" as const,
        value: [device],
      });
    }

    return filterArray.length > 0 ? filterArray : undefined;
  }, [appVersion, osVersion, device, screenName, exceptionType]);

  // Build select fields - include error_type for non-fatal
  const selectFields = useMemo((): SelectField[] => {
    const baseFields: SelectField[] = [
      {
        function: "COL",
        param: {
          field: "GroupId",
        },
        alias: "group_id",
      },
      {
        function: "COL",
        param: {
          field: "Title",
        },
        alias: "title",
      },
    ];

      baseFields.push({
        function: "COL",
        param: {
          field: COLUMN_NAME.EXCEPTION_TYPE,
        },
        alias: "error_type",
      });

    // Add common fields
    baseFields.push(
      {
        function: "CUSTOM",
        param: {
          expression: `arrayStringConcat(groupUniqArray(${COLUMN_NAME.APP_VERSION}), ', ')`,
        },
        alias: "app_versions",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "count()",
        },
        alias: "occurrences",
      },
      {
        function: "CUSTOM",
        param: {
          expression: `uniqCombined(${COLUMN_NAME.USER_ID})`,
        },
        alias: "affected_users",
      },
    );

    return baseFields;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [exceptionType]);

  // Fetch exception list data grouped by GroupId
  const queryResult = useGetDataQuery({
    requestBody: {
      dataType: "EXCEPTIONS",
      timeRange: {
        start: startTime,
        end: endTime,
      },
      filters,
      select: selectFields,
      groupBy: ["group_id", "title", "error_type"],
      orderBy: [
        {
          field: "occurrences",
          direction: "DESC",
        },
      ],
      limit: 10,
    },
    enabled: !!startTime && !!endTime,
  });

  const { data } = queryResult;
  const queryState = useQueryError<DataQueryResponse>({ queryResult });

  // Extract GroupIds for timestamp query (must be done outside useMemo)
  // Use a stable reference by sorting and creating a string key
  const groupIds = useMemo(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return [];
    }
    const fields = responseData.fields;
    const groupIdIndex = fields.indexOf("group_id");
    const ids = responseData.rows
      .map((row) => row[groupIdIndex] || "")
      .filter((id) => id.length > 0);
    // Sort to ensure stable reference
    return [...ids].sort();
  }, [data]);

  // Determine eventName for timestamp query
  const eventNameForTimestamps =
    exceptionType === "crash"
      ? "device.crash"
      : exceptionType === "anr"
        ? "device.anr"
        : undefined;

  // Fetch timestamps separately (called at top level)
  const { timestampsMap } = useExceptionTimestamps({
    groupIds,
    appVersion,
    osVersion,
    device,
    screenName,
    eventName: eventNameForTimestamps,
  });

  // Transform API response to appropriate issue format
  const exceptions: ExceptionIssue[] = useMemo(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return [];
    }

    const fields = responseData.fields;
    const groupIdIndex = fields.indexOf("group_id");
    const appVersionsIndex = fields.indexOf("app_versions");
    const occurrencesIndex = fields.indexOf("occurrences");
    const affectedUsersIndex = fields.indexOf("affected_users");
    const titleIndex = fields.indexOf("title");
    const errorTypeIndex =
      exceptionType === "nonfatal" ? fields.indexOf("error_type") : -1;

    return responseData.rows.map((row, index) => {
      const groupId = row[groupIdIndex] || "";
      const appVersions = row[appVersionsIndex] || "";
      const occurrences = parseFloat(row[occurrencesIndex]) || 0;
      const affectedUsers = parseFloat(row[affectedUsersIndex]) || 0;
      const title = row[titleIndex] || "";

      // Use GroupId as the ID
      const id =
        groupId ||
        `${exceptionType}-${btoa(title || `exception-${index}`)
          .replace(/[+/=]/g, "")
          .substring(0, 16)}-${index}`;

      // Get timestamps from separate query
      const timestamps = timestampsMap.get(groupId);
      const firstSeen = timestamps?.firstSeen || "";
      const lastSeen = timestamps?.lastSeen || "";

      // Transform based on exception type
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
      } else if (exceptionType === "anr") {
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
      } else {
        // Non-fatal
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
      }
    });
  }, [data, timestampsMap, exceptionType]);

  return {
    exceptions,
    queryState,
  };
}
