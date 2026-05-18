import { describe, expect, it } from "vitest";
import {
  CLSThresholds,
  FCPThresholds,
  INPThresholds,
  LCPThresholds,
  TTFBThresholds,
} from "../index";

describe("web-vitals threshold re-exports (public API)", () => {
  it("re-exports upstream threshold tuples from web-vitals", () => {
    expect(Array.isArray(LCPThresholds)).toBe(true);
    expect(LCPThresholds).toHaveLength(2);
    expect(INPThresholds).toHaveLength(2);
    expect(CLSThresholds).toHaveLength(2);
    expect(FCPThresholds).toHaveLength(2);
    expect(TTFBThresholds).toHaveLength(2);
    expect(LCPThresholds.every((n) => typeof n === "number")).toBe(true);
  });
});
