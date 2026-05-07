/**
 * Unit tests for createPulseInstrumentationHandler.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockFetch = vi.fn().mockResolvedValue({ ok: true });
vi.stubGlobal("fetch", mockFetch);

import { createPulseInstrumentationHandler } from "../integrations/next/instrumentation";

const CONFIG = {
  apiKey: "test-api-key",
  collectorEndpoint: "https://collector.example.com/v1/logs",
  serviceName: "test-service",
};

const ERROR = { message: "Server exploded", name: "Error", stack: "Error: Server exploded\n  at foo" };
const REQUEST = { path: "/api/products", method: "GET" };
const CONTEXT = { routerKind: "App Router", routePath: "/api/products", routeType: "route" };

describe("createPulseInstrumentationHandler", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetch.mockResolvedValue({ ok: true });
  });

  it("calls fetch with the configured collector endpoint", () => {
    const handler = createPulseInstrumentationHandler(CONFIG);
    handler(ERROR, REQUEST, CONTEXT);
    expect(mockFetch).toHaveBeenCalledWith(
      CONFIG.collectorEndpoint,
      expect.any(Object),
    );
  });

  it("sends X-API-KEY header (aligned with browser transport contract)", () => {
    const handler = createPulseInstrumentationHandler(CONFIG);
    handler(ERROR, REQUEST, CONTEXT);
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>)["X-API-KEY"]).toBe(
      "test-api-key",
    );
    // Confirm old Bearer header is gone
    expect((init.headers as Record<string, string>)["Authorization"]).toBeUndefined();
  });

  it("sends OTLP JSON body with device.crash pulse.type", () => {
    const handler = createPulseInstrumentationHandler(CONFIG);
    handler(ERROR, REQUEST, CONTEXT);
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(init.body as string);
    const logRecord =
      body.resourceLogs[0].scopeLogs[0].logRecords[0];
    const attrs: Record<string, { stringValue?: string }> = {};
    for (const a of logRecord.attributes) {
      attrs[a.key] = a.value;
    }
    expect(attrs["pulse.type"]?.stringValue).toBe("device.crash");
  });

  it("includes exception.message in OTLP attributes", () => {
    const handler = createPulseInstrumentationHandler(CONFIG);
    handler(ERROR, REQUEST, CONTEXT);
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(init.body as string);
    const logRecord = body.resourceLogs[0].scopeLogs[0].logRecords[0];
    const attrs: Record<string, { stringValue?: string }> = {};
    for (const a of logRecord.attributes) {
      attrs[a.key] = a.value;
    }
    expect(attrs["exception.message"]?.stringValue).toBe("Server exploded");
  });

  it("uses request.path for server.request_path", () => {
    const handler = createPulseInstrumentationHandler(CONFIG);
    handler(ERROR, REQUEST, CONTEXT);
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(init.body as string);
    const logRecord = body.resourceLogs[0].scopeLogs[0].logRecords[0];
    const attrs: Record<string, { stringValue?: string }> = {};
    for (const a of logRecord.attributes) {
      attrs[a.key] = a.value;
    }
    expect(attrs["server.request_path"]?.stringValue).toBe("/api/products");
  });

  it("falls back to request.url when request.path is missing", () => {
    const handler = createPulseInstrumentationHandler(CONFIG);
    handler(ERROR, { url: "/fallback" }, CONTEXT);
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(init.body as string);
    const logRecord = body.resourceLogs[0].scopeLogs[0].logRecords[0];
    const attrs: Record<string, { stringValue?: string }> = {};
    for (const a of logRecord.attributes) {
      attrs[a.key] = a.value;
    }
    expect(attrs["server.request_path"]?.stringValue).toBe("/fallback");
  });

  it("includes serviceName in resource attributes", () => {
    const handler = createPulseInstrumentationHandler(CONFIG);
    handler(ERROR, REQUEST, CONTEXT);
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(init.body as string);
    const resAttrs: Record<string, { stringValue?: string }> = {};
    for (const a of body.resourceLogs[0].resource.attributes) {
      resAttrs[a.key] = a.value;
    }
    expect(resAttrs["service.name"]?.stringValue).toBe("test-service");
  });

  it("does not throw even if fetch rejects (best-effort)", async () => {
    mockFetch.mockRejectedValue(new Error("network error"));
    const handler = createPulseInstrumentationHandler(CONFIG);
    expect(() => handler(ERROR, REQUEST, CONTEXT)).not.toThrow();
    await new Promise((r) => setTimeout(r, 10));
  });

  // ─── OTLP body completeness ───────────────────────────────────────────────

  it("includes exception.type (error.name) in OTLP attributes", () => {
    const handler = createPulseInstrumentationHandler(CONFIG);
    handler(ERROR, REQUEST, CONTEXT);
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(init.body as string);
    const logRecord = body.resourceLogs[0].scopeLogs[0].logRecords[0];
    const attrs: Record<string, { stringValue?: string }> = {};
    for (const a of logRecord.attributes) attrs[a.key] = a.value;
    expect(attrs["exception.type"]?.stringValue).toBe("Error");
  });

  it("includes exception.stacktrace in OTLP attributes", () => {
    const handler = createPulseInstrumentationHandler(CONFIG);
    handler(ERROR, REQUEST, CONTEXT);
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(init.body as string);
    const logRecord = body.resourceLogs[0].scopeLogs[0].logRecords[0];
    const attrs: Record<string, { stringValue?: string }> = {};
    for (const a of logRecord.attributes) attrs[a.key] = a.value;
    expect(attrs["exception.stacktrace"]?.stringValue).toContain("at foo");
  });

  it("includes platform=web in resource attributes", () => {
    const handler = createPulseInstrumentationHandler(CONFIG);
    handler(ERROR, REQUEST, CONTEXT);
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(init.body as string);
    const resAttrs: Record<string, { stringValue?: string }> = {};
    for (const a of body.resourceLogs[0].resource.attributes) resAttrs[a.key] = a.value;
    expect(resAttrs["platform"]?.stringValue).toBe("web");
  });

  it("includes server.router_kind from context", () => {
    const handler = createPulseInstrumentationHandler(CONFIG);
    handler(ERROR, REQUEST, { ...CONTEXT, routerKind: "Pages Router" });
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(init.body as string);
    const logRecord = body.resourceLogs[0].scopeLogs[0].logRecords[0];
    const attrs: Record<string, { stringValue?: string }> = {};
    for (const a of logRecord.attributes) attrs[a.key] = a.value;
    expect(attrs["server.router_kind"]?.stringValue).toBe("Pages Router");
  });

  // ─── Edge: missing fields ─────────────────────────────────────────────────

  it("sends empty server.request_path when both path and url are absent", () => {
    const handler = createPulseInstrumentationHandler(CONFIG);
    expect(() => handler(ERROR, {}, CONTEXT)).not.toThrow();
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(init.body as string);
    const logRecord = body.resourceLogs[0].scopeLogs[0].logRecords[0];
    const attrs: Record<string, { stringValue?: string }> = {};
    for (const a of logRecord.attributes) attrs[a.key] = a.value;
    expect(attrs["server.request_path"]?.stringValue).toBe("");
  });

  it("handles error with no name or stack (minimal error object)", () => {
    const handler = createPulseInstrumentationHandler(CONFIG);
    expect(() =>
      handler({ message: "bare error" }, REQUEST, CONTEXT),
    ).not.toThrow();
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    const body = JSON.parse(init.body as string);
    const logRecord = body.resourceLogs[0].scopeLogs[0].logRecords[0];
    const attrs: Record<string, { stringValue?: string }> = {};
    for (const a of logRecord.attributes) attrs[a.key] = a.value;
    expect(attrs["exception.message"]?.stringValue).toBe("bare error");
    expect(attrs["exception.type"]?.stringValue).toBe("Error"); // default
    expect(attrs["exception.stacktrace"]?.stringValue).toBe("");
  });
});
