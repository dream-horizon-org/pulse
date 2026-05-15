import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { PulseGlobalAttributesProcessor } from "../processors/global-attrs-processor";
import { SessionProvider } from "../session";
import type { PulseWebConfig } from "../config";
import { PulseWebSemconv } from "../semconv";
import { _resetInstallationStateForTesting } from "../session";

function makeProcessor(): PulseGlobalAttributesProcessor {
  const session = {
    getSessionId: vi.fn().mockReturnValue("s1"),
    getWindowId: vi.fn().mockReturnValue("w1"),
    updateActivity: vi.fn(),
  } as unknown as SessionProvider;

  return new PulseGlobalAttributesProcessor(
    session,
    { apiKey: "test-key" } as PulseWebConfig,
    "",
  );
}

describe("PulseGlobalAttributesProcessor — navigation_id", () => {
  beforeEach(() => {
    _resetInstallationStateForTesting();
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("omits navigation_id from getCommonAttrsForMetrics when unset", () => {
    const proc = makeProcessor();
    const attrs = proc.getCommonAttrsForMetrics();
    expect(attrs[PulseWebSemconv.AttributeKey.NAVIGATION_ID]).toBeUndefined();
  });

  it("includes navigation_id after setNavigationId", () => {
    const proc = makeProcessor();
    proc.setNavigationId("aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee");
    const attrs = proc.getCommonAttrsForMetrics();
    expect(attrs[PulseWebSemconv.AttributeKey.NAVIGATION_ID]).toBe(
      "aaaaaaaa-bbbb-4ccc-dddd-eeeeeeeeeeee",
    );
  });
});
