import { describe, it, expect } from "vitest";
import { parseWebVitalsStressSearchParams } from "../webVitalsStressConfig";

describe("parseWebVitalsStressSearchParams", () => {
  it("defaults to off with default probability", () => {
    const p = parseWebVitalsStressSearchParams(new URLSearchParams(""));
    expect(p.mode).toBe("off");
    expect(p.probability).toBe(0.35);
    expect(p.seed).toBeUndefined();
    expect(p.severity).toBe("mild");
  });

  it("reads primary keys", () => {
    const p = parseWebVitalsStressSearchParams(
      new URLSearchParams(
        "pulse_wv_stress=all&pulse_wv_stress_p=0.9&pulse_wv_stress_seed=7&pulse_wv_stress_severity=severe",
      ),
    );
    expect(p.mode).toBe("all");
    expect(p.probability).toBe(0.9);
    expect(p.seed).toBe(7);
    expect(p.severity).toBe("severe");
  });

  it("reads short aliases _p _seed _severity", () => {
    const p = parseWebVitalsStressSearchParams(
      new URLSearchParams("pulse_wv_stress=cls&_p=1&_seed=3&_severity=severe"),
    );
    expect(p.mode).toBe("cls");
    expect(p.probability).toBe(1);
    expect(p.seed).toBe(3);
    expect(p.severity).toBe("severe");
  });
});
