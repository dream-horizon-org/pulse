/**
 * Unit tests for the SendBeacon transport fallback.
 *
 * Covers:
 *   createPulseSendBeaconTransport — direct unit tests
 *   buildBrowserExportTransport.switchToBeacon — integration-level routing tests
 *
 * Positive cases:
 *   - beacon queued successfully → status "success"
 *   - small payload routes to beacon, large payload routes to keepalive fetch
 *   - beacon failure falls back to keepalive fetch
 *   - apiKey is embedded in URL query param (not header)
 *
 * Negative cases:
 *   - sendBeacon unavailable → failure (no crash)
 *   - sendBeacon returns false → failure
 *   - both beacon AND keepalive fail → failure propagated
 *   - apiKey absent → URL has no extra query param
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  createPulseSendBeaconTransport,
  buildBrowserExportTransport,
  BEACON_BODY_LIMIT_BYTES,
  _resetBeaconKeyWarnForTesting,
} from "../exporters/otlp-transport";
import type { IdbSignalBuffer } from "../persistence/indexed-db";

// ─── Helpers ────────────────────────────────────────────────────────────────

const ENDPOINT = "https://collector.example.com/v1/logs";
const API_KEY = "test-api-key-123";
const CONTENT_TYPE = "application/json";

/** Build a Uint8Array of `size` bytes (all zeros). */
function makePayload(size: number): Uint8Array {
  return new Uint8Array(size);
}

/** Minimal IdbSignalBuffer stub that does nothing. */
function makeStubBuffer(): IdbSignalBuffer {
  return {
    write: vi.fn().mockResolvedValue(undefined),
    readAll: vi.fn().mockResolvedValue([]),
    delete: vi.fn().mockResolvedValue(undefined),
    clear: vi.fn().mockResolvedValue(undefined),
  } as unknown as IdbSignalBuffer;
}

function makeTransport(url = ENDPOINT) {
  return buildBrowserExportTransport(
    { url, headers: { "X-API-KEY": API_KEY, "Content-Type": CONTENT_TYPE } },
    {
      useGzip: false,
      diskPersistence: {
        enabled: false,
        buffer: makeStubBuffer(),
        signalKind: "log",
        meta: { contentType: CONTENT_TYPE },
      },
    },
  );
}

// ─── Tests: createPulseSendBeaconTransport ───────────────────────────────────

