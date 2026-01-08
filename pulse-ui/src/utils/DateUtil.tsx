import { DateValue } from "@mantine/dates";
import dayjs, { ManipulateType } from "dayjs";
import {
  CRITICAL_INTERACTION_QUICK_TIME_FILTERS,
  SNOOZE_ALERT_QUICK_TIME_FILTERS,
} from "../constants";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);
export function getStartAndEndDateTimeString(
  quickDateTimeOption: string,
  subractMinutes: number,
) {
  subractMinutes = subractMinutes !== undefined ? subractMinutes : 2;

  switch (quickDateTimeOption) {
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_5_MINUTES:
      return {
        startDate: dayjs()
          .utc()
          .subtract(7, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_15_MINUTES:
      return {
        startDate: dayjs()
          .utc()
          .subtract(17, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_30_MINUTES:
      return {
        startDate: dayjs()
          .utc()
          .subtract(32, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_1_HOUR:
      return {
        startDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .subtract(1, "hour")
          .format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_3_HOURS:
      return {
        startDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .subtract(3, "hours")
          .format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_6_HOURS:
      return {
        startDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .subtract(6, "hours")
          .format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_12_HOURS:
      return {
        startDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .subtract(12, "hours")
          .format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_24_HOURS:
      return {
        startDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .subtract(24, "hours")
          .format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_7_DAYS:
      return {
        startDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .subtract(7, "days")
          .format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_30_DAYS:
      return {
        startDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .subtract(30, "days")
          .format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_90_DAYS:
      return {
        startDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .subtract(90, "days")
          .format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.YESTERDAY:
      return {
        startDate: dayjs()
          .utc()
          .subtract(1, "days")
          .startOf("day")
          .format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(1, "days")
          .endOf("day")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.PREVIOUS_WEEK:
      return {
        startDate: dayjs()
          .utc()
          .subtract(1, "week")
          .startOf("week")
          .format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(1, "week")
          .endOf("week")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.PREVIOUS_MONTH:
      return {
        startDate: dayjs()
          .utc()
          .subtract(1, "month")
          .startOf("month")
          .format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()

          .subtract(1, "month")
          .endOf("month")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.TODAY_SO_FAR:
      return {
        startDate: dayjs().utc().startOf("day").format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.THIS_WEEK:
      return {
        startDate: dayjs().utc().startOf("week").format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.THIS_MONTH_SO_FAR:
      return {
        startDate: dayjs().utc().startOf("month").format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case CRITICAL_INTERACTION_QUICK_TIME_FILTERS.LAST_2_DAYS:
      return {
        startDate: dayjs()
          .utc()
          .subtract(2, "days")
          .startOf("day")
          .format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(1, "days")
          .endOf("day")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
    case SNOOZE_ALERT_QUICK_TIME_FILTERS.NEXT_1_HOUR:
      return getFutureDateForSnooze(1, "hours");
    case SNOOZE_ALERT_QUICK_TIME_FILTERS.NEXT_3_HOURS:
      return getFutureDateForSnooze(3, "hours");
    case SNOOZE_ALERT_QUICK_TIME_FILTERS.NEXT_6_HOURS:
      return getFutureDateForSnooze(6, "hours");
    case SNOOZE_ALERT_QUICK_TIME_FILTERS.NEXT_12_HOURS:
      return getFutureDateForSnooze(12, "hours");
    case SNOOZE_ALERT_QUICK_TIME_FILTERS.NEXT_24_HOURS:
      return getFutureDateForSnooze(24, "hours");
    case SNOOZE_ALERT_QUICK_TIME_FILTERS.NEXT_2_DAYS:
      return getFutureDateForSnooze(2, "days");
    case SNOOZE_ALERT_QUICK_TIME_FILTERS.NEXT_7_DAYS:
      return getFutureDateForSnooze(7, "days");
    case SNOOZE_ALERT_QUICK_TIME_FILTERS.NEXT_30_DAYS:
      return getFutureDateForSnooze(30, "days");
    case SNOOZE_ALERT_QUICK_TIME_FILTERS.NEXT_90_DAYS:
      return getFutureDateForSnooze(90, "days");
    default:
      return {
        startDate: dayjs()
          .utc()
          .subtract(17, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
        endDate: dayjs()
          .utc()
          .subtract(subractMinutes, "minutes")
          .format("YYYY-MM-DD HH:mm:ss"),
      };
  }
}

export function getFutureDateForSnooze(duration: number, unit: ManipulateType) {
  return {
    endDate: dayjs()
      .utc()
      .add(duration, unit)
      .set("second", 0)
      .format("YYYY-MM-DD HH:mm:ss"),
    startDate: dayjs().utc().set("second", 0).format("YYYY-MM-DD HH:mm:ss"),
  };
}

export function getDateTimeStringFromDateValue(value: DateValue) {
  return value ? dayjs(value).format("YYYY-MM-DD HH:mm:ss") : "";
}

export function getUTCDateTimeStringFromDateValue(value: DateValue) {
  return value ? dayjs(value).utc().format("YYYY-MM-DD HH:mm:ss") : "";
}

export function getDateFromUTCTimeString(value: string): DateValue {
  return dayjs.utc(value).toDate();
}

export function getUTCDateTimeFromLocalStringDateValue(
  value: string | undefined,
) {
  return value ? dayjs(value).utc().format("YYYY-MM-DD HH:mm:ss") : "";
}

export function getLocalStringFromUTCDateTimeValue(value: string | undefined) {
  if (!value || value.trim() === "") return "";
  
  // Handle URL encoding: replace + with space (URL encoding for space in query strings)
  let cleanedValue = value.replace(/\+/g, " ").trim();
  
  // Try to decode if still URL-encoded
  try {
    cleanedValue = decodeURIComponent(cleanedValue);
  } catch {
    // Already decoded or invalid, use as is
  }
  
  // Try parsing as UTC with explicit format first, then flexible parsing
  let parsed = dayjs.utc(cleanedValue, "YYYY-MM-DD HH:mm:ss", true); // strict mode
  
  // If strict parsing fails, try flexible parsing
  if (!parsed.isValid()) {
    parsed = dayjs.utc(cleanedValue);
  }
  
  // Validate the parsed date
  if (!parsed.isValid()) {
    console.warn("Invalid date string:", value, "->", cleanedValue);
    return "";
  }
  
  return parsed.local().format("YYYY-MM-DD HH:mm:ss");
}

export function getLocalStringFromUTCEpoch(value: number) {
  return dayjs.unix(value).format("YYYY-MM-DD HH:mm:ss");
}

export function getHumanReadableLocalStringFromUTCEpoch(value: number) {
  return dayjs.unix(value).format("MMM D, YYYY · hh:mm A");
}

export function getCurrentEpochSeconds(): number {
  return Math.floor(dayjs.utc().valueOf() / 1000);
}

/**
 * Converts a time string to ISO format (UTC).
 * Handles both "YYYY-MM-DD HH:mm:ss" format (from getStartAndEndDateTimeString)
 * and ISO format (contains 'T' or 'Z').
 * 
 * @param time - The time string to convert
 * @returns ISO formatted UTC string, or empty string if input is empty
 */
export function formatTimeToISO(time: string): string {
  if (!time) return "";
  
  // If already in ISO format (contains 'T' or 'Z'), parse and ensure valid
  if (time.includes("T") || time.includes("Z")) {
    return dayjs.utc(time).toISOString();
  }
  
  // Parse "YYYY-MM-DD HH:mm:ss" as UTC and convert to ISO format
  return dayjs.utc(time, "YYYY-MM-DD HH:mm:ss").toISOString();
}
