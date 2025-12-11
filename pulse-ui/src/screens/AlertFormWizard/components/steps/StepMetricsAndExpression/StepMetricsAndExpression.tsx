/**
 * Combined Step: Metrics, Thresholds & Expression
 * 
 * Users first select global scope names (with debounced search), then define
 * conditions with thresholds for each scope name.
 */

import React, { useCallback, useMemo, useEffect, useState, useRef } from "react";
import { Box, Text, Button, TextInput, Divider, MultiSelect, Loader } from "@mantine/core";
import { IconPlus } from "@tabler/icons-react";
import { useAlertFormContext } from "../../../context";
import { useGetAlertMetrics } from "../../../../../hooks/useGetAlertMetrics";
import { useGetDataQuery } from "../../../../../hooks/useGetDataQuery";
import { MetricCondition, MetricOperator, isAppVitalsScope, AlertScopeType } from "../../../types";
import { UI_CONSTANTS } from "../../../constants";
import { MetricConditionCard } from "./MetricConditionCard";
import { useDebouncedValue } from "../../../hooks/useDebouncedValue";
import classes from "./StepMetricsAndExpression.module.css";
import sharedClasses from "../shared.module.css";
import dayjs from "dayjs";

export interface StepMetricsAndExpressionProps { className?: string; }

const DEBOUNCE_DELAY = 300;

// Build query for fetching scope names with optional search filter
const buildScopeNamesQuery = (scopeType: AlertScopeType | null, searchTerm?: string) => {
  const timeRange = { start: dayjs().subtract(7, "day").toISOString(), end: dayjs().toISOString() };
  const baseFilters: Array<{ field: string; operator: "EQ" | "IN" | "LIKE"; value: string[] }> = [];

  if (scopeType === AlertScopeType.Interaction) {
    if (searchTerm) baseFilters.push({ field: "SpanName", operator: "LIKE", value: [`%${searchTerm}%`] });
    return {
      dataType: "TRACES" as const, timeRange,
      select: [{ function: "COL" as const, param: { field: "SpanName" }, alias: "interaction_name" }, { function: "CUSTOM" as const, param: { expression: "COUNT()" }, alias: "count" }],
      groupBy: ["interaction_name"], orderBy: [{ field: "count", direction: "DESC" as const }], limit: 20,
      filters: [{ field: "SpanType", operator: "EQ" as const, value: ["interaction"] }, ...baseFilters],
    };
  }
  if (scopeType === AlertScopeType.Screen) {
    if (searchTerm) baseFilters.push({ field: "SpanAttributes['screen.name']", operator: "LIKE", value: [`%${searchTerm}%`] });
    return {
      dataType: "TRACES" as const, timeRange,
      select: [{ function: "COL" as const, param: { field: "SpanAttributes['screen.name']" }, alias: "screen_name" }, { function: "CUSTOM" as const, param: { expression: "COUNT()" }, alias: "count" }],
      groupBy: ["screen_name"], orderBy: [{ field: "count", direction: "DESC" as const }], limit: 20,
      filters: [{ field: "SpanType", operator: "IN" as const, value: ["screen_session", "screen_load"] }, ...baseFilters],
    };
  }
  if (scopeType === AlertScopeType.NetworkAPI) {
    if (searchTerm) baseFilters.push({ field: "SpanAttributes['http.url']", operator: "LIKE", value: [`%${searchTerm}%`] });
    return {
      dataType: "TRACES" as const, timeRange,
      select: [{ function: "COL" as const, param: { field: "SpanAttributes['http.url']" }, alias: "url" }, { function: "CUSTOM" as const, param: { expression: "COUNT()" }, alias: "count" }],
      groupBy: ["url"], orderBy: [{ field: "count", direction: "DESC" as const }], limit: 20,
      filters: [{ field: "SpanType", operator: "EQ" as const, value: ["http_client"] }, ...baseFilters],
    };
  }
  return null;
};

const createDefaultCondition = (): MetricCondition => ({
  id: `cond_${Date.now()}`, alias: "A", metric: "", operator: MetricOperator.GREATER_THAN, threshold: {},
});

