import dayjs from "dayjs";

export const MOCK_REVENUE_METRICS = {
  revenueGenerated: 124800,
  revenueAtRisk: 8400,
  percentageAtRisk: 6.3,
  currency: "USD",
};

// Generates hourly mock trend data for any arbitrary [startTime, endTime] range.
// Revenue at risk spikes are placed at ~30% and ~70% through the range to simulate APDEX dips.
export const generateRevenueTrend = (startTime: string, endTime: string) => {
  const start = dayjs(startTime);
  const end = dayjs(endTime);
  const totalHours = Math.max(1, end.diff(start, "hour"));
  const data: { timestamp: number; generated: number; atRisk: number }[] = [];

  const spike1Start = Math.floor(totalHours * 0.28);
  const spike1End = Math.floor(totalHours * 0.33);
  const spike2Start = Math.floor(totalHours * 0.68);
  const spike2End = Math.floor(totalHours * 0.73);

  for (let i = 0; i <= totalHours; i++) {
    const ts = start.add(i, "hour").valueOf();
    const hour = start.add(i, "hour").hour();

    const trafficMultiplier =
      hour >= 9 && hour <= 21 ? 1 + Math.sin(((hour - 9) / 12) * Math.PI) * 0.6 : 0.3;

    const isDegraded = (i >= spike1Start && i <= spike1End) || (i >= spike2Start && i <= spike2End);

    const baseGenerated = 680 * trafficMultiplier + Math.random() * 120;
    const baseAtRisk = isDegraded
      ? baseGenerated * (0.18 + Math.random() * 0.08)
      : baseGenerated * (0.04 + Math.random() * 0.02);

    data.push({
      timestamp: ts,
      generated: Math.round(baseGenerated),
      atRisk: Math.round(baseAtRisk),
    });
  }

  return data;
};

// Mock revenue config per interaction (keyed by name)
// First 2 interactions in TopInteractionsHealth will show revenue data
export const MOCK_INTERACTION_REVENUE: Record<
  string,
  { revenueValue: number; currency: string }
> = {
  __first__: { revenueValue: 49.99, currency: "USD" },
  __second__: { revenueValue: 29.99, currency: "USD" },
};
