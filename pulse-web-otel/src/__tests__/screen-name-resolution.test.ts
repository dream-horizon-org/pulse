/**
 * Unit tests for resolveScreenName() logic inside PulseGlobalAttributesProcessor.
 * Tests all branches of the heuristic screen name resolution:
 *
 * Positive cases:
 *   - manual override returned directly
 *   - route pattern match returns mapped name
 *   - UUID segments replaced with :id in pathname
 *   - pure-number segments replaced with :id in pathname
 *   - multiple dynamic segments replaced independently, static parts kept
 *   - single UUID-only path normalised to /:id
 *   - all-numeric path normalised to /:id/:id/:id
 *
 * Negative cases:
 *   - invalid regex pattern skipped (no crash)
 *   - non-matching route patterns fall through to heuristic
 *   - manual override cleared when URL changes (SPA nav)
 *   - SSR (window undefined) returns ""
 *
 * Network attr fallback:
 *   - no crash when navigator.connection is undefined
 */

import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { PulseGlobalAttributesProcessor } from "../processors/global-attrs-processor";
import { SessionProvider } from "../session";
import type { PulseWebConfig } from "../config";
import { _resetInstallationStateForTesting } from "../session";
import { PulseWebSemconv } from "../semconv";

function makeProcessor(
  config: Partial<PulseWebConfig> = {},
): PulseGlobalAttributesProcessor {
  const session = {
    getSessionId: vi.fn().mockReturnValue("s1"),
    getWindowId: vi.fn().mockReturnValue("w1"),
    updateActivity: vi.fn(),
  } as unknown as SessionProvider;

  return new PulseGlobalAttributesProcessor(
    session,
    { apiKey: "test-key", ...config } as PulseWebConfig,
    "",
  );
}

function setPath(path: string) {
  Object.defineProperty(window, "location", {
    value: {
      ...window.location,
      pathname: path,
      href: `http://localhost${path}`,
    },
    configurable: true,
    writable: true,
  });
}

