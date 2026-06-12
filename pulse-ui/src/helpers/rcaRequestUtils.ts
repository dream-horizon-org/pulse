export const isValidRcaDateParam = (
  date: string | null | undefined,
): date is string =>
  !!date && date !== "Invalid Date" && /^\d{4}-\d{2}-\d{2}$/.test(date);
