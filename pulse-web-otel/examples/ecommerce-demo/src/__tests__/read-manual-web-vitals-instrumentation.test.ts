import { describe, expect, it } from "vitest";
import { readManualWebVitalsInstrumentation } from "../read-manual-web-vitals-instrumentation";

describe("readManualWebVitalsInstrumentation", () => {
  it("returns webVitals enabled false when pulse_wv_enabled is 0", () => {
    const p = readManualWebVitalsInstrumentation(
      new URLSearchParams("pulse_wv_enabled=0"),
    );
    expect(p).toEqual({ webVitals: { enabled: false } });
  });

  it("returns webVitals enabled false when pulse_wv_enabled is false", () => {
    const p = readManualWebVitalsInstrumentation(
      new URLSearchParams("pulse_wv_enabled=false"),
    );
    expect(p).toEqual({ webVitals: { enabled: false } });
  });

  it("returns webVitals enabled true when pulse_wv_enabled is 1", () => {
    const p = readManualWebVitalsInstrumentation(
      new URLSearchParams("pulse_wv_enabled=1"),
    );
    expect(p).toEqual({ webVitals: { enabled: true } });
  });

  it("returns undefined when param absent", () => {
    expect(readManualWebVitalsInstrumentation(new URLSearchParams())).toBe(
      undefined,
    );
  });
});