export const StepMetricsAndExpression: React.FC<StepMetricsAndExpressionProps> = ({ className }) => {
  const { formData, updateStepData } = useAlertFormContext();
  const { conditions: rawConditions, selectedScopeNames: globalScopeNames } = formData.metricsConditions;
  const { expression } = formData.conditionExpression;
  const scopeType = formData.scopeType.scopeType;
  const isAppVitals = isAppVitalsScope(scopeType);

  // Search state with debounce
  const [searchValue, setSearchValue] = useState("");
  const debouncedSearch = useDebouncedValue(searchValue, DEBOUNCE_DELAY);
  
  // Track if we've initialized the default condition
  const hasInitializedCondition = useRef(false);
  const defaultConditionRef = useRef<MetricCondition>(createDefaultCondition());

  // Use existing conditions or a stable default for rendering
  const conditions = useMemo(() => {
    return rawConditions && rawConditions.length > 0 ? rawConditions : [defaultConditionRef.current];
  }, [rawConditions]);

  // Initialize default condition only once if none exist
  useEffect(() => {
    if ((!rawConditions || rawConditions.length === 0) && !hasInitializedCondition.current) {
      hasInitializedCondition.current = true;
      updateStepData("metricsConditions", { selectedScopeNames: globalScopeNames || [], conditions: [defaultConditionRef.current] });
      updateStepData("conditionExpression", { expression: "A" });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Fetch metrics
  const { data: metricsResponse, isLoading: isMetricsLoading } = useGetAlertMetrics({ scope: scopeType || "" });
  const metrics = useMemo(() => metricsResponse?.data?.metrics || [], [metricsResponse]);

  // Fetch available scope names with search filter
  const scopeNamesQuery = useMemo(() => buildScopeNamesQuery(scopeType, debouncedSearch), [scopeType, debouncedSearch]);
  const shouldFetchScopeNames = !isAppVitals && !!scopeNamesQuery;
  const fallbackQuery = useMemo(() => ({
    dataType: "TRACES" as const,
    timeRange: { start: dayjs().subtract(7, "day").toISOString(), end: dayjs().toISOString() },
    select: [], groupBy: [],
  }), []);

  const { data: scopeNamesData, isLoading: isScopeNamesLoading, isFetching } = useGetDataQuery({
    requestBody: scopeNamesQuery || fallbackQuery,
    enabled: shouldFetchScopeNames,
  });

  const availableScopeNames = useMemo(() => {
    if (isAppVitals) return metrics;
    if (!scopeNamesData?.data?.rows) return [];
    const fields = scopeNamesData.data.fields;
    const idx = fields.findIndex((f: string) => f === "interaction_name" || f === "screen_name" || f === "url");
    if (idx === -1) return [];
    return scopeNamesData.data.rows.map((row: (string | number)[]) => String(row[idx])).filter((n: string) => n?.trim());
  }, [isAppVitals, metrics, scopeNamesData]);

  // Combine already selected values with search results
  const multiSelectData = useMemo(() => {
    const selectedSet = new Set(globalScopeNames || []);
    const searchResults = availableScopeNames.filter((s: string) => !selectedSet.has(s));
    const allOptions = [...(globalScopeNames || []), ...searchResults];
    return allOptions.map(s => ({ value: s, label: s }));
  }, [globalScopeNames, availableScopeNames]);

  // Track previous scope names to detect actual changes
  const prevScopeNamesRef = useRef<string[]>([]);

  // Sync condition thresholds when global scope names change
  useEffect(() => {
    if (isAppVitals || !globalScopeNames) return;
    
    // Only proceed if globalScopeNames actually changed
    const prevNames = prevScopeNamesRef.current;
    const namesChanged = prevNames.length !== globalScopeNames.length || 
      prevNames.some((n, i) => n !== globalScopeNames[i]);
    
    if (!namesChanged) return;
    prevScopeNamesRef.current = globalScopeNames;

    const updatedConditions = conditions.map(cond => {
      const newThreshold: Record<string, number> = {};
      globalScopeNames.forEach(scopeName => { newThreshold[scopeName] = cond.threshold[scopeName] ?? 0; });
      return { ...cond, threshold: newThreshold };
    });
    
    updateStepData("metricsConditions", { selectedScopeNames: globalScopeNames, conditions: updatedConditions });
  }, [globalScopeNames, conditions, isAppVitals, updateStepData]);

  const handleScopeNamesChange = useCallback((newScopeNames: string[]) => {
    updateStepData("metricsConditions", { selectedScopeNames: newScopeNames, conditions });
  }, [updateStepData, conditions]);

  const handleSearchChange = useCallback((value: string) => {
    setSearchValue(value);
  }, []);

  const addCondition = useCallback(() => {
    if (conditions.length >= UI_CONSTANTS.MAX_CONDITIONS) return;
    const alias = String.fromCharCode(65 + conditions.length);
    const threshold: Record<string, number> = {};
    (globalScopeNames || []).forEach(name => { threshold[name] = 0; });
    const newCondition: MetricCondition = { id: `cond_${Date.now()}`, alias, metric: "", operator: MetricOperator.GREATER_THAN, threshold };
    updateStepData("metricsConditions", { selectedScopeNames: globalScopeNames || [], conditions: [...conditions, newCondition] });
    updateStepData("conditionExpression", { expression: [...conditions.map(c => c.alias), alias].join(" && ") });
  }, [conditions, updateStepData, globalScopeNames]);

  const updateCondition = useCallback((index: number, updates: Partial<MetricCondition>) => {
    const updated = conditions.map((c, i) => i === index ? { ...c, ...updates } : c);
    updateStepData("metricsConditions", { selectedScopeNames: globalScopeNames || [], conditions: updated });
  }, [conditions, updateStepData, globalScopeNames]);

  const removeCondition = useCallback((index: number) => {
    if (conditions.length <= 1) return;
    const updated = conditions.filter((_, i) => i !== index);
    updateStepData("metricsConditions", { selectedScopeNames: globalScopeNames || [], conditions: updated });
    updateStepData("conditionExpression", { expression: updated.map(c => c.alias).join(" && ") });
  }, [conditions, updateStepData, globalScopeNames]);

  const isSearching = isFetching && searchValue.length > 0;

  return (
    <Box className={`${classes.container} ${className || ""}`}>
      {/* Global Scope Names Selector with Search */}
      {!isAppVitals && (
        <>
          <Text className={sharedClasses.stepTitle}>Scope Names to Monitor</Text>
          <Text className={sharedClasses.stepDescription}>
            Search and select scope names. These will apply to all conditions.
          </Text>
          <Divider className={sharedClasses.stepDivider} />
          {isScopeNamesLoading && !searchValue ? (
            <Loader size="sm" />
          ) : (
            <MultiSelect
              data={multiSelectData}
              value={globalScopeNames || []}
              onChange={handleScopeNamesChange}
              onSearchChange={handleSearchChange}
              searchValue={searchValue}
              placeholder="Type to search scope names..."
              searchable
              clearable
              maxDropdownHeight={200}
              mb="lg"
              mt="lg"
              nothingFoundMessage={isSearching ? "Searching..." : "No results found"}
              rightSection={isSearching ? <Loader size="xs" /> : undefined}
            />
          )}
          <Divider my="lg" />
        </>
      )}

      <Text className={sharedClasses.stepTitle}>Alert Conditions</Text>
      <Text className={sharedClasses.stepDescription}>Define metric conditions with thresholds for each scope name.</Text>
      <Divider className={sharedClasses.stepDivider} />

      <Box className={classes.conditionsContainer}>
        {conditions.map((condition, idx) => (
          <MetricConditionCard
            key={condition.id}
            condition={condition}
            metrics={metrics}
            globalScopeNames={isAppVitals ? [] : (globalScopeNames || [])}
            isAppVitals={isAppVitals}
            isMetricsLoading={isMetricsLoading}
            onUpdate={(updates) => updateCondition(idx, updates)}
            onRemove={() => removeCondition(idx)}
            canRemove={conditions.length > 1}
          />
        ))}

        {conditions.length < UI_CONSTANTS.MAX_CONDITIONS && (
          <Button variant="subtle" leftSection={<IconPlus size={16} />} onClick={addCondition} color="teal">
            Add Another Condition
          </Button>
        )}
      </Box>

      <Divider my="lg" />

      <Text className={sharedClasses.stepTitle}>Condition Expression</Text>
      <Text className={sharedClasses.stepDescription}>Combine conditions using && (AND) or || (OR)</Text>
      <Divider className={sharedClasses.stepDivider} mb="lg" />
      <TextInput value={expression} onChange={(e) => updateStepData("conditionExpression", { expression: e.target.value })} placeholder="A && B" />
    </Box>
  );
};
