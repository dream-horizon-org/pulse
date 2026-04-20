import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { generatePath } from "react-router-dom";
import { ROUTES } from "../../../../constants";
import { filtersToQueryString } from "../../../../helpers/filtersToQueryString";
import type { RcaHeatmapFiltersWireV1 } from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";

dayjs.extend(utc);

/** UTC day range as strings expected by `useFilterStore` / screen detail URL. */
export function resolveHeatmapEvidenceUtcRange(
  filters: RcaHeatmapFiltersWireV1 | null | undefined,
): { start: string; end: string } {
  const from = filters?.from_date?.trim();
  const to = filters?.to_date?.trim();
  if (!from || !to) {
    return { start: "", end: "" };
  }
  return {
    start: dayjs.utc(from).startOf("day").format("YYYY-MM-DD HH:mm:ss"),
    end: dayjs.utc(to).endOf("day").format("YYYY-MM-DD HH:mm:ss"),
  };
}

/** Screen detail → Heatmap tab with filters aligned to RCA segment window. */
export function buildRcaHeatmapEvidenceHref(
  projectId: string,
  screenName: string,
  heatmapFilters: RcaHeatmapFiltersWireV1 | null | undefined,
): string {
  const params: Record<string, string> = {
    tab: "heatmap",
    quickDateFilter: "-1",
  };
  if (heatmapFilters?.platform) {
    params.PLATFORM = heatmapFilters.platform;
  }
  if (heatmapFilters?.app_version) {
    params.APP_VERSION = heatmapFilters.app_version;
  }
  if (heatmapFilters?.geographical_region) {
    params.STATE = heatmapFilters.geographical_region;
  }
  const { start, end } = resolveHeatmapEvidenceUtcRange(heatmapFilters);
  if (start && end) {
    params.startDate = start;
    params.endDate = end;
  }
  const search = filtersToQueryString(params);
  const pathname = generatePath(ROUTES.PROJECT_SCREEN_DETAILS.path, {
    projectId,
    screenName,
  });
  return `${pathname}?${search}`;
}
