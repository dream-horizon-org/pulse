/**
 * Integration-style test: real {@link createProviders} + real {@link BeforeSendSpanExporter},
 * with browser OTLP exporters mocked so no network/IndexedDB side effects.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { emptyResource } from "@opentelemetry/resources";
import { ExportResultCode } from "@opentelemetry/core";
import type { ReadableSpan } from "@opentelemetry/sdk-trace-web";

const { traceInnerExport, logInnerExport, metricInnerExport } = vi.hoisted(
  () => ({
    traceInnerExport: vi.fn(
      (_: ReadableSpan[], cb: (r: { code: number }) => void) => {
        cb({ code: ExportResultCode.SUCCESS });
      },
    ),
    logInnerExport: vi.fn((_: unknown[], cb: (r: { code: number }) => void) => {
      cb({ code: ExportResultCode.SUCCESS });
    }),
    metricInnerExport: vi.fn(
      (_: unknown, cb: (r: { code: number }) => void) => {
        cb({ code: ExportResultCode.SUCCESS });
      },
    ),
  }),
);

vi.mock("../exporters/pulse-browser-otlp-exporters", () => ({
  PulseBrowserTraceExporter: vi.fn().mockImplementation(() => ({
    export: traceInnerExport,
    shutdown: () => Promise.resolve(),
    forceFlush: () => Promise.resolve(),
  })),
  PulseBrowserLogExporter: vi.fn().mockImplementation(() => ({
    export: logInnerExport,
    shutdown: () => Promise.resolve(),
    forceFlush: () => Promise.resolve(),
  })),
  createPulseBrowserMetricExporter: vi.fn().mockImplementation(() => ({
    export: metricInnerExport,
    shutdown: () => Promise.resolve(),
    forceFlush: () => Promise.resolve(),
    selectAggregationTemporality: () => undefined,
    selectAggregation: () => undefined,
  })),
}));

import { createProviders } from "../exporters";

describe("createProviders + beforeSendData", () => {
  beforeEach(() => {
    traceInnerExport.mockClear();
    logInnerExport.mockClear();
    metricInnerExport.mockClear();
  });

  const baseExporterConfig = {
    endpointBaseUrl: "http://localhost:4318",
    apiKey: "default-project_devkey01",
    meteringSessionId: "meter-session",
    useProtobuf: false,
    diskBuffering: { enabled: false },
  };

  it("invokes beforeSendSpan before inner trace exporter on flush", async () => {
    const beforeSendSpan = vi.fn((s: ReadableSpan) => s);
    const bundle = createProviders(
      { ...baseExporterConfig, beforeSendData: { beforeSendSpan } },
      emptyResource(),
      [],
      [],
    );
    const tracer = bundle.tracerProvider.getTracer("test");
    tracer.startSpan("hello").end();
    await bundle.tracerProvider.forceFlush();
    expect(beforeSendSpan).toHaveBeenCalled();
    expect(traceInnerExport).toHaveBeenCalled();
    bundle.cleanup();
    await bundle.tracerProvider.shutdown();
    await bundle.loggerProvider.shutdown();
    await bundle.meterProvider.shutdown();
  });

  it("drops span when beforeSendSpan returns null — inner trace export not called", async () => {
    const beforeSendSpan = vi.fn(() => null as unknown as ReadableSpan);
    const bundle = createProviders(
      { ...baseExporterConfig, beforeSendData: { beforeSendSpan } },
      emptyResource(),
      [],
      [],
    );
    const tracer = bundle.tracerProvider.getTracer("test");
    tracer.startSpan("dropped").end();
    await bundle.tracerProvider.forceFlush();
    expect(beforeSendSpan).toHaveBeenCalled();
    expect(traceInnerExport).not.toHaveBeenCalled();
    bundle.cleanup();
    await bundle.tracerProvider.shutdown();
    await bundle.loggerProvider.shutdown();
    await bundle.meterProvider.shutdown();
  });
});
