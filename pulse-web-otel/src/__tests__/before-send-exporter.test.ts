import { describe, it, expect, vi } from "vitest";
import { emptyResource } from "@opentelemetry/resources";
import type { ExportResult } from "@opentelemetry/core";
import { ExportResultCode } from "@opentelemetry/core";
import type { ReadableSpan, SpanExporter } from "@opentelemetry/sdk-trace-web";
import type {
  LogRecordExporter,
  ReadableLogRecord,
} from "@opentelemetry/sdk-logs";
import type {
  PushMetricExporter,
  ResourceMetrics,
} from "@opentelemetry/sdk-metrics";

import type {
  PulseExportSignal,
  PulseBeforeSendResult,
  PulseWebBeforeSendCallbacks,
} from "../types/before-send";
import {
  validateBeforeSendConfig,
  resolveBeforeSend,
  isReadableSpan,
} from "../before-send";
import {
  BeforeSendLogRecordExporter,
  BeforeSendMetricExporter,
  BeforeSendSpanExporter,
} from "../exporters/before-send-exporters";

function mockSpan(name: string): ReadableSpan {
  return {
    name,
    spanContext: () => ({
      traceId: "a".repeat(32),
      spanId: "b".repeat(16),
      traceFlags: 1,
    }),
  } as unknown as ReadableSpan;
}

describe("validateBeforeSendConfig", () => {
  it("accepts a function", () => {
    expect(() => validateBeforeSendConfig(() => null)).not.toThrow();
  });

  it("rejects non-function callback fields", () => {
    expect(() =>
      validateBeforeSendConfig({ beforeSendSpan: 1 } as never),
    ).toThrow(
      "[Pulse] beforeSendData.beforeSendSpan must be a function when provided",
    );
  });
});

describe("BeforeSendSpanExporter", () => {
  it("invokes generic then typed; null from generic drops (Android order)", () => {
    const generic = vi.fn(
      (_s: PulseExportSignal): PulseBeforeSendResult => null,
    );
    const typed = vi.fn((s: ReadableSpan) => s);
    const delegate: SpanExporter = {
      export: vi.fn((_spans, cb) => cb({ code: ExportResultCode.SUCCESS })),
      shutdown: async () => {},
      forceFlush: async () => {},
    };
    const exp = new BeforeSendSpanExporter(delegate, {
      beforeSend: generic,
      beforeSendSpan: typed,
    });
    const cb = vi.fn();
    exp.export([mockSpan("x")], cb);
    expect(generic).toHaveBeenCalledTimes(1);
    expect(typed).not.toHaveBeenCalled();
    expect(delegate.export).not.toHaveBeenCalled();
    expect(cb).toHaveBeenCalledWith({ code: ExportResultCode.SUCCESS });
  });

  it("drops when generic returns wrong type", () => {
    const delegate: SpanExporter = {
      export: vi.fn((_spans, cb: (r: ExportResult) => void) =>
        cb({ code: ExportResultCode.SUCCESS }),
      ),
      shutdown: async () => {},
      forceFlush: async () => {},
    };
    const exp = new BeforeSendSpanExporter(delegate, {
      // Intentionally wrong return shape — runtime must drop (see applyBeforeSendGeneric).
      beforeSend: ((_s: PulseExportSignal) => ({
        not: "a span",
      })) as unknown as PulseWebBeforeSendCallbacks["beforeSend"],
    });
    const cb = vi.fn();
    exp.export([mockSpan("x")], cb);
    expect(delegate.export).not.toHaveBeenCalled();
    expect(cb).toHaveBeenCalledWith({ code: ExportResultCode.SUCCESS });
  });

  it("forwards when only beforeSendSpan is set (implicit identity generic)", () => {
    const delegate: SpanExporter = {
      export: vi.fn((_spans, cb) => cb({ code: ExportResultCode.SUCCESS })),
      shutdown: async () => {},
      forceFlush: async () => {},
    };
    const exp = new BeforeSendSpanExporter(delegate, {
      beforeSendSpan: (s) => s,
    });
    exp.export([mockSpan("a")], vi.fn());
    expect(vi.mocked(delegate.export)).toHaveBeenCalledTimes(1);
    const passed = vi.mocked(delegate.export).mock
      .calls[0]![0] as ReadableSpan[];
    expect(passed).toHaveLength(1);
    expect(passed[0]!.name).toBe("a");
  });

  it("typed null drops span", () => {
    const delegate: SpanExporter = {
      export: vi.fn((_s, cb) => cb({ code: ExportResultCode.SUCCESS })),
      shutdown: async () => {},
      forceFlush: async () => {},
    };
    const exp = new BeforeSendSpanExporter(delegate, {
      beforeSendSpan: () => null,
    });
    exp.export([mockSpan("z")], vi.fn());
    expect(delegate.export).not.toHaveBeenCalled();
  });
});

describe("BeforeSendLogRecordExporter", () => {
  it("filters logs with generic null", () => {
    const log = {
      resource: emptyResource(),
    } as unknown as ReadableLogRecord;
    const delegate: LogRecordExporter = {
      export: vi.fn((_logs, cb) => cb({ code: ExportResultCode.SUCCESS })),
      shutdown: async () => {},
      forceFlush: async () => {},
    };
    const exp = new BeforeSendLogRecordExporter(delegate, {
      beforeSend: () => null,
    });
    exp.export([log], vi.fn());
    expect(delegate.export).not.toHaveBeenCalled();
  });
});

describe("BeforeSendMetricExporter", () => {
  const emptyRm: ResourceMetrics = {
    resource: emptyResource(),
    scopeMetrics: [],
  };

  it("drops when beforeSendMetric returns null", () => {
    const delegate: PushMetricExporter = {
      export: vi.fn((_m, cb) => cb({ code: ExportResultCode.SUCCESS })),
      shutdown: async () => {},
      forceFlush: async () => {},
    };
    const exp = new BeforeSendMetricExporter(delegate, {
      beforeSendMetric: () => null,
    });
    exp.export(emptyRm, vi.fn());
    expect(delegate.export).not.toHaveBeenCalled();
  });

  it("forwards ResourceMetrics after generic pass-through", () => {
    const delegate: PushMetricExporter = {
      export: vi.fn((_m, cb) => cb({ code: ExportResultCode.SUCCESS })),
      shutdown: async () => {},
      forceFlush: async () => {},
    };
    const exp = new BeforeSendMetricExporter(delegate, {
      beforeSend: (x) => x,
    });
    exp.export(emptyRm, vi.fn());
    expect(delegate.export).toHaveBeenCalledWith(emptyRm, expect.any(Function));
  });
});

describe("resolveBeforeSend", () => {
  it("wraps a function as generic-only hooks", () => {
    const fn = (x: PulseExportSignal) => x;
    const r = resolveBeforeSend(fn);
    expect(r?.beforeSend).toBe(fn);
    expect(r?.beforeSendSpan).toBeUndefined();
  });
});

describe("isReadableSpan", () => {
  it("identifies mock span", () => {
    expect(isReadableSpan(mockSpan("n"))).toBe(true);
    expect(isReadableSpan({ resource: emptyResource() })).toBe(false);
  });
});
