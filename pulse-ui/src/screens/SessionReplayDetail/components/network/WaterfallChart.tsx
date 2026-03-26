import { BarChart } from "../../../../components/Charts";
import { createWaterfallOption } from "./utils/chartOptions";
import type { NetworkRequest } from "../../../../services/sessionReplay/mockSessionDetail";

interface WaterfallChartProps {
  networkRequests: NetworkRequest[];
}

export function WaterfallChart({ networkRequests }: WaterfallChartProps) {
  const option = createWaterfallOption(networkRequests);

  return (
    <BarChart
      option={option}
      height={Math.max(300, networkRequests.length * 40)}
      withLegend={false}
    />
  );
}
