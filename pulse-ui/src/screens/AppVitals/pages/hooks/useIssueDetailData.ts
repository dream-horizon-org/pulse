import { useMemo } from "react";
import { useGetDataQuery } from "../../../../hooks";
import { useQueryError } from "../../../../hooks/useQueryError";
import type { DataQueryResponse } from "../../../../hooks/useGetDataQuery/useGetDataQuery.interface";
import type {
  CrashIssue,
  ANRIssue,
  NonFatalIssue,
} from "../../AppVitals.interface";
import { COLUMN_NAME } from "../../../../constants/PulseOtelSemcov";
interface UseIssueDetailDataParams {
  groupId: string;
  startTime?: string;
  endTime?: string;
}

export function useIssueDetailData({
  groupId,
  startTime,
  endTime,
}: UseIssueDetailDataParams) {
  // Build filters - filter by GroupId
  const filters = useMemo(() => {
    const filterArray = [];

    // Filter by GroupId
    if (groupId) {
      filterArray.push({
        field: "GroupId",
        operator: "EQ" as const,
        value: [groupId],
      });
    }

    return filterArray.length > 0 ? filterArray : undefined;
  }, [groupId]);

  // Memoize time range to prevent unnecessary re-renders
  const timeRange = useMemo(() => {
    if (startTime && endTime) {
      return {
        start: startTime,
        end: endTime,
      };
    }
    // Default to last 7 days
    return {
      start: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
      end: new Date().toISOString(),
    };
  }, [startTime, endTime]);

  // Memoize request body
  const requestBody = useMemo(
    () => ({
      dataType: "EXCEPTIONS" as const,
      timeRange,
      filters,
      select: [
        {
          function: "COL" as const,
          param: {
            field: "GroupId",
          },
          alias: "group_id",
        },
        {
          function: "COL" as const,
          param: {
            field: "PulseType",
          },
          alias: "event_name",
        },
        {
          function: "COL" as const,
          param: {
            field: "ExceptionMessage",
          },
          alias: "error_message",
        },
        {
          function: "COL" as const,
          param: {
            field: "ExceptionType",
          },
          alias: "error_type",
        },
        {
          function: "COL" as const,
          param: {
            field: "Title",
          },
          alias: "title",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: `arrayStringConcat(groupUniqArray(${COLUMN_NAME.APP_VERSION}), ', ')`,
          },
          alias: "app_versions",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "count()",
          },
          alias: "occurrences",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "min(Timestamp)",
          },
          alias: "first_seen",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "max(Timestamp)",
          },
          alias: "last_seen",
        },
        {
          function: "CUSTOM" as const,
          param: {
            expression: "uniqCombined(UserId)",
          },
          alias: "affected_users",
        },
      ],
      groupBy: ["group_id", "event_name", "error_message", "error_type", "title"],
      orderBy: [
        {
          field: "occurrences",
          direction: "DESC" as const,
        },
      ],
      limit: 1,
    }),
    [timeRange, filters],
  );

  // Fetch top 10 crashes for this GroupId (ordered by occurrence)
  const summaryQuery = useGetDataQuery({
    requestBody,
    enabled: !!groupId,
  });

  const { data: summaryData } = summaryQuery;
  const summaryQueryState = useQueryError<DataQueryResponse>({
    queryResult: summaryQuery,
  });

  // Find the matching issue from the summary
  const issue = useMemo(() => {
    const responseData = summaryData?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return null;
    }

    const fields = responseData.fields;
    const eventNameIndex = fields.indexOf("event_name");
    const errorMessageIndex = fields.indexOf("error_message");
    const errorTypeIndex = fields.indexOf("error_type");
    const appVersionsIndex = fields.indexOf("app_versions");
    const occurrencesIndex = fields.indexOf("occurrences");
    const firstSeenIndex = fields.indexOf("first_seen");
    const lastSeenIndex = fields.indexOf("last_seen");
    const affectedUsersIndex = fields.indexOf("affected_users");
    const titleIndex = fields.indexOf("title");

    // Get the first row (top occurrence) for this GroupId
    const row = responseData.rows[0];

    const eventName = row[eventNameIndex] || "";
    const errorMessage = row[errorMessageIndex] || "";
    const errorType = row[errorTypeIndex] || "";
    const appVersions = row[appVersionsIndex] || "";
    const occurrences = parseFloat(row[occurrencesIndex]) || 0;
    const firstSeen = row[firstSeenIndex] || "";
    const lastSeen = row[lastSeenIndex] || "";
    const affectedUsers = parseFloat(row[affectedUsersIndex]) || 0;
    const title =
      row[titleIndex] || errorMessage.split(" at ")[0] || errorMessage;

    // Use GroupId as the ID
    const id = groupId;

    // Determine issue type from PulseType
    let issueType: "crash" | "anr" | "nonfatal" = "crash";
    if (eventName === "device.anr") {
      issueType = "anr";
    } else if (
      eventName &&
      eventName !== "device.crash" &&
      eventName !== "device.anr"
    ) {
      issueType = "nonfatal";
    }

    if (issueType === "crash") {
      return {
        id,
        title,
        message: errorMessage,
        errorMessage,
        stackTrace: "",
        affectedUsers: Math.round(affectedUsers),
        occurrences: Math.round(occurrences),
        firstSeen: firstSeen || new Date().toISOString(),
        lastSeen: lastSeen || new Date().toISOString(),
        appVersion: appVersions || "Unknown",
        osVersion: "Various",
        device: "Various",
        trend: [],
      } as CrashIssue;
    } else if (issueType === "anr") {
      return {
        id,
        title,
        message: errorMessage,
        anrMessage: errorMessage,
        affectedUsers: Math.round(affectedUsers),
        occurrences: Math.round(occurrences),
        firstSeen: firstSeen || new Date().toISOString(),
        lastSeen: lastSeen || new Date().toISOString(),
        appVersion: appVersions || "Unknown",
        osVersion: "Various",
        device: "Various",
        trend: [],
      } as ANRIssue;
    } else {
      return {
        id,
        title,
        message: errorMessage,
        errorMessage,
        type: errorType,
        issueType: errorType,
        affectedUsers: Math.round(affectedUsers),
        occurrences: Math.round(occurrences),
        firstSeen: firstSeen || new Date().toISOString(),
        lastSeen: lastSeen || new Date().toISOString(),
        appVersion: appVersions || "Unknown",
        osVersion: "Various",
        device: "Various",
        trend: [],
      } as NonFatalIssue;
    }
  }, [summaryData, groupId]);

  return {
    issue,
    queryState: summaryQueryState,
  };
}
