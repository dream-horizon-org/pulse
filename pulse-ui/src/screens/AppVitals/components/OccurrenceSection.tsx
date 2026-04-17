import { Box, Text, Paper, SegmentedControl, Group, Button } from "@mantine/core";
import { IconRefresh } from "@tabler/icons-react";
import type { TimeBucketSize } from "../../../utils/TimeBucketUtil";
import type { StartEndDateTimeType } from "../../CriticalInteractionDetails/components/DateTimeRangePickerDropDown/DateTimeRangePicker.interface";
import { OccurrenceTrendChart } from "./OccurrenceTrendChart";
import { ScreenBreakdownList } from "./ScreenBreakdownList";
import classes from "./OccurrenceSection.module.css";

interface OccurrenceSectionProps {
  trendView: string;
  onTrendViewChange: (value: string) => void;
  trendData: any[];
  screenBreakdown: any[];
  chartColors: any;
  bucketSize: TimeBucketSize;
  /** ISO bounds for the trend query (used for x-axis styling). */
  rangeStart?: string;
  rangeEnd?: string;
  getXAxisInterval: () => number;
  onTimeFilterChange?: (value: StartEndDateTimeType) => void;
  /** When true, show reset next to the chart (custom time range / brush). */
  showResetTimeRange?: boolean;
  onResetTimeRange?: () => void;
}

const VIEW_OPTIONS = [
  { label: "Aggregated", value: "aggregated" },
  { label: "App Version", value: "appVersion" },
  { label: "OS Version", value: "os" },
  { label: "By Screen", value: "screen" },
];

export const OccurrenceSection: React.FC<OccurrenceSectionProps> = ({
  trendView,
  onTrendViewChange,
  trendData,
  screenBreakdown,
  chartColors,
  bucketSize,
  rangeStart,
  rangeEnd,
  getXAxisInterval,
  onTimeFilterChange,
  showResetTimeRange,
  onResetTimeRange,
}) => {
  return (
    <Paper className={classes.sectionContainer}>
      <Group justify="space-between" align="center" mb="md" wrap="nowrap">
        <Text className={classes.sectionTitle}>Occurrence</Text>
        {showResetTimeRange && onResetTimeRange && (
          <Button
            variant="light"
            color="teal"
            size="xs"
            leftSection={<IconRefresh size={14} />}
            onClick={onResetTimeRange}
          >
            Reset time range
          </Button>
        )}
      </Group>

      {/* View Toggle - Compact, not full width */}
      <SegmentedControl
        value={trendView}
        onChange={onTrendViewChange}
        data={VIEW_OPTIONS}
        size="sm"
        className={classes.segmentedControl}
      />

      <Box className={classes.contentContainer}>
        {trendView === "screen" ? (
          <ScreenBreakdownList screenBreakdown={screenBreakdown} />
        ) : (
          <OccurrenceTrendChart
            trendData={trendData}
            trendView={trendView}
            chartColors={chartColors}
            bucketSize={bucketSize}
            rangeStart={rangeStart}
            rangeEnd={rangeEnd}
            getXAxisInterval={getXAxisInterval}
            onTimeFilterChange={onTimeFilterChange}
          />
        )}
      </Box>
    </Paper>
  );
};
