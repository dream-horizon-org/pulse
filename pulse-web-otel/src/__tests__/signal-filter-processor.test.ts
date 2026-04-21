import { describe, it, expect } from "vitest";
import { context } from "@opentelemetry/api";

import { SignalFilterProcessor } from "../processors/signal-filter-processor";
import type { Span } from "@opentelemetry/api";
import type { LogRecord } from "@opentelemetry/sdk-logs";
import type { PulseSignalConfig } from "../types/remote-config";

describe("SignalFilterProcessor", () => {
  it("removes trace attributes in onStart when TRACES condition matches span name", () => {
    const attrs: Record<string, string> = {
      "screen.name": "/products",
      "pulse.type": "sdk.init",
      platform: "web",
    };
    const span = { name: "sdk.init", attributes: attrs } as unknown as Span;

    const signalConfig: PulseSignalConfig = {
      scheduleDurationMs: 5000,
      attributesToDrop: [
        {
          values: ["screen.name"],
          condition: {
            name: "^sdk\\.init$",
            props: [],
            scopes: ["TRACES"],
            sdks: ["pulse_web_js"],
          },
        },
      ],
      attributesToAdd: [],
      filters: { mode: "BLACKLIST", values: [] },
    };

    const proc = new SignalFilterProcessor(signalConfig);
    proc.onStart(span, context.active());

    expect(attrs["screen.name"]).toBeUndefined();
    expect(attrs["pulse.type"]).toBe("sdk.init");
  });

  it("drops trace attributes whose keys match drop value regex patterns", () => {
    const attrs: Record<string, string> = {
      "screen.name": "/x",
      "screen.class": "Y",
      "pulse.type": "sdk.init",
    };
    const span = { name: "sdk.init", attributes: attrs } as unknown as Span;
    const signalConfig: PulseSignalConfig = {
      scheduleDurationMs: 5000,
      attributesToDrop: [
        {
          values: ["screen\\..*"],
          condition: {
            name: "^sdk\\.init$",
            props: [],
            scopes: ["TRACES"],
            sdks: ["pulse_web_js"],
          },
        },
      ],
      attributesToAdd: [],
      filters: { mode: "BLACKLIST", values: [] },
    };
    new SignalFilterProcessor(signalConfig).onStart(span, context.active());
    expect(attrs["screen.name"]).toBeUndefined();
    expect(attrs["screen.class"]).toBeUndefined();
    expect(attrs["pulse.type"]).toBe("sdk.init");
  });

  it("does not drop when span name does not match", () => {
    const attrs = { "screen.name": "/home" };
    const span = { name: "http.get", attributes: attrs } as unknown as Span;
    const signalConfig: PulseSignalConfig = {
      scheduleDurationMs: 5000,
      attributesToDrop: [
        {
          values: ["screen.name"],
          condition: {
            name: "^sdk\\.init$",
            props: [],
            scopes: ["TRACES"],
            sdks: ["pulse_web_js"],
          },
        },
      ],
      attributesToAdd: [],
      filters: { mode: "BLACKLIST", values: [] },
    };
    new SignalFilterProcessor(signalConfig).onStart(span, context.active());
    expect(attrs["screen.name"]).toBe("/home");
  });

  it("removes log attributes when LOGS condition matches log body", () => {
    const attrs: Record<string, string> = {
      "screen.name": "/cart",
      "pulse.type": "session.start",
    };
    const logRecord = {
      body: "session.start",
      attributes: attrs,
    } as unknown as LogRecord;

    const signalConfig: PulseSignalConfig = {
      scheduleDurationMs: 5000,
      attributesToDrop: [
        {
          values: ["screen.name"],
          condition: {
            name: "^session\\.start$",
            props: [],
            scopes: ["LOGS"],
            sdks: ["pulse_web_js"],
          },
        },
      ],
      attributesToAdd: [],
      filters: { mode: "BLACKLIST", values: [] },
    };

    new SignalFilterProcessor(signalConfig).onEmit(logRecord);
    expect(attrs["screen.name"]).toBeUndefined();
    expect(attrs["pulse.type"]).toBe("session.start");
  });

  it("only adds attributes when full Pulse condition matches", () => {
    const signalConfig: PulseSignalConfig = {
      scheduleDurationMs: 5000,
      attributesToDrop: [],
      attributesToAdd: [
        {
          values: [{ name: "x.y", value: "1", type: "STRING" }],
          condition: {
            name: "^http\\.get$",
            props: [{ key: "pulse\\.type", value: "session\\.start" }],
            scopes: ["TRACES"],
            sdks: ["pulse_web_js"],
          },
        },
      ],
      filters: { mode: "BLACKLIST", values: [] },
    };
    const proc = new SignalFilterProcessor(signalConfig);

    const attrsNoMatch: Record<string, string> = {
      "pulse.type": "http.request",
    };
    const spanBad = {
      name: "http.get",
      attributes: attrsNoMatch,
    } as unknown as Span;
    proc.onStart(spanBad, context.active());
    expect(attrsNoMatch["x.y"]).toBeUndefined();

    const attrsMatch: Record<string, string> = {
      "pulse.type": "session.start",
    };
    const spanOk = {
      name: "http.get",
      attributes: attrsMatch,
      setAttribute(k: string, v: unknown) {
        attrsMatch[k] = String(v);
        return this;
      },
    } as unknown as Span;
    proc.onStart(spanOk, context.active());
    expect(attrsMatch["x.y"]).toBe("1");
  });
});
