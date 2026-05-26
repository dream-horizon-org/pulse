export const allValuesZero = (values: number[]) =>
  values.length > 0 && values.every((v) => v === 0);

export const itemsLabel = (count: number, singular: string, plural: string) =>
  count === 1 ? `1 ${singular}` : `${count} ${plural}`;

export const topItemsLabel = (
  count: number,
  singular: string,
  plural: string,
) => (count === 1 ? `top ${count} ${singular}` : `top ${count} ${plural}`);

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
