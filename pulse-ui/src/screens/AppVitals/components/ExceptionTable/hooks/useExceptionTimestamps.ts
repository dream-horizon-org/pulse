import { useMemo, useRef } from "react";
import { useGetDataQuery } from "../../../../../hooks";
import { useQueryError } from "../../../../../hooks/useQueryError";
import type {
  DataQueryResponse,
  SelectField,
} from "../../../../../hooks/useGetDataQuery/useGetDataQuery.interface";
import { COLUMN_NAME } from "../../../../../constants/PulseOtelSemcov";
interface UseExceptionTimestampsParams {
  groupIds: string[];
  appVersion?: string;
  osVersion?: string;
  device?: string;
  screenName?: string;
  eventName?: string; // device.crash, device.anr, or undefined for non-fatal
}

interface ExceptionTimestamp {
  groupId: string;
  firstSeen: string;
  lastSeen: string;
}

/**
 * Fetches firstSeen and lastSeen timestamps for multiple GroupIds
 * Uses a 6-month time range (6 months ago to now)
 */
export function useExceptionTimestamps({
  groupIds,
  appVersion = "all",
  osVersion = "all",
  device = "all",
  screenName,
  eventName,
}: UseExceptionTimestampsParams) {
  // Calculate 6 months ago - memoize to prevent recreation
  const timeRange = useMemo(() => {
    const now = new Date();
    const sixMonthsAgo = new Date(now.getTime() - 6 * 30 * 24 * 60 * 60 * 1000);
    return {
      start: sixMonthsAgo.toISOString(),
      end: now.toISOString(),
    };
  }, []);

  // Create a stable key from groupIds to prevent infinite loops
  // Use a ref to track previous value and only update when content actually changes
  const prevGroupIdsRef = useRef<string>("");
  const groupIdsKey = useMemo(() => {
    if (groupIds.length === 0) {
      if (prevGroupIdsRef.current !== "") {
        prevGroupIdsRef.current = "";
      }
      return "";
    }
    // Sort and join to create a stable string key
    const sorted = [...groupIds].sort();
    const key = sorted.join(",");
    // Only update if the key actually changed
    if (prevGroupIdsRef.current !== key) {
      prevGroupIdsRef.current = key;
    }
    return prevGroupIdsRef.current;
  }, [groupIds]);

  // Build filters - only recreate when groupIdsKey changes (stable reference)
  const filters = useMemo(() => {
    const filterArray = [];

    // Filter by GroupIds - use the actual groupIds array but only recreate when key changes
    if (groupIds.length > 0) {
      filterArray.push({
        field: "GroupId",
        operator: "IN" as const,
        value: groupIds,
      });
    }

    // Filter by EventName if provided
    if (eventName) {
      filterArray.push({
        field: "EventName",
        operator: "EQ" as const,
        value: [eventName],
      });
    } else {
      // For non-fatal, exclude crashes and ANRs
      filterArray.push({
        field: "EventName",
        operator: "EQ" as const,
        value: ["non_fatal"],
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

    if (screenName) {
      filterArray.push({
        field: "ScreenName",
        operator: "EQ" as const,
        value: [screenName],
      });
    }

    return filterArray.length > 0 ? filterArray : undefined;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupIdsKey, appVersion, osVersion, device, screenName, eventName]);

  // Memoize select fields to prevent recreation
  const selectFields = useMemo((): SelectField[] => {
    return [
      {
        function: "COL",
        param: {
          field: "GroupId",
        },
        alias: "group_id",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "min(Timestamp)",
        },
        alias: "first_seen",
      },
      {
        function: "CUSTOM",
        param: {
          expression: "max(Timestamp)",
        },
        alias: "last_seen",
      },
    ];
  }, []);

  // Memoize request body to prevent recreation
  // Only recreate when filters actually change (filters already depends on groupIdsKey)
  const requestBody = useMemo(
    () => ({
      dataType: "EXCEPTIONS" as const,
      timeRange,
      filters,
      select: selectFields,
      groupBy: ["group_id"],
    }),
    [timeRange, filters, selectFields],
  );

  // Fetch timestamps grouped by GroupId
  const queryResult = useGetDataQuery({
    requestBody,
    enabled: groupIds.length > 0,
  });

  const { data } = queryResult;
  const queryState = useQueryError<DataQueryResponse>({ queryResult });

  // Transform API response to map of GroupId -> timestamps
  const timestampsMap = useMemo(() => {
    const responseData = data?.data;
    if (!responseData || !responseData.rows || responseData.rows.length === 0) {
      return new Map<string, ExceptionTimestamp>();
    }

    const fields = responseData.fields;
    const groupIdIndex = fields.indexOf("group_id");
    const firstSeenIndex = fields.indexOf("first_seen");
    const lastSeenIndex = fields.indexOf("last_seen");

    const map = new Map<string, ExceptionTimestamp>();

    responseData.rows.forEach((row) => {
      const groupId = row[groupIdIndex] || "";
      const firstSeen = row[firstSeenIndex] || "";
      const lastSeen = row[lastSeenIndex] || "";

      if (groupId) {
        map.set(groupId, {
          groupId,
          firstSeen,
          lastSeen,
        });
      }
    });

    return map;
  }, [data]);

  return {
    timestampsMap,
    queryState,
  };
}
