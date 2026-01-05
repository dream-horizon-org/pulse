import TopIssuesCharts, { SectionConfig } from "../components/TopIssuesCharts";
import { ErrorAndEmptyStateWithNotification } from "../../ErrorAndEmptyStateWithNotification";
import { AnalysisSectionSkeleton } from "../../../../../../../components/Skeletons";
import {
  ANALYSIS_ERROR_MESSAGES,
} from "../Analysis.constants";
import { AnalysisSectionProps } from "../Analysis.interface";
import { useGetLatencyAnalysis } from "../hooks/useGetLatencyAnalysis";
import { useMemo } from "react";

const latencyFormatter = (p: { value: number }) => `${p.value}ms`;

export const LatencyAnalysisSection: React.FC<AnalysisSectionProps> = ({
  dashboardFilters,
  startTimeMs,
  endTimeMs,
  shouldFetch,
  interactionName,
}) => {
  const {
    latencyAnalysisData,
    isLoading,
    isError,
    error,
  } = useGetLatencyAnalysis({
    interactionName,
    startTime: startTimeMs,
    endTime: endTimeMs,
    enabled: shouldFetch,
    dashboardFilters,
  });

  const { latencyByNetwork, latencyByDevice, latencyByOS } = latencyAnalysisData;

  const sections = useMemo((): SectionConfig[] => {
    if (
      !latencyByNetwork.length &&
      !latencyByDevice.length &&
      !latencyByOS.length
    ) {
      return [];
    }

    return [
      {
        title: "Latency Analysis (P95)",
        description: "Response time breakdown by network providers, devices, and OS versions",
        charts: [
          {
            title: "Latency by Network Provider",
            description: "Average response time by network provider",
            data: latencyByNetwork,
            yAxisDataKey: "networkProvider",
            valueKey: "latency",
            seriesName: "Latency",
            xAxisName: "Latency (ms)",
            labelFormatter: latencyFormatter,
          },
          {
            title: "Latency by Device Model",
            description: "Average response time by device model",
            data: latencyByDevice,
            yAxisDataKey: "device",
            valueKey: "latency",
            seriesName: "Latency",
            xAxisName: "Latency (ms)",
            labelFormatter: latencyFormatter,
          },
          {
            title: "Latency by OS Version",
            description: "Average response time by OS version",
            data: latencyByOS,
            yAxisDataKey: "os",
            valueKey: "latency",
            seriesName: "Latency",
            xAxisName: "Latency (ms)",
            labelFormatter: latencyFormatter,
          },
        ],
      },
    ];
  }, [latencyByNetwork, latencyByDevice, latencyByOS]);

  if (isError) {
    const errorMessage = error?.message || "Unknown error";
    return (
      <ErrorAndEmptyStateWithNotification
        message={ANALYSIS_ERROR_MESSAGES.LATENCY.ERROR}
        errorDetails={errorMessage}
      />
    );
  }

  if (isLoading) {
    return <AnalysisSectionSkeleton chartCount={3} layout="grid" chartHeight={250} />;
  }

  const hasData =
    latencyByNetwork.length > 0 ||
    latencyByDevice.length > 0 ||
    latencyByOS.length > 0;

  if (!hasData) {
    return (
      <ErrorAndEmptyStateWithNotification
        message={ANALYSIS_ERROR_MESSAGES.LATENCY.EMPTY}
        isError={false}
        showNotification={false}
      />
    );
  }

  return <TopIssuesCharts sections={sections} />;
};
