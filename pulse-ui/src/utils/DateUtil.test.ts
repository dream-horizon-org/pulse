import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import {
  getHumanReadableLocalStringFromUTCDateTimeValue,
  getLocalStringFromUTCDateTimeValue,
} from "./DateUtil";

jest.mock("../constants", () => ({
  CRITICAL_INTERACTION_QUICK_TIME_FILTERS: {},
  SNOOZE_ALERT_QUICK_TIME_FILTERS: {},
}));

dayjs.extend(utc);

describe("DateUtil UTC/local conversion helpers", () => {
  it("normalizes '+' encoded spaces in UTC datetime values", () => {
    const encoded = getLocalStringFromUTCDateTimeValue("2026-05-27+13:00:00");
    const plain = getLocalStringFromUTCDateTimeValue("2026-05-27 13:00:00");

    expect(encoded).toBe(plain);
    expect(encoded).not.toBe("");
  });

  it("returns empty string for invalid UTC datetime values", () => {
    expect(getLocalStringFromUTCDateTimeValue("not-a-date")).toBe("");
    expect(getHumanReadableLocalStringFromUTCDateTimeValue("not-a-date")).toBe(
      "",
    );
  });

  it("produces human-readable output from UTC datetime value", () => {
    const rawUtc = "2026-05-27 13:00:00";
    const local = getLocalStringFromUTCDateTimeValue(rawUtc);
    const expected = dayjs(local, "YYYY-MM-DD HH:mm:ss", true).format(
      "MMM DD, YYYY HH:mm:ss",
    );

    expect(getHumanReadableLocalStringFromUTCDateTimeValue(rawUtc)).toBe(
      expected,
    );
  });
});