describe("createPulseSendBeaconTransport", () => {
  let mockSendBeacon: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    mockSendBeacon = vi.fn().mockReturnValue(true);
    vi.stubGlobal("navigator", { sendBeacon: mockSendBeacon });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  // ── Positive ──────────────────────────────────────────────────────────────

  it("returns success when sendBeacon is queued", async () => {
    const transport = createPulseSendBeaconTransport({
      url: ENDPOINT,
      apiKey: API_KEY,
      contentType: CONTENT_TYPE,
    });
    const result = await transport.send(makePayload(100), 5000);
    expect(result.status).toBe("success");
  });

  it("calls sendBeacon exactly once per send()", async () => {
    const transport = createPulseSendBeaconTransport({
      url: ENDPOINT,
      apiKey: API_KEY,
      contentType: CONTENT_TYPE,
    });
    await transport.send(makePayload(100), 5000);
    expect(mockSendBeacon).toHaveBeenCalledTimes(1);
  });

  it("sends a Blob with the correct OTLP content-type", async () => {
    const transport = createPulseSendBeaconTransport({
      url: ENDPOINT,
      apiKey: API_KEY,
      contentType: CONTENT_TYPE,
    });
    await transport.send(makePayload(64), 5000);
    const [, blob] = mockSendBeacon.mock.calls[0] as [string, Blob];
    expect(blob).toBeInstanceOf(Blob);
    expect(blob.type).toBe(CONTENT_TYPE);
  });

  it("embeds apiKey as a URL query parameter", async () => {
    const transport = createPulseSendBeaconTransport({
      url: ENDPOINT,
      apiKey: API_KEY,
      contentType: CONTENT_TYPE,
    });
    await transport.send(makePayload(64), 5000);
    const [url] = mockSendBeacon.mock.calls[0] as [string, Blob];
    expect(url).toContain(`?apiKey=${encodeURIComponent(API_KEY)}`);
    expect(url).toContain(ENDPOINT);
  });

  it("appends apiKey with & when URL already has a query string", async () => {
    const urlWithQuery = `${ENDPOINT}?source=sdk`;
    const transport = createPulseSendBeaconTransport({
      url: urlWithQuery,
      apiKey: API_KEY,
      contentType: CONTENT_TYPE,
    });
    await transport.send(makePayload(64), 5000);
    const [url] = mockSendBeacon.mock.calls[0] as [string, Blob];
    expect(url).toContain(`&apiKey=${encodeURIComponent(API_KEY)}`);
    expect(url).toContain("?source=sdk");
  });

  // ── Negative ──────────────────────────────────────────────────────────────

  it("returns failure when sendBeacon returns false (quota exceeded)", async () => {
    mockSendBeacon.mockReturnValue(false);
    const transport = createPulseSendBeaconTransport({
      url: ENDPOINT,
      apiKey: API_KEY,
      contentType: CONTENT_TYPE,
    });
    const result = await transport.send(makePayload(100), 5000);
    expect(result.status).toBe("failure");
    expect("error" in result && result.error?.message).toMatch(/rejected/i);
  });

  it("returns failure (no crash) when navigator.sendBeacon is undefined", async () => {
    vi.stubGlobal("navigator", {});
    const transport = createPulseSendBeaconTransport({
      url: ENDPOINT,
      apiKey: API_KEY,
      contentType: CONTENT_TYPE,
    });
    const result = await transport.send(makePayload(100), 5000);
    expect(result.status).toBe("failure");
  });

  it("returns failure (no crash) when navigator is undefined (SSR env)", async () => {
    vi.stubGlobal("navigator", undefined);
    const transport = createPulseSendBeaconTransport({
      url: ENDPOINT,
      apiKey: API_KEY,
      contentType: CONTENT_TYPE,
    });
    const result = await transport.send(makePayload(100), 5000);
    expect(result.status).toBe("failure");
  });

  it("does NOT append query param when apiKey is absent", async () => {
    const transport = createPulseSendBeaconTransport({
      url: ENDPOINT,
      contentType: CONTENT_TYPE,
    });
    await transport.send(makePayload(64), 5000);
    const [url] = mockSendBeacon.mock.calls[0] as [string, Blob];
    expect(url).toBe(ENDPOINT);
    expect(url).not.toContain("apiKey");
  });

  it("does NOT append query param when apiKey is empty string", async () => {
    const transport = createPulseSendBeaconTransport({
      url: ENDPOINT,
      apiKey: "",
      contentType: CONTENT_TYPE,
    });
    await transport.send(makePayload(64), 5000);
    const [url] = mockSendBeacon.mock.calls[0] as [string, Blob];
    expect(url).toBe(ENDPOINT);
  });

  it("shutdown() is a no-op (does not throw)", () => {
    const transport = createPulseSendBeaconTransport({
      url: ENDPOINT,
      apiKey: API_KEY,
      contentType: CONTENT_TYPE,
    });
    expect(() => transport.shutdown()).not.toThrow();
  });

  // ── beaconRelayUrl ────────────────────────────────────────────────────────

  it("uses beaconRelayUrl as the beacon URL — apiKey NOT in query param", async () => {
    const RELAY = "https://myapp.example.com/api/pulse-relay";
    const transport = createPulseSendBeaconTransport({
      url: ENDPOINT,
      apiKey: API_KEY,
      beaconRelayUrl: RELAY,
      contentType: CONTENT_TYPE,
    });
    await transport.send(makePayload(100), 5000);
    const [url] = mockSendBeacon.mock.calls[0] as [string, Blob];
    expect(url).toBe(RELAY);
    expect(url).not.toContain("apiKey");
    expect(url).not.toContain(ENDPOINT);
  });

  it("emits console.warn once when falling back to query-param (no relay URL)", async () => {
    _resetBeaconKeyWarnForTesting();
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});

    const transport = createPulseSendBeaconTransport({
      url: ENDPOINT,
      apiKey: API_KEY,
      contentType: CONTENT_TYPE,
    });

    await transport.send(makePayload(64), 5000);
    await transport.send(makePayload(64), 5000); // second send must NOT warn again

    expect(warnSpy).toHaveBeenCalledTimes(1);
    expect(warnSpy.mock.calls[0]?.[0]).toContain("beaconRelayUrl");
    warnSpy.mockRestore();
    _resetBeaconKeyWarnForTesting();
  });

  it("does NOT emit console.warn when beaconRelayUrl is provided", async () => {
    _resetBeaconKeyWarnForTesting();
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});

    const transport = createPulseSendBeaconTransport({
      url: ENDPOINT,
      apiKey: API_KEY,
      beaconRelayUrl: "https://myapp.example.com/api/pulse-relay",
      contentType: CONTENT_TYPE,
    });

    await transport.send(makePayload(64), 5000);

    expect(warnSpy).not.toHaveBeenCalled();
    warnSpy.mockRestore();
    _resetBeaconKeyWarnForTesting();
  });
});

