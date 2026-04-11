import dayjs from "dayjs";

const ROOT_CAUSE_DATE_FORMAT = "YYYY-MM-DD" as const;
const INVALID_DATE_LABEL = "Invalid Date" as const;
const NUMERIC_STRING_PATTERN = /^\d+$/;

export const getRootCauseDateFromEndTime = (
  endTime: string | null | undefined,
): string | undefined => {
  const hasEndTime = endTime != null && endTime !== "";

  if (!hasEndTime) {
    return undefined;
  }

  const isNumericString = NUMERIC_STRING_PATTERN.test(endTime);
  const dayjsInput = isNumericString ? Number(endTime) : endTime;
  const rootCauseDateRaw = dayjs(dayjsInput).format(ROOT_CAUSE_DATE_FORMAT);
  const isInvalidFormattedDate = rootCauseDateRaw === INVALID_DATE_LABEL;

  if (isInvalidFormattedDate) {
    return undefined;
  }

  return rootCauseDateRaw;
};