describe("screen name — manual override", () => {
  beforeEach(() => {
    _resetInstallationStateForTesting();
    window.localStorage.clear();
    setPath("/home");
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("returns manual name when set", () => {
    const proc = makeProcessor();
    proc.setScreenName("Dashboard");
    expect(proc.getCurrentScreenName()).toBe("Dashboard");
  });

  it("clears manual override when URL changes (SPA navigation)", () => {
    const proc = makeProcessor();
    setPath("/home");
    proc.setScreenName("Home Screen");
    expect(proc.getCurrentScreenName()).toBe("Home Screen");

    // Simulate navigation
    setPath("/cart");
    // Now getCurrentScreenName should fall back to heuristic
    const name = proc.getCurrentScreenName();
    expect(name).not.toBe("Home Screen");
    expect(name).toBe("/cart");
  });

  it("keeps manual name when URL has NOT changed", () => {
    setPath("/checkout");
    const proc = makeProcessor();
    proc.setScreenName("Checkout Page");
    // URL unchanged
    expect(proc.getCurrentScreenName()).toBe("Checkout Page");
  });
});

describe("screen name — route pattern matching", () => {
  beforeEach(() => {
    _resetInstallationStateForTesting();
    window.localStorage.clear();
  });

  afterEach(() => vi.restoreAllMocks());

  it("returns matched route name when pattern matches pathname", () => {
    setPath("/products/123");
    const proc = makeProcessor({
      routePatterns: [{ pattern: "^/products/", name: "Product Detail" }],
    });
    expect(proc.getCurrentScreenName()).toBe("Product Detail");
  });

  it("tries patterns in order, returns first match", () => {
    setPath("/orders/456/details");
    const proc = makeProcessor({
      routePatterns: [
        { pattern: "^/orders/\\d+/details$", name: "Order Details" },
        { pattern: "^/orders/", name: "Orders" },
      ],
    });
    expect(proc.getCurrentScreenName()).toBe("Order Details");
  });

  it("falls through to heuristic when no pattern matches", () => {
    setPath("/settings");
    const proc = makeProcessor({
      routePatterns: [{ pattern: "^/dashboard", name: "Dashboard" }],
    });
    // /settings doesn't match ^/dashboard → heuristic returns /settings
    expect(proc.getCurrentScreenName()).toBe("/settings");
  });

  it("skips invalid regex patterns without crashing", () => {
    setPath("/profile");
    const proc = makeProcessor({
      routePatterns: [
        { pattern: "[invalid(regex", name: "Bad Pattern" },
        { pattern: "^/profile$", name: "Profile" },
      ],
    });
    expect(proc.getCurrentScreenName()).toBe("Profile");
  });
});

describe("screen name — UUID and numeric segment normalisation", () => {
  beforeEach(() => {
    _resetInstallationStateForTesting();
    window.localStorage.clear();
  });

  afterEach(() => vi.restoreAllMocks());

  it("replaces UUID segment with :id", () => {
    setPath("/users/550e8400-e29b-41d4-a716-446655440000/profile");
    const proc = makeProcessor();
    expect(proc.getCurrentScreenName()).toBe("/users/:id/profile");
  });

  it("replaces pure-number segment with :id", () => {
    setPath("/orders/12345/items");
    const proc = makeProcessor();
    expect(proc.getCurrentScreenName()).toBe("/orders/:id/items");
  });

  it("replaces multiple dynamic segments independently", () => {
    setPath(
      "/org/99/team/abc-123-def/member/550e8400-e29b-41d4-a716-446655440000",
    );
    const proc = makeProcessor();
    // 99 → :id, abc-123-def kept (slug — not pure-number, not UUID), UUID → :id
    expect(proc.getCurrentScreenName()).toBe(
      "/org/:id/team/abc-123-def/member/:id",
    );
  });

  it("single UUID-only path becomes /:id", () => {
    setPath("/550e8400-e29b-41d4-a716-446655440000");
    const proc = makeProcessor();
    expect(proc.getCurrentScreenName()).toBe("/:id");
  });

  it("all-numeric path normalised to /:id/:id/:id", () => {
    setPath("/123/456/789");
    const proc = makeProcessor();
    expect(proc.getCurrentScreenName()).toBe("/:id/:id/:id");
  });

  it("returns '/' for root path", () => {
    setPath("/");
    const proc = makeProcessor();
    expect(proc.getCurrentScreenName()).toBe("/");
  });

  it("keeps static alpha segments", () => {
    setPath("/dashboard/analytics");
    const proc = makeProcessor();
    expect(proc.getCurrentScreenName()).toBe("/dashboard/analytics");
  });

  it("preserves slugs with hyphens (non-UUID, non-numeric)", () => {
    setPath("/blog/my-great-article");
    const proc = makeProcessor();
    expect(proc.getCurrentScreenName()).toBe("/blog/my-great-article");
  });
});

describe("screen name — network attrs fallback", () => {
  beforeEach(() => {
    _resetInstallationStateForTesting();
    window.localStorage.clear();
    setPath("/");
  });

  afterEach(() => vi.restoreAllMocks());

  it("does not crash when navigator.connection is undefined", () => {
    const nav = navigator as Navigator & { connection?: unknown };
    const original = nav.connection;
    Object.defineProperty(navigator, "connection", {
      value: undefined,
      configurable: true,
    });

    const proc = makeProcessor();
    const attrs = proc.getCommonAttrsForMetrics();

    // Should still have network attrs with fallback values
    expect(attrs["network.connection.type"]).toBe("unknown");
    expect(attrs["network.effective_type"]).toBe("unknown");

    Object.defineProperty(navigator, "connection", {
      value: original,
      configurable: true,
    });
  });

  it("includes network.rtt when connection.rtt is a number", () => {
    Object.defineProperty(navigator, "connection", {
      value: { type: "4g", effectiveType: "4g", rtt: 50, downlink: 10 },
      configurable: true,
    });

    const proc = makeProcessor();
    const attrs = proc.getCommonAttrsForMetrics();

    expect(attrs["network.rtt"]).toBe(50);
    expect(attrs["network.downlink"]).toBe(10);
  });

  it("omits network.rtt when undefined", () => {
    Object.defineProperty(navigator, "connection", {
      value: { type: "wifi", effectiveType: "4g" },
      configurable: true,
    });

    const proc = makeProcessor();
    const attrs = proc.getCommonAttrsForMetrics();

    expect("network.rtt" in attrs).toBe(false);
    expect("network.downlink" in attrs).toBe(false);
  });
});

describe("last.screen.name", () => {
  beforeEach(() => {
    _resetInstallationStateForTesting();
    window.localStorage.clear();
  });

  afterEach(() => vi.restoreAllMocks());

  it("stamps last.screen.name with previous resolved screen after pathname changes", () => {
    setPath("/home");
    const proc = makeProcessor();
    proc.getCommonAttrsForMetrics();

    setPath("/products");
    const attrs = proc.getCommonAttrsForMetrics();

    expect(attrs[PulseWebSemconv.AttributeKey.LAST_SCREEN_NAME]).toBe("/home");
    expect(attrs["screen.name"]).toBe("/products");
  });
});
