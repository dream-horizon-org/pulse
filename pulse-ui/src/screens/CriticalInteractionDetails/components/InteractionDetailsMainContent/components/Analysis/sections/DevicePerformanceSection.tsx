import TopIssuesCharts, { SectionConfig } from "../components/TopIssuesCharts";
import { ErrorAndEmptyStateWithNotification } from "../../ErrorAndEmptyStateWithNotification";
import { AnalysisSectionSkeleton } from "../../../../../../../components/Skeletons";
import {
  ANALYSIS_ERROR_MESSAGES,
} from "../Analysis.constants";
import { AnalysisSectionProps } from "../Analysis.interface";
import { useGetDevicePerformance } from "../hooks/useGetDevicePerformance";
import { useMemo } from "react";

export const DevicePerformanceSection: React.FC<AnalysisSectionProps> = ({
  dashboardFilters,
  startTimeMs,
  endTimeMs,
  shouldFetch,
  interactionName,
}) => {
  const {
    devicePerformanceData,
    isLoading,
    isError,
    error,
  } = useGetDevicePerformance({
    interactionName,
    startTime: startTimeMs,
    endTime: endTimeMs,
    enabled: shouldFetch,
    dashboardFilters,
  });

  const { crashesByDevice, anrByDevice, frozenFramesByDevice } = devicePerformanceData;

  const sections = useMemo((): SectionConfig[] => {
    if (
      !crashesByDevice.length &&
      !anrByDevice.length &&
      !frozenFramesByDevice.length
    ) {
      return [];
    }

    return [
      {
        title: "Device Performance Analysis",
        description: "Crashes, ANR, and frozen frames breakdown by device models",
        charts: [
          {
            title: "Crashes by Device Model",
            description: "Device models with the highest crash rates",
            data: crashesByDevice,
            yAxisDataKey: "device",
            valueKey: "crashes",
            seriesName: "Crashes",
          },
          {
            title: "ANR by Device Model",
            description: "Application Not Responding events by device",
            data: anrByDevice,
            yAxisDataKey: "device",
            valueKey: "anr",
            seriesName: "ANR",
          },
          {
            title: "Frozen Frames by Device Model",
            description: "UI performance issues by device",
            data: frozenFramesByDevice,
            yAxisDataKey: "device",
            valueKey: "frozenFrames",
            seriesName: "Frozen Frames",
          },
        ],
      },
    ];
  }, [crashesByDevice, anrByDevice, frozenFramesByDevice]);

  if (isError) {
    const errorMessage = error?.message || "Unknown error";
    return (
      <ErrorAndEmptyStateWithNotification
        message={ANALYSIS_ERROR_MESSAGES.DEVICE_PERFORMANCE.ERROR}
        errorDetails={errorMessage}
      />
    );
  }

  if (isLoading) {
    return <AnalysisSectionSkeleton chartCount={3} layout="grid" chartHeight={250} />;
  }

  const hasData =
    crashesByDevice.length > 0 ||
    anrByDevice.length > 0 ||
    frozenFramesByDevice.length > 0;

  if (!hasData) {
    return (
      <ErrorAndEmptyStateWithNotification
        message={ANALYSIS_ERROR_MESSAGES.DEVICE_PERFORMANCE.EMPTY}
        isError={false}
        showNotification={false}
      />
    );
  }

  return <TopIssuesCharts sections={sections} />;
};
