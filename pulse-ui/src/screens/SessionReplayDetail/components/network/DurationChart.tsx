import { Box } from "@mantine/core";
import { BarChart } from "../../../../components/Charts";
import { createDurationOption } from "./utils/chartOptions";
import type { NetworkRequest } from "../../../../services/sessionReplay/mockSessionDetail";
import classes from "./DurationChart.module.css";

/** Fixed canvas height so the duration bar chart stays inside the Network tab layout */
const DURATION_CHART_HEIGHT_PX = 360;

interface DurationChartProps {
  networkRequests: NetworkRequest[];
}

export function DurationChart({ networkRequests }: DurationChartProps) {
  const option = createDurationOption(networkRequests);

  return (
    <Box className={classes.durationChartWrap}>
      <BarChart
        option={option}
        height={DURATION_CHART_HEIGHT_PX}
        withLegend={false}
      />
    </Box>
  );
}
