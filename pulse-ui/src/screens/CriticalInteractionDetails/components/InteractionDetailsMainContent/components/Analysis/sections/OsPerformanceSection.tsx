import TopIssuesCharts, { SectionConfig } from "../components/TopIssuesCharts";
import { ErrorAndEmptyStateWithNotification } from "../../ErrorAndEmptyStateWithNotification";
import { AnalysisSectionSkeleton } from "../../../../../../../components/Skeletons";
import {
  ANALYSIS_ERROR_MESSAGES,
} from "../Analysis.constants";
import { AnalysisSectionProps } from "../Analysis.interface";
import { useGetOsPerformance } from "../hooks/useGetOsPerformance";
import { useMemo } from "react";

export const OsPerformanceSection: React.FC<AnalysisSectionProps> = ({
  dashboardFilters,
  startTimeMs,
  endTimeMs,
  shouldFetch,
  interactionName,
}) => {
  const {
    osPerformanceData,
    isLoading,
    isError,
    error,
  } = useGetOsPerformance({
    interactionName,
    startTime: startTimeMs,
    endTime: endTimeMs,
    enabled: shouldFetch,
    dashboardFilters,
  });

  const { crashesByOS, anrByOS, frozenFramesByOS } = osPerformanceData;

  const sections = useMemo((): SectionConfig[] => {
    if (!crashesByOS.length && !anrByOS.length && !frozenFramesByOS.length) {
      return [];
    }

    return [
      {
        title: "OS Performance Analysis",
        description: "Crashes, ANR, and frozen frames breakdown by OS versions",
        charts: [
          {
            title: "Crashes by OS Version",
            description: "OS versions with the highest crash rates",
            data: crashesByOS,
            yAxisDataKey: "os",
            valueKey: "crashes",
            seriesName: "Crashes",
          },
          {
            title: "ANR by OS Version",
            description: "Application Not Responding events by OS",
            data: anrByOS,
            yAxisDataKey: "os",
            valueKey: "anr",
            seriesName: "ANR",
          },
          {
            title: "Frozen Frames by OS Version",
            description: "UI performance issues by OS version",
            data: frozenFramesByOS,
            yAxisDataKey: "os",
            valueKey: "frozenFrames",
            seriesName: "Frozen Frames",
          },
        ],
      },
    ];
  }, [crashesByOS, anrByOS, frozenFramesByOS]);

  if (isError) {
    const errorMessage = error?.message || "Unknown error";
    return (
      <ErrorAndEmptyStateWithNotification
        message={ANALYSIS_ERROR_MESSAGES.OS_PERFORMANCE.ERROR}
        errorDetails={errorMessage}
      />
    );
  }

  if (isLoading) {
    return <AnalysisSectionSkeleton chartCount={3} layout="grid" chartHeight={250} />;
  }

  const hasData =
    crashesByOS.length > 0 || anrByOS.length > 0 || frozenFramesByOS.length > 0;

  if (!hasData) {
    return (
      <ErrorAndEmptyStateWithNotification
        message={ANALYSIS_ERROR_MESSAGES.OS_PERFORMANCE.EMPTY}
        isError={false}
        showNotification={false}
      />
    );
  }

  return <TopIssuesCharts sections={sections} />;
};
