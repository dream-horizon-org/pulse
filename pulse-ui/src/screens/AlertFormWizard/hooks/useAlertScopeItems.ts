/**
 * Hook to fetch scope items based on scope type
 */

import { useMemo } from "react";
import { useGetInteractions } from "../../../hooks/useGetInteractions";
import { useGetScreenNames } from "../../../hooks/useGetScreenNames";
import { useGetDataQuery, DataQueryRequestBody } from "../../../hooks/useGetDataQuery";
import { AlertScopeType, ScopeItem } from "../types";
import { UI_CONSTANTS } from "../constants";
import dayjs from "dayjs";

interface UseAlertScopeItemsParams {
  scopeType: AlertScopeType | null;
  searchStr?: string;
  enabled?: boolean;
}

const getTimeRange = () => ({
  start: dayjs().subtract(7, "day").toISOString(),
  end: dayjs().toISOString(),
});

export const useAlertScopeItems = ({ scopeType, searchStr = "", enabled = true }: UseAlertScopeItemsParams) => {
  const timeRange = useMemo(getTimeRange, []);
  const limit = UI_CONSTANTS.SCOPE_ITEMS_LIMIT;

  // Interactions
  const interactionsEnabled = enabled && scopeType === AlertScopeType.Interaction;
  const interactionsQuery = useGetInteractions({
    queryParams: { size: limit, interactionName: searchStr || undefined },
    enabled: interactionsEnabled,
  });

  // Screens
  const screensEnabled = enabled && scopeType === AlertScopeType.Screen;
  const { screenNames, isLoading: loadingScreens } = useGetScreenNames({
    startTime: timeRange.start,
    endTime: timeRange.end,
    searchStr,
    enabled: screensEnabled,
  });

  // Network APIs
  const networkEnabled = enabled && scopeType === AlertScopeType.NetworkAPI;
  const networkRequestBody = useMemo((): DataQueryRequestBody => ({
    dataType: "TRACES",
    timeRange: { start: timeRange.start, end: timeRange.end },
    select: [
      { function: "COL", param: { field: "SpanAttributes['http.url']" }, alias: "url" },
      { function: "CUSTOM", param: { expression: "COUNT()" }, alias: "count" },
    ],
    groupBy: ["url"],
    orderBy: [{ field: "count", direction: "DESC" }],
    limit,
    filters: [{ field: "SpanType", operator: "LIKE", value: ["network%"] }],
  }), [timeRange, limit]);

  const { data: networkData, isLoading: loadingNetwork } = useGetDataQuery({
    requestBody: networkRequestBody,
    enabled: networkEnabled,
  });

  // Transform to ScopeItem[]
  const items = useMemo((): ScopeItem[] => {
    if (scopeType === AlertScopeType.Interaction && interactionsQuery.data?.data?.interactions) {
      const interactions = interactionsQuery.data.data.interactions;
      return interactions.slice(0, limit).map((i) => ({ id: i.name || "", name: i.name || "", displayLabel: i.name || "" }));
    }
    if (scopeType === AlertScopeType.Screen && screenNames) {
      return screenNames.slice(0, limit).map((name) => ({ id: name, name, displayLabel: name }));
    }
    if (scopeType === AlertScopeType.NetworkAPI && networkData?.data?.rows) {
      const urlIdx = networkData.data.fields.indexOf("url");
      return networkData.data.rows.slice(0, limit).map((row) => {
        const url = String(row[urlIdx] || "");
        return { id: url, name: url, displayLabel: url };
      });
    }
    return [];
  }, [scopeType, interactionsQuery.data, screenNames, networkData, limit]);

  const isLoading = (scopeType === AlertScopeType.Interaction && interactionsQuery.isLoading) ||
    (scopeType === AlertScopeType.Screen && loadingScreens) ||
    (scopeType === AlertScopeType.NetworkAPI && loadingNetwork);

  return { items, isLoading, isAppVitals: scopeType === AlertScopeType.AppVitals };
};
