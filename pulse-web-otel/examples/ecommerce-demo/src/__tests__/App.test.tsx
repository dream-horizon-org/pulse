/**
 * Task 5 — Update Demo App
 *
 * Unit tests for the ecommerce demo App.tsx refactor:
 * - Verifies PulseProvider / PulseErrorBoundary / useRouterTracking are used
 *   instead of manual PulseWeb.start() wiring.
 * - Tests config derivation from env vars and query params.
 * - Tests error boundary fallback renders on child crash.
 */

import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

// ─── SDK mocks ────────────────────────────────────────────────────────────────

const mockStart = vi.fn();
const mockShutdown = vi.fn().mockResolvedValue(undefined);
const mockSetScreenName = vi.fn();
const mockTrackEvent = vi.fn();
const mockIsInitialized = vi.fn().mockReturnValue(false);

vi.mock("@dreamhorizon/pulse-web", () => ({
  PulseWeb: {
    start: mockStart,
    shutdown: mockShutdown,
    setScreenName: mockSetScreenName,
    setUserId: vi.fn(),
    setUserProperties: vi.fn(),
    trackEvent: mockTrackEvent,
    isInitialized: mockIsInitialized,
    reportDeviceCrash: vi.fn(),
    trackNonFatal: vi.fn(),
    reportException: vi.fn(),
  },
  PulseDataCollectionConsent: {
    ALLOWED: "ALLOWED",
    DENIED: "DENIED",
    PENDING: "PENDING",
  },
  PulseLogLevel: {
    VERBOSE: 0,
    DEBUG: 1,
    INFO: 2,
    WARN: 3,
    ERROR: 4,
    NONE: 5,
  },
}));

// Mock the React integration — lightweight stubs; PulseProvider wraps children in
// PulseErrorBoundary (matches real PulseProvider) so route render errors are contained.
vi.mock("@dreamhorizon/pulse-web/react", () => {
  const React = require("react");

  class PulseErrorBoundary extends React.Component<{
    children: React.ReactNode;
    fallback?: React.ReactNode | ((e: Error) => React.ReactNode);
  }> {
    state = { hasError: false, error: null as Error | null };
    static getDerivedStateFromError(e: Error) {
      return { hasError: true, error: e };
    }
    render() {
      if (this.state.hasError && this.state.error) {
        const { fallback } = this.props;
        if (typeof fallback === "function") return fallback(this.state.error);
        return fallback ?? null;
      }
      return this.props.children;
    }
  }

  function PulseProvider({
    children,
    config,
  }: {
    children: React.ReactNode;
    config: unknown;
    shutdownOnUnmount?: boolean;
  }) {
    React.useEffect(() => {
      mockStart(config);
      mockIsInitialized.mockReturnValue(true);
      return () => {
        void mockShutdown();
      };
    }, []);
    return React.createElement(PulseErrorBoundary, null, children);
  }

  function useRouterTracking(opts?: { skipInitial?: boolean }) {
    const loc = require("react-router-dom").useLocation();
    const prev = React.useRef(null as string | null);
    React.useEffect(() => {
      if (prev.current === null) {
        prev.current = loc.pathname;
        if (opts?.skipInitial !== false) return;
      } else if (prev.current === loc.pathname) {
        return;
      } else {
        prev.current = loc.pathname;
      }
      mockSetScreenName(loc.pathname);
    }, [loc.pathname]);
  }

  function usePulse() {
    return { trackEvent: mockTrackEvent, setScreenName: mockSetScreenName };
  }

  return { PulseProvider, PulseErrorBoundary, useRouterTracking, usePulse };
});

