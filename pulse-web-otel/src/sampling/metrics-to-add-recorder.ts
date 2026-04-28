// Factory for OTel instruments from `signals.metricsToAdd` — mirrors Android createMeterRecorderFactory.

import type { Attributes, Meter } from "@opentelemetry/api";
import { ValueType } from "@opentelemetry/api";

import type {
  PulseMetricsToAddEntry,
  PulseMetricsType,
} from "../types/remote-config";
import { sanitizeInstrumentationName } from "./sanitize-instrumentation-name";

export type DataRecorder = (value: unknown, attributes: Attributes) => void;

/** Returns a cached recorder for a (possibly suffixed) sanitized instrument name. */
export type DataRecorderFactory = (
  sanitizedInstrumentName: string,
) => DataRecorder;

function parseLongString(value: unknown): number | undefined {
  const s = String(value).trim();
  if (!/^-?\d+$/.test(s)) return undefined;
  const n = Number(s);
  if (!Number.isSafeInteger(n)) return undefined;
  return n;
}

function parseDoubleString(value: unknown): number | undefined {
  const n = Number(String(value));
  return Number.isFinite(n) ? n : undefined;
}

function buildRecorder(
  meter: Meter,
  data: PulseMetricsType,
  instrumentName: string,
): DataRecorder {
  switch (data.type) {
    case "counter": {
      const counter = meter.createCounter(instrumentName, {
        valueType: ValueType.INT,
      });
      return (_value, attributes) => {
        counter.add(1, attributes);
      };
    }
    case "gauge": {
      if (data.isFraction) {
        const gauge = meter.createGauge(instrumentName, {
          valueType: ValueType.DOUBLE,
        });
        return (value, attributes) => {
          const v = parseDoubleString(value);
          if (v !== undefined) gauge.record(v, attributes);
        };
      }
      const gauge = meter.createGauge(instrumentName, {
        valueType: ValueType.INT,
      });
      return (value, attributes) => {
        const v = parseLongString(value);
        if (v !== undefined) gauge.record(v, attributes);
      };
    }
    case "histogram": {
      const bucketBoundaries = (data.bucket ?? []).map((b) => Number(b));
      const advice =
        bucketBoundaries.length > 0
          ? { explicitBucketBoundaries: bucketBoundaries }
          : undefined;
      if (data.isFraction) {
        const histogram = meter.createHistogram(instrumentName, {
          valueType: ValueType.DOUBLE,
          advice,
        });
        return (value, attributes) => {
          const v = parseDoubleString(value);
          if (v !== undefined) histogram.record(v, attributes);
        };
      }
      const histogram = meter.createHistogram(instrumentName, {
        valueType: ValueType.INT,
        advice,
      });
      return (value, attributes) => {
        const v = parseLongString(value);
        if (v !== undefined) histogram.record(v, attributes);
      };
    }
    case "sum": {
      if (data.isFraction && data.isMonotonic) {
        const counter = meter.createCounter(instrumentName, {
          valueType: ValueType.DOUBLE,
        });
        return (value, attributes) => {
          const v = parseDoubleString(value);
          if (v !== undefined) counter.add(v, attributes);
        };
      }
      if (data.isFraction && !data.isMonotonic) {
        const udc = meter.createUpDownCounter(instrumentName, {
          valueType: ValueType.DOUBLE,
        });
        return (value, attributes) => {
          const v = parseDoubleString(value);
          if (v !== undefined) udc.add(v, attributes);
        };
      }
      if (!data.isFraction && data.isMonotonic) {
        const counter = meter.createCounter(instrumentName, {
          valueType: ValueType.INT,
        });
        return (value, attributes) => {
          const v = parseLongString(value);
          if (v !== undefined) counter.add(v, attributes);
        };
      }
      const udc = meter.createUpDownCounter(instrumentName, {
        valueType: ValueType.INT,
      });
      return (value, attributes) => {
        const v = parseLongString(value);
        if (v !== undefined) udc.add(v, attributes);
      };
    }
    default: {
      return () => {};
    }
  }
}

export function createMeterRecorderFactory(
  entry: PulseMetricsToAddEntry,
  getMeter: () => Meter,
): DataRecorderFactory {
  const recorderCache = new Map<string, DataRecorder>();
  return (metricName: string) => {
    const sanitized = sanitizeInstrumentationName(metricName);
    let recorder = recorderCache.get(sanitized);
    if (!recorder) {
      recorder = buildRecorder(getMeter(), entry.type, sanitized);
      recorderCache.set(sanitized, recorder);
    }
    return recorder;
  };
}
