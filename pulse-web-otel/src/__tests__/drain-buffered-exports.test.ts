/**
 * Unit tests for drainBufferedOtlpExports — replay of IDB-buffered OTLP rows.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { drainBufferedOtlpExports } from "../persistence/drain-buffered-exports";
import type { IdbSignalBuffer } from "../persistence/indexed-db";
import type { BufferedSignalRow } from "../types/persistence";

const TRACES = "https://collector.example.com/v1/traces";
const LOGS = "https://collector.example.com/v1/logs";
const METRICS = "https://collector.example.com/v1/metrics";

function row(
  id: number,
  signalType: BufferedSignalRow["signalType"],
): BufferedSignalRow {
  return {
    id,
    signalType,
    envelope: {
      bodyB64: btoa("{}"),
      contentType: "application/json",
    },
    timestamp: Date.now(),
    retryCount: 0,
  };
}

describe("drainBufferedOtlpExports", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({ ok: true, status: 200 } as Response),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("POSTs each buffered row to the correct OTLP URL and deletes on 2xx", async () => {
    const rows = [row(1, "log"), row(2, "trace")];
    const deleteSpy = vi.fn().mockResolvedValue(undefined);
    const buffer = {
      readAll: vi.fn().mockResolvedValue(rows),
      delete: deleteSpy,
      write: vi.fn(),
      clear: vi.fn(),
    } as unknown as IdbSignalBuffer;

    await drainBufferedOtlpExports({
      tracesUrl: TRACES,
      logsUrl: LOGS,
      metricsUrl: METRICS,
      apiKey: "k",
      meteringSessionId: "m",
      buffer,
    });

    expect(fetch).toHaveBeenCalledTimes(2);
    const fetchMock = vi.mocked(fetch);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(LOGS);
    expect(fetchMock.mock.calls[1]?.[0]).toBe(TRACES);
    expect(deleteSpy).toHaveBeenCalledWith(1);
    expect(deleteSpy).toHaveBeenCalledWith(2);
  });

  it("no-op when buffer is empty", async () => {
    const buffer = {
      readAll: vi.fn().mockResolvedValue([]),
      delete: vi.fn(),
      write: vi.fn(),
      clear: vi.fn(),
    } as unknown as IdbSignalBuffer;

    await drainBufferedOtlpExports({
      tracesUrl: TRACES,
      logsUrl: LOGS,
      metricsUrl: METRICS,
      apiKey: "k",
      meteringSessionId: "m",
      buffer,
    });

    expect(fetch).not.toHaveBeenCalled();
  });
});
