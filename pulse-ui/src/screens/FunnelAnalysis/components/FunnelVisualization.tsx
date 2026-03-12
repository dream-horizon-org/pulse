import { Box, Text, Tooltip } from "@mantine/core";
import { IconTrendingUp, IconTrendingDown, IconClock } from "@tabler/icons-react";
import { MockFunnelData, formatDuration } from "../mockData";
import classes from "../FunnelAnalysis.module.css";

interface FunnelVisualizationProps {
  data: MockFunnelData;
}

const SLOW_THRESHOLD_SECONDS = 30;

export function FunnelVisualization({ data }: FunnelVisualizationProps) {
  const maxCompleted = data.steps.length > 0 ? data.steps[0].completed : 1;
  const isPositiveTrend = data.conversionTrend >= 0;

  return (
    <>
      {/* Big Number KPI */}
      <Box className={classes.kpiSection}>
        <Text className={classes.kpiBigNumber}>
          {data.totalConversionRate}%
        </Text>
        <Box>
          <Text className={classes.kpiLabel}>Total Conversion</Text>
          <Box
            className={`${classes.kpiTrend} ${isPositiveTrend ? classes.kpiTrendUp : classes.kpiTrendDown}`}
          >
            {isPositiveTrend ? (
              <IconTrendingUp size={14} />
            ) : (
              <IconTrendingDown size={14} />
            )}
            {isPositiveTrend ? "+" : ""}
            {data.conversionTrend}% from last week
          </Box>
        </Box>
      </Box>

      {/* Horizontal Bar Chart */}
      <Box className={classes.chartSection}>
        <Text size="sm" fw={600} c="dark.7" mb="lg">
          Funnel Steps
        </Text>

        {data.steps.map((step, index) => {
          const completedPct = (step.completed / maxCompleted) * 100;
          const dropoffPct =
            index === 0
              ? 0
              : (step.dropoffCount / maxCompleted) * 100;

          return (
            <Box key={step.id}>
              {/* Time-to-Convert Badge between steps */}
              {step.medianTimeToStep !== null && (
                <Box
                  className={`${classes.timeBadge} ${
                    step.medianTimeToStep > SLOW_THRESHOLD_SECONDS
                      ? classes.timeBadgeSlow
                      : ""
                  }`}
                >
                  <IconClock size={12} />
                  {formatDuration(step.medianTimeToStep)} median
                </Box>
              )}

              {/* Bar Row */}
              <Box className={classes.chartBar}>
                <Text className={classes.chartBarLabel} title={step.eventName}>
                  {step.eventName}
                </Text>
                <Box className={classes.chartBarTrack}>
                  <Box
                    className={classes.chartBarFill}
                    style={{ width: `${completedPct}%` }}
                  >
                    <Text className={classes.chartBarCount}>
                      {step.completed.toLocaleString()}
                    </Text>
                  </Box>
                  {dropoffPct > 0 && (
                    <Tooltip
                      label={`${step.dropoffCount.toLocaleString()} users dropped off at Step ${index + 1}`}
                      position="top"
                      withArrow
                    >
                      <Box
                        className={classes.chartBarDropoff}
                        style={{ width: `${dropoffPct}%` }}
                      >
                        <Text className={classes.chartBarDropoffCount}>
                          -{step.dropoffCount.toLocaleString()}
                        </Text>
                      </Box>
                    </Tooltip>
                  )}
                </Box>
              </Box>
            </Box>
          );
        })}
      </Box>
    </>
  );
}
