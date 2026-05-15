/**
 * Unit tests for ecommerce-demo entry wiring in {@link ../Root.tsx}:
 * - PulseProvider receives config derived from env + URL (same goals as legacy App.test).
 * - PulseErrorBoundary + EcommerceErrorFallback when a child throws during render.
 */

import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

const mockStart = vi.fn();
const mockShutdown = vi.fn().mockResolvedValue(undefined);
const mockIsInitialized = vi.fn().mockReturnValue(false);

vi.mock("@dreamhorizonorg/pulse-web", () => ({
  Pulse: {
    init: mockStart,
    shutdown: mockShutdown,
    setScreenName: vi.fn(),
    setUserId: vi.fn(),
    setUserProperties: vi.fn(),
    trackEvent: vi.fn(),
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

vi.mock("@dreamhorizonorg/pulse-web/react", async () => {
  const React = await import("react");

  class PulseErrorBoundary extends React.Component<{
    children: React.ReactNode;
    fallback?:
      | React.ReactNode
      | ((e: Error, reset: () => void) => React.ReactNode);
  }> {
    state = { hasError: false, error: null as Error | null };
    reset = (): void => {
      this.setState({ hasError: false, error: null });
    };
    static getDerivedStateFromError(e: Error) {
      return { hasError: true, error: e };
    }
    render() {
      if (this.state.hasError && this.state.error) {
        const { fallback } = this.props;
        if (typeof fallback === "function") {
          return fallback(this.state.error, this.reset);
        }
        return fallback ?? null;
      }
      return this.props.children;
    }
  }

  function PulseProvider({
    children,
    config,
    shutdownOnUnmount: _shutdownOnUnmount,
    errorBoundaryFallback,
  }: {
    children: React.ReactNode;
    config: unknown;
    shutdownOnUnmount?: boolean;
    errorBoundaryFallback?: (e: Error, reset: () => void) => React.ReactNode;
  }) {
    React.useEffect(() => {
      mockStart(config);
      mockIsInitialized.mockReturnValue(true);
      return () => {
        void mockShutdown();
      };
    }, []);
    return React.createElement(PulseErrorBoundary, {
      fallback: errorBoundaryFallback,
      children,
    });
  }

  return {
    PulseProvider,
    PulseErrorBoundary,
    usePulse: vi.fn(),
    useRouterTracking: vi.fn(),
  };
});

const APP_TEST_KEY = "__ECOMMERCE_ROOT_APP_TEST_MODE__" as const;

vi.mock("../App", () => ({
  default: function MockAppForRootTest() {
    if (
      (globalThis as unknown as Record<string, string | undefined>)[
        APP_TEST_KEY
      ] === "crash"
    ) {
      throw new Error("test render crash");
    }
    return React.createElement("div", { "data-testid": "app-stub" }, "stub");
  },
}));

beforeEach(() => {
  (globalThis as unknown as Record<string, string | undefined>)[APP_TEST_KEY] =
    "stub";
  vi.stubEnv("VITE_PULSE_API_KEY", "vitest-default-pulse-key");
  window.history.replaceState({}, "", "/");
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
  mockIsInitialized.mockReturnValue(false);
});

async function renderRoot() {
  const { Root } = await import("../Root");
  let result: ReturnType<typeof render> | undefined;
  await act(async () => {
    result = render(
      React.createElement(MemoryRouter, null, React.createElement(Root)),
    );
    await new Promise((r) => setTimeout(r, 50));
  });
  return result!;
}

describe("Demo Root — PulseProvider wiring", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockIsInitialized.mockReturnValue(false);
    vi.resetModules();
  });

  it("calls Pulse.init() exactly once on mount via PulseProvider", async () => {
    await renderRoot();
    expect(mockStart).toHaveBeenCalledTimes(1);
  });

  it("passes apiKey from VITE_PULSE_API_KEY env to PulseProvider config", async () => {
    vi.stubEnv("VITE_PULSE_API_KEY", "test-project_devkey123");
    await renderRoot();
    expect(mockStart).toHaveBeenCalledWith(
      expect.objectContaining({ apiKey: "test-project_devkey123" }),
    );
  });

  it("passes dataCollectionState=ALLOWED by default", async () => {
    await renderRoot();
    expect(mockStart).toHaveBeenCalledWith(
      expect.objectContaining({ dataCollectionState: "ALLOWED" }),
    );
  });

  it("passes dataCollectionState=DENIED when pulse_consent=denied", async () => {
    window.history.replaceState({}, "", "/?pulse_consent=denied");
    await renderRoot();
    expect(mockStart).toHaveBeenCalledWith(
      expect.objectContaining({ dataCollectionState: "DENIED" }),
    );
  });

  it("passes network instrumentation off when pulse_network_enabled=0", async () => {
    window.history.replaceState({}, "", "/?pulse_network_enabled=0");
    await renderRoot();
    expect(mockStart).toHaveBeenCalledWith(
      expect.objectContaining({
        instrumentations: { network: { enabled: false } },
      }),
    );
  });

  it("passes captureQueryParams when pulse_capture_query=1", async () => {
    window.history.replaceState({}, "", "/?pulse_capture_query=1");
    await renderRoot();
    expect(mockStart).toHaveBeenCalledWith(
      expect.objectContaining({
        instrumentations: {
          network: { enabled: true, captureQueryParams: true },
        },
      }),
    );
  });

  it("passes capturedRequestHeaders when pulse_capture_req_headers is set", async () => {
    window.history.replaceState(
      {},
      "",
      "/?pulse_capture_req_headers=x-request-id,x-custom-header",
    );
    await renderRoot();
    expect(mockStart).toHaveBeenCalledWith(
      expect.objectContaining({
        instrumentations: {
          network: {
            enabled: true,
            capturedRequestHeaders: ["x-request-id", "x-custom-header"],
          },
        },
      }),
    );
  });
});

describe("Demo Root — PulseErrorBoundary wiring", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.resetModules();
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  it("shows EcommerceErrorFallback when App throws on render", async () => {
    (globalThis as unknown as Record<string, string | undefined>)[
      APP_TEST_KEY
    ] = "crash";
    await renderRoot();
    expect(
      screen.getByRole("heading", { name: /render error caught/i }),
    ).toBeTruthy();
    expect(screen.getByText("test render crash")).toBeTruthy();
  });
});

