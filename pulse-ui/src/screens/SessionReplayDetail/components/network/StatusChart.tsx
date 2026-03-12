import { PieChart } from "../../../../components/Charts";
import { createStatusDistributionOption } from "./utils/chartOptions";
import type { NetworkRequest } from "../../../../services/sessionReplay/mockSessionDetail";

interface StatusChartProps {
  networkRequests: NetworkRequest[];
}

export function StatusChart({ networkRequests }: StatusChartProps) {
  const option = createStatusDistributionOption(networkRequests);

  return <PieChart option={option} height={300} withLegend={true} />;
}
