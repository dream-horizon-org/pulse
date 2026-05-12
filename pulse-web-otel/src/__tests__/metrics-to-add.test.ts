import { describe, it, expect } from "vitest";
import { emptyResource } from "@opentelemetry/resources";
import {
  AggregationTemporality,
  InMemoryMetricExporter,
  MeterProvider,
  PeriodicExportingMetricReader,
} from "@opentelemetry/sdk-metrics";

import { mergePulseSdkConfig } from "../remote-config";
import {
  applyMetricsToAddToSpans,
  buildMetricsToAddPairs,
} from "../sampling/metrics-to-add-apply";
import { sanitizeInstrumentationName } from "../sampling/sanitize-instrumentation-name";
import type { PulseMetricsToAddEntry } from "../types/remote-config";
import type { ReadableSpan } from "@opentelemetry/sdk-trace-web";

describe("sanitizeInstrumentationName", () => {
  it("prefixes with m when first char is not a letter", () => {
    expect(sanitizeInstrumentationName("123")).toBe("m123");
  });

  it("replaces unsupported chars with underscore", () => {
    expect(sanitizeInstrumentationName("a:b")).toBe("a_b");
  });
});

describe("mergePulseSdkConfig metricsToAdd", () => {
  it("normalizes nested conditions, scopes, and backend prop name→key", () => {
    const raw = {
      version: 3,
      sampling: { default: { sessionSampleRate: 1 }, rules: [] },
      signals: {
        scheduleDurationMs: 5000,
        attributesToDrop: [],
        attributesToAdd: [],
        filters: { mode: "BLACKLIST", values: [] },
        metricsToAdd: [
          {
            name: "span_count",
            target: { type: "name" },
            condition: {
              name: ".*",
              props: [{ name: "pulse\\.type", value: "x" }],
              scopes: ["traces"],
              sdks: ["pulse_web_js"],
            },
            type: { type: "counter" },
            attributesToPick: [
              {
                name: ".*",
                props: [{ name: "session\\..*", value: "" }],
                scopes: ["traces"],
                sdks: ["pulse_web_js"],
              },
            ],
          },
        ],
      },
      interaction: { beforeInitQueueSize: 1 },
      features: [],
    };
    const m = mergePulseSdkConfig(raw as never);
    const e = m.signals.metricsToAdd[0]!;
    expect(e.condition.scopes).toEqual(["TRACES"]);
    expect(e.condition.props[0]?.key).toBe("pulse\\.type");
    expect(e.attributesToPick?.[0]?.props[0]?.key).toBe("session\\..*");
  });
});

describe("metricsToAdd span export", () => {
  it("increments a counter for each matching span (Android-style)", async () => {
    const mem = new InMemoryMetricExporter(AggregationTemporality.CUMULATIVE);
    const reader = new PeriodicExportingMetricReader({
      exporter: mem,
      exportIntervalMillis: 600_000,
    });
    const mp = new MeterProvider({
      resource: emptyResource(),
      readers: [reader],
    });
    const meter = mp.getMeter("pulse.web.metrics_derived", "1.0.0");

    const entry: PulseMetricsToAddEntry = {
      name: "signal_event_count",
      target: { type: "name" },
      condition: {
        name: ".*",
        props: [],
        scopes: ["TRACES"],
        sdks: ["pulse_web_js"],
      },
      type: { type: "counter" },
    };

    const pairs = buildMetricsToAddPairs(
      [entry],
      "TRACES",
      "pulse_web_js",
      () => meter,
    );

    const spans = [
      { name: "a", attributes: {} },
      { name: "b", attributes: {} },
    ] as unknown as ReadableSpan[];

    applyMetricsToAddToSpans(pairs, "pulse_web_js", spans);

    await mp.forceFlush();
    const exported = mem.getMetrics();
    expect(exported.length).toBeGreaterThanOrEqual(1);
    const scope = exported[0]!.scopeMetrics.find((s) =>
      s.metrics.some((m) => m.descriptor.name === "signal_event_count"),
    );
    expect(scope).toBeDefined();
    const metric = scope!.metrics.find(
      (m) => m.descriptor.name === "signal_event_count",
    );
    expect(metric).toBeDefined();
    const sum = metric!.dataPoints.reduce(
      (acc, dp) => acc + ("value" in dp ? Number(dp.value) : 0),
      0,
    );
    expect(sum).toBe(2);
  });
});
