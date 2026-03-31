import { BarChart } from "../../../../components/Charts";
import { createDurationOption } from "./utils/chartOptions";
import type { NetworkRequest } from "../../../../services/sessionReplay/mockSessionDetail";

interface DurationChartProps {
  networkRequests: NetworkRequest[];
}

export function DurationChart({ networkRequests }: DurationChartProps) {
  const option = createDurationOption(networkRequests);

  return (
    <BarChart
      option={option}
      height={Math.max(300, networkRequests.length * 40)}
      withLegend={false}
    />
  );
}
