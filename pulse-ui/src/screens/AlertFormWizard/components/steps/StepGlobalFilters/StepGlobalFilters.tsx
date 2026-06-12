/**
 * Step 4: Global Filters
 * Uses FilterBuilder for complex dimension filter expressions
 */

import React, { useCallback, useMemo } from "react";
import { Box } from "@mantine/core";
import { useAlertFormContext } from "../../../context";
import { useGetTelemetryFilters } from "../../../../../hooks/useGetTelemetryFilters";
import { StepHeader } from "../StepHeader";
import { FilterBuilder, FilterBuilderData } from "./FilterBuilder";
import classes from "./StepGlobalFilters.module.css";

export interface StepGlobalFiltersProps { className?: string; }

export const StepGlobalFilters: React.FC<StepGlobalFiltersProps> = ({ className }) => {
  const { formData, updateStepData } = useAlertFormContext();
  const { filterBuilder } = formData.globalFilters;

  const { data: filtersResponse, isLoading } = useGetTelemetryFilters();
  const filtersData = filtersResponse?.data;

  // Fields match TelemetryFilterOptionsResponse from backend
  const availableFields = useMemo(() => [
    { value: "AppVersion", label: "App Version", options: filtersData?.appVersionCodes || [] },
    { value: "Platform", label: "Platform", options: filtersData?.platforms || [] },
    { value: "OsVersion", label: "OS Version", options: filtersData?.osVersions || [] },
    { value: "GeoState", label: "State / Region", options: filtersData?.states || [] },
    { value: "NetworkProvider", label: "Network Provider", options: filtersData?.networkProviders || [] },
    { value: "DeviceModel", label: "Device Model", options: filtersData?.deviceModels || [] },
  ], [filtersData]);

  const handleFilterChange = useCallback((newValue: FilterBuilderData) => {
    updateStepData("globalFilters", { filterBuilder: newValue });
  }, [updateStepData]);

  return (
    <Box className={`${classes.container} ${className || ""}`}>
      <StepHeader 
        title="Global Filters" 
        description="Build complex filter expressions. Leave empty to alert on all dimensions." 
      />

      <FilterBuilder
        value={filterBuilder}
        onChange={handleFilterChange}
        availableFields={availableFields}
        isLoading={isLoading}
      />
    </Box>
  );
};
