/**
 * SSR / non-browser guard for ClicksInstrumentation.install (parity with errors gate file).
 */
import { afterEach, describe, expect, it, vi } from "vitest";
import { ClicksInstrumentation } from "../instrumentations/clicks";
import type { SdkContext } from "../instrumentation-registry";

describe("ClicksInstrumentation — SSR / no window", () => {
  const mockSdk = {
    logger: { emit: vi.fn() },
    loggerProvider: { forceFlush: vi.fn().mockResolvedValue(undefined) },
    tracer: {},
    config: { instrumentations: {} },
    sessionProvider: {},
    globalAttrsProcessor: {},
  } as unknown as SdkContext;

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("install is a no-op when window is undefined", () => {
    vi.stubGlobal("window", undefined);
    try {
      const instr = new ClicksInstrumentation();
      expect(() => instr.install(mockSdk)).not.toThrow();
      expect(() => instr.uninstall()).not.toThrow();
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
