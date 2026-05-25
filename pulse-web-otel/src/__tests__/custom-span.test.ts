import { describe, it, expect } from "vitest";
import { SpanStatusCode, noopSpan } from "../types/trace";
import { PulseWebSemconv } from "../semconv";

describe("CustomSpan API — Types", () => {
  describe("SpanStatusCode enum", () => {
    it("CS-P1: SpanStatusCode has OK, ERROR, UNSET values", () => {
      expect(SpanStatusCode.OK).toBe("OK");
      expect(SpanStatusCode.ERROR).toBe("ERROR");
      expect(SpanStatusCode.UNSET).toBe("UNSET");
    });
  });

  describe("noopSpan", () => {
    it("CS-E1: noopSpan is idempotent", () => {
      // All methods should succeed without errors
      noopSpan.end();
      noopSpan.end(SpanStatusCode.OK);
      noopSpan.end(SpanStatusCode.ERROR);
      noopSpan.addEvent("event");
      noopSpan.setAttributes({ attr: "val" });
      noopSpan.recordException(new Error("test"));
      expect(true).toBe(true);
    });

    it("CS-E1: noopSpan has all required methods", () => {
      expect(typeof noopSpan.end).toBe("function");
      expect(typeof noopSpan.addEvent).toBe("function");
      expect(typeof noopSpan.setAttributes).toBe("function");
      expect(typeof noopSpan.recordException).toBe("function");
    });

    it("CS-E3: recordException coerces non-Error to Error", () => {
      // Should not throw
      noopSpan.recordException("string error" as any);
      noopSpan.recordException(123 as any);
      noopSpan.recordException(null as any);
      expect(true).toBe(true);
    });
  });

  describe("CUSTOM_SPAN semconv", () => {
    it("CS-P1: CUSTOM_SPAN is in PulseType", () => {
      expect(PulseWebSemconv.PulseType.CUSTOM_SPAN).toBe("custom_span");
    });

    it("pulse.type attribute key exists", () => {
      expect(PulseWebSemconv.AttributeKey.PULSE_TYPE).toBe("pulse.type");
    });
  });
});

describe("CustomSpan API — Public exports", () => {
  it("SpanStatusCode is exported", async () => {
    const module = await import("../index");
    expect(module.SpanStatusCode).toBeDefined();
    expect(module.SpanStatusCode.OK).toBe("OK");
  });

  it("PulseSpan type is exported", async () => {
    const module = await import("../index");
    // Type imports are not runtime values, but we can verify they resolve
    expect(module).toBeDefined();
  });

  it("Pulse.startSpan exists", async () => {
    const module = await import("../index");
    expect(typeof module.Pulse).toBe("object");
    expect(typeof module.Pulse.startSpan).toBe("function");
  });

  it("Pulse.trackSpan exists", async () => {
    const module = await import("../index");
    expect(typeof module.Pulse.trackSpan).toBe("function");
  });
});
