import { describe, it, expect, vi, beforeEach } from "vitest";
import { context } from "@opentelemetry/api";
import type { SdkLogRecord } from "@opentelemetry/sdk-logs";
import { InteractionLogProcessor } from "../processors/interaction-log-processor";
import { PulseWebSemconv } from "../semconv";

function makeLogRecord(
  pulseType: string | undefined,
  body: unknown,
  hrTimeSec = 1000,
  hrTimeNano = 0,
): SdkLogRecord {
  return {
    attributes: pulseType != null ? { [PulseWebSemconv.AttributeKey.PULSE_TYPE]: pulseType } : {},
    body: body,
    hrTime: [hrTimeSec, hrTimeNano] as [number, number],
  } as unknown as SdkLogRecord;
}

describe("InteractionLogProcessor", () => {
  let processor: InteractionLogProcessor;
  const mockInstr = {
    trackEvent: vi.fn(),
    addMarkerToAll: vi.fn(),
  };

  beforeEach(() => {
    processor = new InteractionLogProcessor();
    mockInstr.trackEvent.mockClear();
    mockInstr.addMarkerToAll.mockClear();
  });

  describe("Branch A — click bridge", () => {
    it("calls trackEvent for pulse.type=app.click with string body", () => {
      processor.setInstrumentation(mockInstr as never);
      const record = makeLogRecord(PulseWebSemconv.PulseType.APP_CLICK, { stringValue: "app.widget.click" }, 1000, 500_000_000);
      processor.onEmit(record, context.active());

      expect(mockInstr.trackEvent).toHaveBeenCalledOnce();
      const [body, , timeMs] = mockInstr.trackEvent.mock.calls[0];
      expect(body).toBe("app.widget.click");
      expect(timeMs).toBeCloseTo(1000500, -1);
    });

    it("does not call trackEvent when app.click body is empty", () => {
      processor.setInstrumentation(mockInstr as never);
      const record = makeLogRecord(PulseWebSemconv.PulseType.APP_CLICK, { stringValue: "" });
      processor.onEmit(record, context.active());

      expect(mockInstr.trackEvent).not.toHaveBeenCalled();
    });

    it("does not call trackEvent when app.click body is null", () => {
      processor.setInstrumentation(mockInstr as never);
      const record = makeLogRecord(PulseWebSemconv.PulseType.APP_CLICK, null);
      processor.onEmit(record, context.active());

      expect(mockInstr.trackEvent).not.toHaveBeenCalled();
    });
  });

  describe("Branch B — marker events (device.crash / non_fatal)", () => {
    it("calls addMarkerToAll for pulse.type=device.crash", () => {
      processor.setInstrumentation(mockInstr as never);
      const record = makeLogRecord(PulseWebSemconv.PulseType.DEVICE_CRASH, { stringValue: "fatal error" }, 1000, 0);
      processor.onEmit(record, context.active());

      expect(mockInstr.addMarkerToAll).toHaveBeenCalledOnce();
      const [body, , timeMs] = mockInstr.addMarkerToAll.mock.calls[0];
      expect(body).toBe("fatal error");
      expect(timeMs).toBeCloseTo(1000000, -1);
      expect(mockInstr.trackEvent).not.toHaveBeenCalled();
    });

    it("calls addMarkerToAll for pulse.type=non_fatal", () => {
      processor.setInstrumentation(mockInstr as never);
      const record = makeLogRecord(PulseWebSemconv.PulseType.NON_FATAL, { stringValue: "soft error" }, 2000, 0);
      processor.onEmit(record, context.active());

      expect(mockInstr.addMarkerToAll).toHaveBeenCalledOnce();
      expect(mockInstr.addMarkerToAll.mock.calls[0][0]).toBe("soft error");
      expect(mockInstr.trackEvent).not.toHaveBeenCalled();
    });

    it("does NOT call addMarkerToAll when body is empty on device.crash", () => {
      processor.setInstrumentation(mockInstr as never);
      const record = makeLogRecord(PulseWebSemconv.PulseType.DEVICE_CRASH, { stringValue: "" });
      processor.onEmit(record, context.active());

      expect(mockInstr.addMarkerToAll).not.toHaveBeenCalled();
    });

    it("does NOT call trackEvent for pulse.type=device.crash (Branch B only)", () => {
      processor.setInstrumentation(mockInstr as never);
      const record = makeLogRecord(PulseWebSemconv.PulseType.DEVICE_CRASH, { stringValue: "crash" });
      processor.onEmit(record, context.active());

      expect(mockInstr.trackEvent).not.toHaveBeenCalled();
    });
  });

  describe("Neutral pulse types — neither branch", () => {
    it("does not call trackEvent for pulse.type=custom_event", () => {
      processor.setInstrumentation(mockInstr as never);
      const record = makeLogRecord(PulseWebSemconv.PulseType.CUSTOM_EVENT, { stringValue: "my_event" });
      processor.onEmit(record, context.active());

      expect(mockInstr.trackEvent).not.toHaveBeenCalled();
    });

    it("does not call trackEvent for pulse.type=session.start", () => {
      processor.setInstrumentation(mockInstr as never);
      const record = makeLogRecord("session.start", { stringValue: "session.start" });
      processor.onEmit(record, context.active());

      expect(mockInstr.trackEvent).not.toHaveBeenCalled();
    });

    it("does not call either branch when pulse.type is undefined", () => {
      processor.setInstrumentation(mockInstr as never);
      const record = makeLogRecord(undefined, { stringValue: "something" });
      processor.onEmit(record, context.active());

      expect(mockInstr.trackEvent).not.toHaveBeenCalled();
    });
  });

  describe("null instrumentation guard", () => {
    it("does not throw when instrumentation is null and app.click arrives", () => {
      const record = makeLogRecord(PulseWebSemconv.PulseType.APP_CLICK, { stringValue: "app.widget.click" });
      expect(() => processor.onEmit(record, context.active())).not.toThrow();
    });

    it("does not call trackEvent after setInstrumentation(null)", () => {
      processor.setInstrumentation(mockInstr as never);
      processor.setInstrumentation(null);
      const record = makeLogRecord(PulseWebSemconv.PulseType.APP_CLICK, { stringValue: "app.widget.click" });
      processor.onEmit(record, context.active());

      expect(mockInstr.trackEvent).not.toHaveBeenCalled();
    });
  });

  describe("lifecycle", () => {
    it("forceFlush resolves immediately", async () => {
      await expect(processor.forceFlush()).resolves.toBeUndefined();
    });

    it("shutdown resolves and clears instrumentation", async () => {
      processor.setInstrumentation(mockInstr as never);
      await expect(processor.shutdown()).resolves.toBeUndefined();
      const record = makeLogRecord(PulseWebSemconv.PulseType.APP_CLICK, { stringValue: "app.widget.click" });
      processor.onEmit(record, context.active());
      expect(mockInstr.trackEvent).not.toHaveBeenCalled();
    });
  });
});