// ─── Tests: buildBrowserExportTransport → switchToBeacon ────────────────────

describe("buildBrowserExportTransport.switchToBeacon", () => {
  let mockSendBeacon: ReturnType<typeof vi.fn>;
  let mockFetch: ReturnType<typeof vi.fn>;

  const smallPayload = makePayload(100);
  const largePayload = makePayload(BEACON_BODY_LIMIT_BYTES + 1);

  beforeEach(() => {
    mockSendBeacon = vi.fn().mockReturnValue(true);
    mockFetch = vi.fn().mockResolvedValue({ ok: true, status: 200 });
    vi.stubGlobal("navigator", { sendBeacon: mockSendBeacon });
    vi.stubGlobal("fetch", mockFetch);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  // ── Positive ──────────────────────────────────────────────────────────────

  it("small payload (≤ 64 KiB) routes to sendBeacon after switchToBeacon", async () => {
    const t = makeTransport();
    t.switchToBeacon({ apiKey: API_KEY, contentType: CONTENT_TYPE });
    await t.send(smallPayload, 5000);
    expect(mockSendBeacon).toHaveBeenCalledTimes(1);
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("large payload (> 64 KiB) routes to keepalive fetch after switchToBeacon", async () => {
    const t = makeTransport();
    t.switchToBeacon({ apiKey: API_KEY, contentType: CONTENT_TYPE });
    await t.send(largePayload, 5000);
    expect(mockSendBeacon).not.toHaveBeenCalled();
    expect(mockFetch).toHaveBeenCalledTimes(1);
    // Keepalive fetch must have keepalive: true
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(init.keepalive).toBe(true);
  });

  it("beacon failure for small payload falls back to keepalive fetch", async () => {
    mockSendBeacon.mockReturnValue(false); // beacon rejected
    const t = makeTransport();
    t.switchToBeacon({ apiKey: API_KEY, contentType: CONTENT_TYPE });
    const result = await t.send(smallPayload, 5000);
    expect(mockSendBeacon).toHaveBeenCalledTimes(1);
    expect(mockFetch).toHaveBeenCalledTimes(1);
    expect(result.status).toBe("success"); // keepalive fetch succeeded
  });

  it("sendBeacon unavailable falls back to keepalive fetch for small payload", async () => {
    vi.stubGlobal("navigator", {}); // no sendBeacon
    const t = makeTransport();
    t.switchToBeacon({ apiKey: API_KEY, contentType: CONTENT_TYPE });
    const result = await t.send(smallPayload, 5000);
    expect(mockFetch).toHaveBeenCalledTimes(1);
    expect(result.status).toBe("success");
  });

  it("apiKey appears in beacon URL query param", async () => {
    const t = makeTransport(ENDPOINT);
    t.switchToBeacon({ apiKey: API_KEY, contentType: CONTENT_TYPE });
    await t.send(smallPayload, 5000);
    const [url] = mockSendBeacon.mock.calls[0] as [string, Blob];
    expect(url).toContain(`apiKey=${encodeURIComponent(API_KEY)}`);
  });

  it("keepalive fetch uses original headers (including X-API-KEY)", async () => {
    const t = makeTransport(ENDPOINT);
    t.switchToBeacon({ apiKey: API_KEY, contentType: CONTENT_TYPE });
    await t.send(largePayload, 5000);
    const [, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    const headers = init.headers as Record<string, string>;
    expect(headers["X-API-KEY"]).toBe(API_KEY);
  });

  it("payload exactly at limit routes to beacon (boundary condition)", async () => {
    const atLimit = makePayload(BEACON_BODY_LIMIT_BYTES);
    const t = makeTransport();
    t.switchToBeacon({ apiKey: API_KEY, contentType: CONTENT_TYPE });
    await t.send(atLimit, 5000);
    expect(mockSendBeacon).toHaveBeenCalledTimes(1);
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("payload one byte over limit routes to keepalive (boundary condition)", async () => {
    const overLimit = makePayload(BEACON_BODY_LIMIT_BYTES + 1);
    const t = makeTransport();
    t.switchToBeacon({ apiKey: API_KEY, contentType: CONTENT_TYPE });
    await t.send(overLimit, 5000);
    expect(mockSendBeacon).not.toHaveBeenCalled();
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  // ── Negative ──────────────────────────────────────────────────────────────

  it("both beacon and keepalive fetch fail → propagates failure", async () => {
    mockSendBeacon.mockReturnValue(false);
    mockFetch.mockRejectedValue(new Error("network failure"));
    const t = makeTransport();
    t.switchToBeacon({ apiKey: API_KEY, contentType: CONTENT_TYPE });
    const result = await t.send(smallPayload, 5000);
    expect(result.status).toBe("failure");
  });

  it("before switchToBeacon, sends via XHR (not beacon)", async () => {
    const mockXhrOpen = vi.fn();
    const mockXhrSend = vi.fn(() => {
      // simulate readystatechange to DONE with 200
    });
    // We just verify sendBeacon is NOT called before switch
    const t = makeTransport();
    // Don't call switchToBeacon
    // The transport sends via XHR internally; sendBeacon must NOT be called
    // (We can't easily intercept XHR in vitest without more mocking, but we can
    // assert sendBeacon was never called)
    void t.send(smallPayload, 5000).catch(() => {}); // XHR will fail silently in tests
    expect(mockSendBeacon).not.toHaveBeenCalled();
  });

  it("switchToBeacon with no apiKey does not add query param to beacon URL", async () => {
    const t = makeTransport();
    t.switchToBeacon({ contentType: CONTENT_TYPE });
    await t.send(smallPayload, 5000);
    const [url] = mockSendBeacon.mock.calls[0] as [string, Blob];
    expect(url).toBe(ENDPOINT);
    expect(url).not.toContain("apiKey");
  });

  it("can call switchToBeacon multiple times — last call wins", async () => {
    const t = makeTransport();
    t.switchToBeacon({ apiKey: "first-key", contentType: CONTENT_TYPE });
    t.switchToBeacon({ apiKey: API_KEY, contentType: CONTENT_TYPE });
    await t.send(smallPayload, 5000);
    const [url] = mockSendBeacon.mock.calls[0] as [string, Blob];
    expect(url).toContain(`apiKey=${encodeURIComponent(API_KEY)}`);
  });

  it("BEACON_BODY_LIMIT_BYTES is exported and equals 65536 (64 KiB)", () => {
    expect(BEACON_BODY_LIMIT_BYTES).toBe(65536);
  });
});
