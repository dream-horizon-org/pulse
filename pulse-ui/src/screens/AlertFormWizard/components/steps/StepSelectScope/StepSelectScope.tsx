/**
 * Step 2: Select Scope Type
 * API: GET /v1/alert/scopes
 */

import React, { useCallback } from "react";
import { Box, Text, Loader, Alert } from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";
import { useAlertFormContext } from "../../../context";
import { AlertScopeType } from "../../../types";
import { useGetAlertScopes } from "../../../../../hooks/useGetAlertScopes";
import { StepHeader } from "../StepHeader";
import { ScopeCard } from "./ScopeCard";
import { StepSelectScopeProps } from "./StepSelectScope.interface";
import classes from "./StepSelectScope.module.css";

// Display config for scope types
const SCOPE_DISPLAY_CONFIG: Record<string, { color: string; features: string[] }> = {
  interaction: { color: "#0ec9c2", features: ["Track journeys", "Monitor conversions", "Performance alerts"] },
  screen: { color: "#4c6ef5", features: ["Load duration", "Rendering metrics", "UX monitoring"] },
  app_vitals: { color: "#f03e3e", features: ["Crash rate", "ANR detection", "Stability alerts"] },
  network_api: { color: "#7950f2", features: ["Response time", "Error tracking", "Throughput alerts"] },
};

export const StepSelectScope: React.FC<StepSelectScopeProps> = ({ className }) => {
  const { formData, updateStepData } = useAlertFormContext();
  const selectedScope = formData.scopeType.scopeType;

  const { data: scopesResponse, isLoading, error } = useGetAlertScopes();
  const scopes = scopesResponse?.data?.scopes || [];

  const handleSelect = useCallback((scopeType: AlertScopeType) => {
    updateStepData("scopeType", { scopeType });
    updateStepData("scopeItems", { availableScopeItems: [], selectedScopeItems: [] });
    updateStepData("scopeNames", { selectedScopeNames: [] });
    updateStepData("metricsConditions", { selectedScopeNames: [], conditions: [] });
  }, [updateStepData]);

  if (isLoading) {
    return (
      <Box className={`${classes.container} ${className || ""}`}>
        <Box className={classes.loadingState}>
          <Loader size="md" color="teal" />
          <Text size="sm" c="dimmed">Loading scope types...</Text>
        </Box>
      </Box>
    );
  }

  if (error) {
    return (
      <Box className={`${classes.container} ${className || ""}`}>
        <Alert icon={<IconAlertCircle size={16} />} title="Error" color="red">
          Failed to load scope types. Please try again.
        </Alert>
      </Box>
    );
  }

  return (
    <Box className={`${classes.container} ${className || ""}`}>
      <StepHeader title="Select Scope Type" description="Choose what type of entity you want to monitor" />

      <Box className={classes.grid}>
        {scopes.map((scope) => {
          const config = SCOPE_DISPLAY_CONFIG[scope.id] || { color: "#868e96", features: [] };
          return (
            <ScopeCard
              key={scope.id}
              id={scope.id as AlertScopeType}
              label={scope.label}
              description={`Monitor ${scope.label.toLowerCase()} metrics`}
              features={config.features}
              color={config.color}
              isSelected={selectedScope === scope.id}
              onClick={() => handleSelect(scope.id as AlertScopeType)}
            />
          );
        })}
      </Box>
    </Box>
  );
};
