import { useState, useEffect } from "react";
import { Loader, Text, Group, Select, Button } from "@mantine/core";
import { DateInput } from "@mantine/dates";
import { useNavigate, useLocation } from "react-router-dom";
import { IconCalendar, IconDownload } from "@tabler/icons-react";
import { InsightsDashboard } from "./components/InsightsDashboard";
import { InsightsNavigation } from "./components/InsightsNavigation";
import {
  sessionReplayService,
  SessionReplayMetrics,
} from "../../services/sessionReplay";
import { useSessionReplayFilters } from "../../contexts/SessionReplayFilterContext";
import { useDateRangeConfig } from "../SessionReplay/hooks/useDateRangeConfig";
import classes from "./SessionReplayInsights.module.css";

/**
 * Session Replay Insights Page
 *
 * High-level metrics, KPIs, trends, and patterns.
 * Every metric is clickable and navigates to Session List with appropriate filters.
 *
 * Route: /session-replay/insights
 */
export function SessionReplayInsights() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { state, actions } = useSessionReplayFilters();

  const projectMatch = pathname.match(/^\/projects\/([^/]+)/);
  const sessionReplayBase = projectMatch
    ? `/projects/${projectMatch[1]}/session-replay`
    : "/session-replay";
  const { config: dateRangeConfig, loading: dateRangeLoading } =
    useDateRangeConfig();
  const [loading, setLoading] = useState(true);
  const [metrics, setMetrics] = useState<SessionReplayMetrics | null>(null);

  // Initialize date range from API config
  useEffect(() => {
    if (dateRangeConfig && !state.dateRange.preset) {
      actions.setDateRange(dateRangeConfig.defaultValue);
    }
  }, [dateRangeConfig, state.dateRange.preset, actions]);

  // Fetch insights metrics
  useEffect(() => {
    const fetchMetrics = async () => {
      setLoading(true);
      try {
        let dateRange;

        if (state.dateRange.preset === "custom") {
          if (state.dateRange.from && state.dateRange.to) {
            dateRange = {
              start: new Date(state.dateRange.from).toISOString(),
              end: new Date(state.dateRange.to).toISOString(),
            };
          } else {
            // Default to 7 days if custom but not set
            dateRange = {
              start: new Date(
                Date.now() - 7 * 24 * 60 * 60 * 1000,
              ).toISOString(),
              end: new Date().toISOString(),
            };
          }
        } else if (state.dateRange.preset) {
          // Parse preset like "7d" -> 7 days
          const days = parseInt(state.dateRange.preset.replace("d", ""));
          dateRange = {
            start: new Date(
              Date.now() - days * 24 * 60 * 60 * 1000,
            ).toISOString(),
            end: new Date().toISOString(),
          };
        } else {
          // Default to 7 days
          dateRange = {
            start: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
            end: new Date().toISOString(),
          };
        }

        const response = await sessionReplayService.getSessions({
          filters: {},
          dateRange,
          page: 1,
          pageSize: 10,
        });

        setMetrics(response.metrics);
      } catch (error) {
        console.error("Failed to fetch insights:", error);
      } finally {
        setLoading(false);
      }
    };

    fetchMetrics();
  }, [state.dateRange]);

  const handleViewSession = (sessionId: string) => {
    navigate(`${sessionReplayBase}/${sessionId}`);
  };

  const handleDrillDown = (type: any, value: any, label: string) => {
    actions.setDrillDown(type, value, label);
    navigate(`${sessionReplayBase}/sessions`);
  };

  if (loading && !metrics) {
    return (
      <div className={classes.container}>
        <div className={classes.loadingContainer}>
          <Loader color="teal" size="lg" />
          <Text size="sm" c="dimmed">
            Loading insights...
          </Text>
        </div>
      </div>
    );
  }

  if (!metrics) {
    return (
      <div className={classes.container}>
        <Text c="dimmed">No insights data available</Text>
      </div>
    );
  }

  return (
    <div className={classes.pageContainer}>
      {/* Header with Date Range Controls */}
      <Group justify="space-between" mb="lg">
        <div>
          <Text size="xl" fw={700}>
            Session Replay Insights
          </Text>
          <Text size="sm" c="dimmed">
            High-level metrics and patterns. Click any metric to drill down to
            sessions.
          </Text>
        </div>
        <Button
          leftSection={<IconDownload size={16} />}
          variant="light"
          color="teal"
        >
          Export Report
        </Button>
      </Group>

      {/* Date Range Controls */}
      <Group gap="sm" mb="xl">
        <Select
          leftSection={<IconCalendar size={16} />}
          placeholder="Date range"
          value={state.dateRange.preset}
          onChange={(value) => {
            actions.setDateRange(value ?? "7d");
            if (value !== "custom") {
              actions.setDateRange(value ?? "7d", null, null);
            }
          }}
          data={dateRangeConfig?.options || []}
          disabled={dateRangeLoading}
          style={{ minWidth: 150, maxWidth: 200 }}
        />

        {/* Custom Date Range Pickers */}
        {state.dateRange.preset === "custom" && (
          <>
            <DateInput
              leftSection={<IconCalendar size={16} />}
              placeholder="From date"
              value={
                state.dateRange.from
                  ? new Date(state.dateRange.from)
                  : undefined
              }
              onChange={(date) => {
                actions.setDateRange(
                  "custom",
                  date?.toISOString() ?? null,
                  state.dateRange.to,
                );
              }}
              maxDate={
                state.dateRange.to ? new Date(state.dateRange.to) : new Date()
              }
              style={{ minWidth: 200 }}
              clearable
            />
            <DateInput
              leftSection={<IconCalendar size={16} />}
              placeholder="To date"
              value={
                state.dateRange.to ? new Date(state.dateRange.to) : undefined
              }
              onChange={(date) => {
                actions.setDateRange(
                  "custom",
                  state.dateRange.from,
                  date?.toISOString() ?? null,
                );
              }}
              minDate={
                state.dateRange.from
                  ? new Date(state.dateRange.from)
                  : undefined
              }
              maxDate={new Date()}
              style={{ minWidth: 200 }}
              clearable
            />
          </>
        )}
      </Group>

      {/* Sticky Section Navigation */}
      <InsightsNavigation />

      {/* Main Dashboard Content (Full Width) */}
      <InsightsDashboard
        metrics={metrics}
        onViewSession={handleViewSession}
        onDrillDown={handleDrillDown}
      />
    </div>
  );
}
