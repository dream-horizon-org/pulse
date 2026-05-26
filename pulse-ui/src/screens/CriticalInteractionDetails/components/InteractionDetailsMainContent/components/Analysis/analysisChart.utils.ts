export const allValuesZero = (values: number[]) =>
  values.length > 0 && values.every((v) => v === 0);

export const itemsLabel = (count: number, singular: string, plural: string) =>
  count === 1 ? `1 ${singular}` : `${count} ${plural}`;

export const topItemsLabel = (
  count: number,
  singular: string,
  plural: string,
) => (count === 1 ? `top ${count} ${singular}` : `top ${count} ${plural}`);

export type ValueAxisScale = { max: number; interval?: number };

/** Default x-axis scale for count/error bar charts in TopIssuesCharts. */
export const getCountValueAxisScale = (maxValue: number): ValueAxisScale => {
  const max = Math.ceil(maxValue / 500) * 500 || 500;
  return { max };
};

/** X-axis scale for P95 latency (ms) horizontal bar charts. */
export const getLatencyValueAxisScale = (maxValue: number): ValueAxisScale => {
  const paddedMax = maxValue * 1.1;

  if (paddedMax <= 500) {
    const max = Math.ceil(paddedMax / 10) * 10 || 10;
    const interval = Math.ceil(max / 5 / 10) * 10 || 5;
    return { max, interval };
  }

  const max = Math.ceil(paddedMax / 500) * 500;
  return { max, interval: max / 5 };
};

export const getEmptyMessageWhenAllZero = <T>(
  data: readonly T[],
  valueKey: keyof T,
  message: string,
): string | null => {
  if (!data.length) {
    return null;
  }
  const values = data.map((d) => Number(d[valueKey]) || 0);
  return allValuesZero(values) ? message : null;
};