describe("Demo Root — config derivation", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.resetModules();
  });

  it("uses my-app as default serviceName when VITE_PULSE_SERVICE_NAME unset", async () => {
    vi.stubEnv("VITE_PULSE_SERVICE_NAME", "");
    await renderRoot();
    expect(mockStart).toHaveBeenCalledWith(
      expect.objectContaining({ serviceName: "my-app" }),
    );
  });

  it("uses VITE_PULSE_SERVICE_NAME when set", async () => {
    vi.stubEnv("VITE_PULSE_SERVICE_NAME", "ecommerce-demo");
    await renderRoot();
    expect(mockStart).toHaveBeenCalledWith(
      expect.objectContaining({ serviceName: "ecommerce-demo" }),
    );
  });

  it("includes export config with default protobuf format", async () => {
    vi.stubEnv("VITE_PULSE_FORMAT", "protobuf");
    await renderRoot();
    expect(mockStart).toHaveBeenCalledWith(
      expect.objectContaining({
        export: expect.objectContaining({ format: "protobuf" }),
      }),
    );
  });

  it("uses json export format when VITE_PULSE_FORMAT=json", async () => {
    vi.stubEnv("VITE_PULSE_FORMAT", "json");
    await renderRoot();
    expect(mockStart).toHaveBeenCalledWith(
      expect.objectContaining({
        export: expect.objectContaining({ format: "json" }),
      }),
    );
  });
});
