import TopIssuesCharts, { SectionConfig } from "../components/TopIssuesCharts";
import { ErrorAndEmptyStateWithNotification } from "../../ErrorAndEmptyStateWithNotification";
import { AnalysisSectionSkeleton } from "../../../../../../../components/Skeletons";
import {
  ANALYSIS_ERROR_MESSAGES,
} from "../Analysis.constants";
import { AnalysisSectionProps } from "../Analysis.interface";
import { useGetNetworkIssues } from "../hooks/useGetNetworkIssues";
import { useMemo } from "react";

export const NetworkIssuesSection: React.FC<AnalysisSectionProps> = ({
  dashboardFilters,
  startTimeMs,
  endTimeMs,
  shouldFetch,
  interactionName,
}) => {
  const {
    networkIssuesData,
    isLoading,
    isError,
    error,
  } = useGetNetworkIssues({
    interactionName,
    startTime: startTimeMs,
    endTime: endTimeMs,
    enabled: shouldFetch,
    dashboardFilters,
  });

  const {
    connectionTimeoutErrorsByNetwork,
    error5xxByNetwork,
    error4xxByNetwork,
  } = networkIssuesData;

  const sections = useMemo((): SectionConfig[] => {
    if (
      !connectionTimeoutErrorsByNetwork.length &&
      !error5xxByNetwork.length &&
      !error4xxByNetwork.length
    ) {
      return [];
    }

    return [
      {
        title: "Network Issues Analysis",
        description: "Breakdown of network-related errors by provider",
        charts: [
          {
            title: "Connection & Timeout Errors by Network Provider",
            description: "Network providers with the highest connection and timeout issues",
            data: connectionTimeoutErrorsByNetwork,
            yAxisDataKey: "networkProvider",
            valueKey: "errors",
            seriesName: "Connection & Timeout Errors",
          },
          {
            title: "5xx Errors by Network Provider",
            description: "Server-side errors by network provider",
            data: error5xxByNetwork,
            yAxisDataKey: "networkProvider",
            valueKey: "errors",
            seriesName: "5xx Errors",
          },
          {
            title: "4xx Errors by Network Provider",
            description: "Client-side errors by network provider",
            data: error4xxByNetwork,
            yAxisDataKey: "networkProvider",
            valueKey: "errors",
            seriesName: "4xx Errors",
          },
        ],
      },
    ];
  }, [connectionTimeoutErrorsByNetwork, error5xxByNetwork, error4xxByNetwork]);


  if (isError) {
    const errorMessage = error?.message || "Unknown error";
    return (
      <ErrorAndEmptyStateWithNotification
        message={ANALYSIS_ERROR_MESSAGES.NETWORK_ISSUES.ERROR}
        errorDetails={errorMessage}
      />
    );
  }

  if (isLoading) {
    return <AnalysisSectionSkeleton chartCount={3} layout="grid" chartHeight={250} />;
  }

  const hasData =
    connectionTimeoutErrorsByNetwork.length > 0 ||
    error5xxByNetwork.length > 0 ||
    error4xxByNetwork.length > 0;

  if (!hasData) {
    return (
      <ErrorAndEmptyStateWithNotification
        message={ANALYSIS_ERROR_MESSAGES.NETWORK_ISSUES.EMPTY}
        isError={false}
        showNotification={false}
      />
    );
  }

  return <TopIssuesCharts sections={sections} />;
};