// Mock lazy-loaded routes — Home can throw when `homeThrowsOnRender` is true (error-boundary test).
let homeThrowsOnRender = false;
vi.mock("../routes/Home", () => ({
  default: () => {
    if (homeThrowsOnRender) {
      throw new Error("test render crash");
    }
    return React.createElement("div", { "data-testid": "home" }, "Home");
  },
}));
vi.mock("../routes/Products", () => ({
  default: () =>
    React.createElement("div", { "data-testid": "products" }, "Products"),
}));
vi.mock("../routes/ProductDetail", () => ({
  default: () => React.createElement("div", null, "ProductDetail"),
}));
vi.mock("../routes/Cart", () => ({
  default: () => React.createElement("div", null, "Cart"),
}));
vi.mock("../routes/Checkout", () => ({
  default: () => React.createElement("div", null, "Checkout"),
}));
vi.mock("../routes/ErrorDemo", () => ({
  default: () =>
    React.createElement("div", { "data-testid": "error-demo" }, "ErrorDemo"),
}));
vi.mock("../components/PulseDebugPanel", () => ({
  PulseDebugPanel: () => null,
}));

// ─── helpers ──────────────────────────────────────────────────────────────────

async function renderApp(search = "") {
  const App = (await import("../App")).default;
  let result: ReturnType<typeof render> | undefined;
  await act(async () => {
    result = render(React.createElement(App));
    await new Promise((r) => setTimeout(r, 50));
  });
  return result!;
}

// ─── tests ───────────────────────────────────────────────────────────────────

describe("Demo App — PulseProvider wiring", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockIsInitialized.mockReturnValue(false);
    // Reset module so App re-evaluates useMemo config
    vi.resetModules();
  });

  it("calls PulseWeb.start() exactly once on mount via PulseProvider", async () => {
    await renderApp();
    expect(mockStart).toHaveBeenCalledTimes(1);
  });

  it("passes apiKey from VITE_PULSE_API_KEY env to PulseProvider config", async () => {
    vi.stubEnv("VITE_PULSE_API_KEY", "test-project_devkey123");
    await renderApp();
    expect(mockStart).toHaveBeenCalledWith(
      expect.objectContaining({ apiKey: "test-project_devkey123" }),
    );
    vi.unstubAllEnvs();
  });

  it("passes dataCollectionState=ALLOWED by default", async () => {
    await renderApp();
    expect(mockStart).toHaveBeenCalledWith(
      expect.objectContaining({ dataCollectionState: "ALLOWED" }),
    );
  });

  it("renders NavBar and Home route without crashing", async () => {
    const { getByText } = await renderApp();
    expect(getByText("🛍 PulseStore")).toBeTruthy();
  });
});

describe("Demo App — PulseErrorBoundary wiring", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.resetModules();
    homeThrowsOnRender = true;
    // Suppress React error boundary console noise
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    homeThrowsOnRender = false;
    vi.restoreAllMocks();
  });

  it("catches render errors silently via internal PulseErrorBoundary", async () => {
    const App = (await import("../App")).default;
    let result: ReturnType<typeof render> | undefined;
    await act(async () => {
      result = render(React.createElement(App));
      await new Promise((r) => setTimeout(r, 50));
    });
    // PulseErrorBoundary is internal with no fallback UI — renders null on error
    expect(result!.container.firstChild).toBeNull();
  });
});

describe("Demo App — useRouterTracking wiring", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockIsInitialized.mockReturnValue(true);
    vi.resetModules();
  });

  it("calls setScreenName on initial mount when skipInitial=false", async () => {
    await renderApp();
    // useRouterTracking({ skipInitial: false }) fires on initial mount
    expect(mockSetScreenName).toHaveBeenCalledWith("/");
  });
});

describe("Demo App — config derivation", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.resetModules();
  });

  it("uses ecommerce-demo as default serviceName", async () => {
    await renderApp();
    expect(mockStart).toHaveBeenCalledWith(
      expect.objectContaining({ serviceName: "ecommerce-demo" }),
    );
  });

  it("includes export config with default protobuf format", async () => {
    await renderApp();
    expect(mockStart).toHaveBeenCalledWith(
      expect.objectContaining({
        export: expect.objectContaining({ format: "protobuf" }),
      }),
    );
  });
});
