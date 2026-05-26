import { Box, Text, Tooltip } from "@mantine/core";
import {
  IconClock,
  IconTrendingDown,
  IconTrendingUp,
} from "@tabler/icons-react";
import { FunnelStepResult } from "../../../hooks/useGetFunnelData";
import { FunnelMode } from "../../../services/funnels.service";
import { formatDuration } from "../FunnelJourneyCreate.util";
import classes from "../FunnelCreate.module.css";

interface FunnelVisualizationProps {
  steps: FunnelStepResult[];
  totalConversionRate: number;
  conversionTrend: number;
  medianTimes: (number | null)[];
  /**
   * Analysis grouping key for this funnel. Drives the drop-off tooltip wording
   * ("users" vs "sessions"). Defaults to UNIQUE_USERS when omitted.
   */
  mode?: FunnelMode;
  /** Zero-based step the user dropped from; fired when the drop-off segment is clicked. */
  onStepDropoffClick?: (focusStepIndex: number) => void;
}

const SLOW_THRESHOLD_SECONDS = 30;

export function FunnelVisualization({
  steps,
  totalConversionRate,
  conversionTrend,
  medianTimes,
  mode = FunnelMode.UNIQUE_USERS,
  onStepDropoffClick,
}: FunnelVisualizationProps) {
  const subjectPlural = mode === FunnelMode.SESSIONS ? "sessions" : "users";
  const maxCompleted = steps.length > 0 ? steps[0].count : 1;
  const isPositiveTrend = conversionTrend >= 0;

  // Round to one decimal place to match the listing's conversion column. The
  // raw percentage from ClickHouse can carry many decimals (e.g. 83.61529016630755);
  // both the headline KPI and the trend chip below should be displayed compactly.
  const formatPct = (n: number): string => (Math.round(n * 10) / 10).toString();

  return (
    <>
      <Box className={classes.kpiSection}>
        <Text className={classes.kpiBigNumber}>
          {formatPct(totalConversionRate)}%
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
            {formatPct(conversionTrend)}% from last week
          </Box>
        </Box>
      </Box>

      <Box className={classes.chartSection}>
        <Text size="sm" fw={600} c="dark.7" mb="lg">
          Funnel Steps
        </Text>

        {steps.map((step, index) => {
          const completedPct = (step.count / maxCompleted) * 100;
          const dropoffCount =
            index === 0 ? 0 : steps[index - 1].count - step.count;
          const dropoffPct =
            index === 0 ? 0 : (dropoffCount / maxCompleted) * 100;
          const medianTime = medianTimes[index] ?? null;

          return (
            <Box key={step.stepName}>
              {medianTime !== null && (
                <Box
                  className={`${classes.timeBadge} ${
                    medianTime > SLOW_THRESHOLD_SECONDS
                      ? classes.timeBadgeSlow
                      : ""
                  }`}
                >
                  <IconClock size={12} />
                  {formatDuration(medianTime)} median
                </Box>
              )}

              <Box className={classes.chartBar}>
                <Text className={classes.chartBarLabel} title={step.stepName}>
                  {step.stepName}
                </Text>
                <Box className={classes.chartBarTrack}>
                  <Box
                    className={classes.chartBarFill}
                    style={{ width: `${completedPct}%` }}
                  >
                    <Text className={classes.chartBarCount}>
                      {step.count.toLocaleString()}
                    </Text>
                  </Box>
                  {dropoffPct > 0 && (
                    <Tooltip
                      label={`${dropoffCount.toLocaleString()} ${subjectPlural} dropped off at Step ${index + 1}`}
                      position="top"
                      withArrow
                    >
                      <Box
                        className={classes.chartBarDropoff}
                        style={{
                          width: `${dropoffPct}%`,
                          cursor: onStepDropoffClick ? "pointer" : undefined,
                        }}
                        role={onStepDropoffClick ? "button" : undefined}
                        tabIndex={onStepDropoffClick ? 0 : undefined}
                        onClick={
                          onStepDropoffClick
                            ? () => onStepDropoffClick(index - 1)
                            : undefined
                        }
                        onKeyDown={
                          onStepDropoffClick
                            ? (e) => {
                                if (e.key === "Enter" || e.key === " ") {
                                  e.preventDefault();
                                  onStepDropoffClick(index - 1);
                                }
                              }
                            : undefined
                        }
                      >
                        <Text className={classes.chartBarDropoffCount}>
                          -{dropoffCount.toLocaleString()}
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
