import {
  formatAppVersionRange,
  formatExceptionTimestamp,
} from "./exceptionTableUtils";
import { getHumanReadableLocalStringFromUTCDateTimeValue } from "../../../../utils/DateUtil";

jest.mock("../../../../utils/DateUtil", () => ({
  getHumanReadableLocalStringFromUTCDateTimeValue: jest.fn(),
}));

const mockedHumanReadable =
  getHumanReadableLocalStringFromUTCDateTimeValue as jest.MockedFunction<
    typeof getHumanReadableLocalStringFromUTCDateTimeValue
  >;

describe("exceptionTableUtils", () => {
  describe("formatAppVersionRange", () => {
    it("returns range in semantic order", () => {
      expect(formatAppVersionRange("2.3.5, 2.3.0, 2.4.0")).toBe(
        "2.3.0 - 2.4.0",
      );
    });

    it("returns '-' for empty values", () => {
      expect(formatAppVersionRange("")).toBe("-");
      expect(formatAppVersionRange("   ")).toBe("-");
    });
  });

  describe("formatExceptionTimestamp", () => {
    beforeEach(() => {
      mockedHumanReadable.mockReset();
    });

    it("delegates UTC->local human-readable formatting to DateUtil", () => {
      mockedHumanReadable.mockReturnValue("May 27, 2026 18:30:00");

      expect(formatExceptionTimestamp("2026-05-27 13:00:00")).toBe(
        "May 27, 2026 18:30:00",
      );
      expect(mockedHumanReadable).toHaveBeenCalledWith("2026-05-27 13:00:00");
    });

    it("returns '-' when input is missing or formatter yields empty value", () => {
      mockedHumanReadable.mockReturnValue("");
      expect(formatExceptionTimestamp("2026-05-27 13:00:00")).toBe("-");
      expect(formatExceptionTimestamp("-")).toBe("-");
      expect(formatExceptionTimestamp(undefined)).toBe("-");
    });
  });
});
