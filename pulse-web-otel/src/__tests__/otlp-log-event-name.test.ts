/**
 * Tier 1: prove logger.emit({ eventName }) flows to ReadableLogRecord.eventName
 * (sdk-logs ≥0.217 / api-logs with top-level event name).
 */
import { describe, it, expect, afterEach } from "vitest";
import { logs, SeverityNumber } from "@opentelemetry/api-logs";
import {
  LoggerProvider,
  InMemoryLogRecordExporter,
  SimpleLogRecordProcessor,
} from "@opentelemetry/sdk-logs";
import { resourceFromAttributes } from "@opentelemetry/resources";
import { PulseWebSemconv } from "../semconv";

describe("OTLP log eventName (emit → export)", () => {
  afterEach(() => {
    logs.disable();
  });

  it("exports ReadableLogRecord with eventName for crash and non_fatal", async () => {
    const mem = new InMemoryLogRecordExporter();
    const lp = new LoggerProvider({
      resource: resourceFromAttributes({ "service.name": "pulse-web-test" }),
      processors: [new SimpleLogRecordProcessor(mem)],
    });

    logs.setGlobalLoggerProvider(lp);
    const logger = logs.getLogger("test-scope");

    logger.emit({
      eventName: PulseWebSemconv.LogEventName.DEVICE_CRASH,
      body: "boom",
      severityNumber: SeverityNumber.FATAL,
      attributes: {
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
          PulseWebSemconv.PulseType.DEVICE_CRASH,
      },
    });

    logger.emit({
      eventName: PulseWebSemconv.LogEventName.CUSTOM_NON_FATAL,
      body: "nope",
      severityNumber: SeverityNumber.WARN,
      attributes: {
        [PulseWebSemconv.AttributeKey.PULSE_TYPE]:
          PulseWebSemconv.PulseType.NON_FATAL,
      },
    });

    await lp.forceFlush();
    const out = mem.getFinishedLogRecords();
    await lp.shutdown();
    expect(out).toHaveLength(2);
    expect(out[0]!.eventName).toBe(PulseWebSemconv.LogEventName.DEVICE_CRASH);
    expect(out[1]!.eventName).toBe(
      PulseWebSemconv.LogEventName.CUSTOM_NON_FATAL,
    );
  });
});
