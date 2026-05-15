export const formatTooltipValue = (
  value: any,
  decimalPlaces: number = 2,
): string => {
  if (Array.isArray(value)) {
    const y = value.length >= 2 ? value[1] : value[0];
    return Number(y).toFixed(decimalPlaces);
  }
  return Number(value).toFixed(decimalPlaces);
};
