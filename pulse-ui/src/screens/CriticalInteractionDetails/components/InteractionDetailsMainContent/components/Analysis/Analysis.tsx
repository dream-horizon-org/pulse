import { Box, SegmentedControl } from "@mantine/core";
import { useState } from "react";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import classes from "./Analysis.module.css";
import type { AnalysisProps, AnalysisSection } from "./Analysis.interface";
import { ANALYSIS_SECTIONS } from "./Analysis.interface";
import {
  ReleasePerformanceSection,
  RegionalInsightsSection,
  PlatformInsightsSection,
  NetworkIssuesSection,
  DevicePerformanceSection,
  OsPerformanceSection,
  LatencyAnalysisSection,
} from "./sections";

dayjs.extend(utc);

export const Analysis: React.FC<AnalysisProps> = ({
  interactionName,
  dashboardFilters,
  startTime,
  endTime,
}) => {
  const [selectedSection, setSelectedSection] = useState<AnalysisSection>(
    ANALYSIS_SECTIONS.RELEASE,
  );

  const sections = [
    { value: ANALYSIS_SECTIONS.RELEASE, label: "Release Performance" },
    { value: ANALYSIS_SECTIONS.REGIONAL, label: "Regional Insights" },
    { value: ANALYSIS_SECTIONS.PLATFORM, label: "Platform Insights" },
    { value: ANALYSIS_SECTIONS.NETWORK, label: "Network Issues" },
    { value: ANALYSIS_SECTIONS.DEVICE, label: "Device Performance" },
    { value: ANALYSIS_SECTIONS.OS, label: "OS Performance" },
    { value: ANALYSIS_SECTIONS.LATENCY, label: "Latency Analysis" },
  ];

  const onChangeTab = (value: string) => {
    setSelectedSection(value as AnalysisSection);
  };

  return (
    <Box py="md">
      {/* Section Pills */}
      <Box mb="lg">
        <SegmentedControl
          value={selectedSection}
          onChange={onChangeTab}
          data={sections}
          fullWidth={false}
          size="sm"
          className={classes.segmentedControl}
        />
      </Box>

      {selectedSection === ANALYSIS_SECTIONS.RELEASE && (
        <ReleasePerformanceSection
          dashboardFilters={dashboardFilters}
          startTimeMs={startTime || ""}
          endTimeMs={endTime || ""}
          shouldFetch={selectedSection === ANALYSIS_SECTIONS.RELEASE}
          interactionName={interactionName || ""}
        />
      )}

      {selectedSection === ANALYSIS_SECTIONS.REGIONAL && (
        <RegionalInsightsSection
          dashboardFilters={dashboardFilters}
          startTimeMs={startTime || ""}
          endTimeMs={endTime || ""}
          shouldFetch={selectedSection === ANALYSIS_SECTIONS.REGIONAL}
          interactionName={interactionName || ""}
        />
      )}

      {selectedSection === ANALYSIS_SECTIONS.PLATFORM && (
        <PlatformInsightsSection
          dashboardFilters={dashboardFilters}
          startTimeMs={startTime || ""}
          endTimeMs={endTime || ""}
          shouldFetch={selectedSection === ANALYSIS_SECTIONS.PLATFORM}
          interactionName={interactionName || ""}
        />
      )}

      {selectedSection === ANALYSIS_SECTIONS.NETWORK && (
        <NetworkIssuesSection
          dashboardFilters={dashboardFilters}
          startTimeMs={startTime || ""}
          endTimeMs={endTime || ""}
          shouldFetch={selectedSection === ANALYSIS_SECTIONS.NETWORK}
          interactionName={interactionName || ""}
        />
      )}

      {selectedSection === ANALYSIS_SECTIONS.DEVICE && (
        <DevicePerformanceSection
          dashboardFilters={dashboardFilters}
          startTimeMs={startTime || ""}
          endTimeMs={endTime || ""}
          shouldFetch={selectedSection === ANALYSIS_SECTIONS.DEVICE}
          interactionName={interactionName || ""}
        />
      )}

      {selectedSection === ANALYSIS_SECTIONS.OS && (
        <OsPerformanceSection
          dashboardFilters={dashboardFilters}
          startTimeMs={startTime || ""}
          endTimeMs={endTime || ""}
          shouldFetch={selectedSection === ANALYSIS_SECTIONS.OS}
          interactionName={interactionName || ""}
        />
      )}

      {selectedSection === ANALYSIS_SECTIONS.LATENCY && (
        <LatencyAnalysisSection
          dashboardFilters={dashboardFilters}
          startTimeMs={startTime || ""}
          endTimeMs={endTime || ""}
          shouldFetch={selectedSection === ANALYSIS_SECTIONS.LATENCY}
          interactionName={interactionName || ""}
        />
      )}
    </Box>
  );
};
