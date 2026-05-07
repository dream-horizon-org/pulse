import { describe, it, expect, vi, beforeEach } from "vitest";

const {
  spanBatchOptions,
  logBatchOptions,
  metricReaderOptions,
  traceSwitchToKeepalive,
  logSwitchToKeepalive,
} = vi.hoisted(() => ({
  spanBatchOptions: [] as unknown[],
  logBatchOptions: [] as unknown[],
  metricReaderOptions: [] as unknown[],
  traceSwitchToKeepalive: vi.fn(),
  logSwitchToKeepalive: vi.fn(),
}));

vi.mock("@opentelemetry/sdk-trace-web", () => {
  class BatchSpanProcessor {
    constructor(_exporter: unknown, options: unknown) {
      spanBatchOptions.push(options);
    }
  }
  class WebTracerProvider {
    addSpanProcessor = vi.fn();
    constructor(_opts: unknown) {}
  }
  return { BatchSpanProcessor, WebTracerProvider };
});

vi.mock("@opentelemetry/sdk-logs", () => {
  class BatchLogRecordProcessor {
    constructor(_exporter: unknown, options: unknown) {
      logBatchOptions.push(options);
    }
  }
  class LoggerProvider {
    addLogRecordProcessor = vi.fn();
    constructor(_opts: unknown) {}
  }
  return { BatchLogRecordProcessor, LoggerProvider };
});

vi.mock("@opentelemetry/sdk-metrics", () => {
  class PeriodicExportingMetricReader {
    constructor(options: unknown) {
      metricReaderOptions.push(options);
    }
  }
  class MeterProvider {
    getMeter = vi.fn(() => ({}));
    constructor(_opts: unknown) {}
  }
  return { PeriodicExportingMetricReader, MeterProvider };
});

vi.mock("../exporters/pulse-browser-otlp-exporters", () => {
  class PulseBrowserTraceExporter {
    switchToKeepalive = traceSwitchToKeepalive;
    constructor(_params: unknown, _opts: unknown) {}
  }
  class PulseBrowserLogExporter {
    switchToKeepalive = logSwitchToKeepalive;
    constructor(_params: unknown, _opts: unknown) {}
  }
  function createPulseBrowserMetricExporter() {
    return {
      export: vi.fn(),
      shutdown: vi.fn(),
      forceFlush: vi.fn(),
      selectAggregationTemporality: undefined,
      selectAggregation: undefined,
    };
  }
  return {
    PulseBrowserTraceExporter,
    PulseBrowserLogExporter,
    createPulseBrowserMetricExporter,
  };
});

vi.mock("../persistence/indexed-db", () => {
  class IdbSignalBuffer {
    constructor(_maxAgeMs?: number, _maxCacheSizeBytes?: number) {}
  }
  return { IdbSignalBuffer };
});

import { Resource } from "@opentelemetry/resources";
import { createProviders } from "../exporters";
import { DEFAULT_BATCH_OPTIONS } from "../constants/exporters";

describe("Exporter batching and queue guardrails", () => {
  beforeEach(() => {
    spanBatchOptions.length = 0;
    logBatchOptions.length = 0;
    metricReaderOptions.length = 0;
    traceSwitchToKeepalive.mockClear();
    logSwitchToKeepalive.mockClear();
  });

  it("keeps safe queue/batch invariants", () => {
    expect(DEFAULT_BATCH_OPTIONS.maxQueueSize).toBeGreaterThan(0);
    expect(DEFAULT_BATCH_OPTIONS.maxExportBatchSize).toBeGreaterThan(0);
    expect(DEFAULT_BATCH_OPTIONS.exportTimeoutMillis).toBeGreaterThan(0);
    expect(DEFAULT_BATCH_OPTIONS.maxQueueSize).toBeGreaterThanOrEqual(
      DEFAULT_BATCH_OPTIONS.maxExportBatchSize,
    );
  });

  it("wires same batch options into span/log processors and metric interval", () => {
    createProviders(
      {
        endpointBaseUrl: "http://localhost:4318",
        apiKey: "default-project_devkey01",
        meteringSessionId: "m1",
        diskBuffering: { enabled: false },
      },
      Resource.empty(),
      [],
      [],
    );

    expect(spanBatchOptions).toHaveLength(1);
    expect(logBatchOptions).toHaveLength(1);
    expect(metricReaderOptions).toHaveLength(1);

    expect(spanBatchOptions[0]).toEqual(DEFAULT_BATCH_OPTIONS);
    expect(logBatchOptions[0]).toEqual(DEFAULT_BATCH_OPTIONS);
    expect(metricReaderOptions[0]).toMatchObject({
      exportIntervalMillis: DEFAULT_BATCH_OPTIONS.scheduledDelayMillis,
    });
  });

  it("prepareForDocumentUnload switches trace/log exporters to keepalive mode", () => {
    const bundle = createProviders(
      {
        endpointBaseUrl: "http://localhost:4318",
        apiKey: "default-project_devkey01",
        meteringSessionId: "m2",
        diskBuffering: { enabled: false },
      },
      Resource.empty(),
      [],
      [],
    );

    bundle.prepareForDocumentUnload?.();
    expect(traceSwitchToKeepalive).toHaveBeenCalledTimes(1);
    expect(logSwitchToKeepalive).toHaveBeenCalledTimes(1);
  });
});
