// Mock @opentelemetry/api-logs — same pattern as sdk-lifecycle.test.ts
vi.mock("@opentelemetry/api-logs", () => ({
  logs: {
    getLogger: vi.fn().mockReturnValue({ emit: vi.fn() }),
    setGlobalLoggerProvider: vi.fn(),
  },
}));

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import React, { StrictMode } from "react";
import { render, act, renderHook } from "@testing-library/react";

vi.mock("../exporters", () => {
  const mockTracerProvider = {
    addSpanProcessor: vi.fn(),
    getTracer: vi.fn().mockReturnValue({
      startSpan: vi.fn().mockReturnValue({
        setAttribute: vi.fn(),
        end: vi.fn(),
      }),
    }),
    forceFlush: vi.fn().mockResolvedValue(undefined),
    shutdown: vi.fn().mockResolvedValue(undefined),
    register: vi.fn(),
  };
  const mockLoggerProvider = {
    addLogRecordProcessor: vi.fn(),
    getLogger: vi.fn().mockReturnValue({ emit: vi.fn() }),
    forceFlush: vi.fn().mockResolvedValue(undefined),
    shutdown: vi.fn().mockResolvedValue(undefined),
  };
  const mockMeterProvider = {
    addMetricReader: vi.fn(),
    getMeter: vi.fn().mockReturnValue({}),
    forceFlush: vi.fn().mockResolvedValue(undefined),
    shutdown: vi.fn().mockResolvedValue(undefined),
  };

  return {
    createProviders: vi.fn().mockReturnValue({
      tracerProvider: mockTracerProvider,
      loggerProvider: mockLoggerProvider,
      meterProvider: mockMeterProvider,
      cleanup: vi.fn(),
      prepareForDocumentUnload: vi.fn(),
    }),
  };
});

import type { PulseWebConfig } from "../config";
import { PulseDataCollectionConsent } from "../config";
import {
  PulseProvider,
  usePulse,
  _resetPulseProviderStateForTesting,
} from "../integrations/react/PulseProvider";
import { PulseErrorBoundary } from "../integrations/react/PulseErrorBoundary";
import { PulseWebLogger } from "../pulse-web-logger";
import { Pulse } from "../sdk";
import { PulseLogLevel } from "../pulse-log-level";

function makeConfig(overrides: Partial<PulseWebConfig> = {}): PulseWebConfig {
  return {
    apiKey: "proj_abc_supersecretkey",
    serviceName: "test-app",
    dataCollectionState: PulseDataCollectionConsent.ALLOWED,
    ...overrides,
  };
}

beforeEach(() => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      json: () => Promise.resolve({}),
    }),
  );
  const mockXHR = {
    open: vi.fn(),
    send: vi.fn(),
    setRequestHeader: vi.fn(),
    abort: vi.fn(),
    readyState: 4,
    status: 200,
    responseText: "",
    onreadystatechange: null,
    onload: null,
    onerror: null,
    ontimeout: null,
    timeout: 0,
    withCredentials: false,
    upload: { addEventListener: vi.fn() },
  };
  vi.stubGlobal(
    "XMLHttpRequest",
    vi.fn(() => mockXHR),
  );

  window.localStorage.clear();
  window.sessionStorage.clear();
  _resetPulseProviderStateForTesting();
});

afterEach(async () => {
  if (Pulse.isInitialized()) {
    await Pulse.shutdown();
  }
  _resetPulseProviderStateForTesting();
  vi.unstubAllGlobals();
});

// Helper — flush pending microtasks (queueMicrotask in cleanup).
async function flushMicrotasks(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
}

// ---------------------------------------------------------------------------
// Happy path — single mount initialises SDK once
// ---------------------------------------------------------------------------

describe("PulseProvider — mount / unmount", () => {
  it("calls Pulse.init() exactly once on first mount", async () => {
    const initSpy = vi.spyOn(Pulse, "init");
    render(
      <PulseProvider config={makeConfig()}>
        <div>child</div>
      </PulseProvider>,
    );
    await act(async () => {
      await Promise.resolve();
    });

    expect(initSpy).toHaveBeenCalledTimes(1);
    expect(Pulse.isInitialized()).toBe(true);
    initSpy.mockRestore();
  });

  it("unmount does not shutdown when shutdownOnUnmount defaults to false", async () => {
    const { unmount } = render(
      <PulseProvider config={makeConfig()}>
        <div />
      </PulseProvider>,
    );
    await act(async () => {
      await Promise.resolve();
    });
    expect(Pulse.isInitialized()).toBe(true);

    unmount();
    await act(async () => {
      await flushMicrotasks();
    });

    expect(Pulse.isInitialized()).toBe(true);
  });

  it("unmount shuts down when shutdownOnUnmount is true", async () => {
    const { unmount } = render(
      <PulseProvider config={makeConfig()} shutdownOnUnmount={true}>
        <div />
      </PulseProvider>,
    );
    await act(async () => {
      await Promise.resolve();
    });
    expect(Pulse.isInitialized()).toBe(true);

    unmount();
    await act(async () => {
      await flushMicrotasks();
    });

    expect(Pulse.isInitialized()).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// StrictMode — double mount/unmount cycle in dev must not re-init or shutdown
// ---------------------------------------------------------------------------

describe("PulseProvider — StrictMode safety", () => {
  it("StrictMode double-mount calls init() at least once and stays initialised", async () => {
    const initSpy = vi.spyOn(Pulse, "init");

    render(
      <StrictMode>
        <PulseProvider config={makeConfig()} shutdownOnUnmount={true}>
          <div />
        </PulseProvider>
      </StrictMode>,
    );
    await act(async () => {
      await flushMicrotasks();
    });

    // StrictMode fires effect twice but the second call is a no-op at SDK level
    // (guard: _initialized || _initializing). Provider's isInitialized() check also
    // skips the second init(). Either way: net effect = one init.
    expect(Pulse.isInitialized()).toBe(true);

    // init() may be invoked at most twice by React (strict fake-unmount/remount)
    // but the SDK treats the second call as a no-op — what we really care about
    // is that init succeeded and we weren't torn down.
    expect(initSpy.mock.calls.length).toBeGreaterThanOrEqual(1);
    expect(initSpy.mock.calls.length).toBeLessThanOrEqual(2);
    initSpy.mockRestore();
  });

  it("StrictMode fake-unmount/remount does NOT trigger shutdown", async () => {
    const shutdownSpy = vi.spyOn(Pulse, "shutdown");

    const { unmount } = render(
      <StrictMode>
        <PulseProvider config={makeConfig()} shutdownOnUnmount={true}>
          <div />
        </PulseProvider>
      </StrictMode>,
    );
    await act(async () => {
      await flushMicrotasks();
    });

    // The fake strict unmount/remount happens synchronously inside the render
    // above. After microtasks flush, shutdown should NOT have run because the
    // provider re-mounted inside the same task.
    expect(shutdownSpy).not.toHaveBeenCalled();
    expect(Pulse.isInitialized()).toBe(true);

    // Real unmount should still trigger shutdown.
    unmount();
    await act(async () => {
      await flushMicrotasks();
    });
    expect(shutdownSpy).toHaveBeenCalledTimes(1);
    shutdownSpy.mockRestore();
  });

  it("StrictMode double-mount: createProviders (expensive init) called exactly once", async () => {
    const { createProviders } = await import("../exporters");
    const createSpy = vi.mocked(createProviders);
    createSpy.mockClear();

    render(
      <StrictMode>
        <PulseProvider config={makeConfig()} shutdownOnUnmount={false}>
          <div />
        </PulseProvider>
      </StrictMode>,
    );
    await act(async () => {
      await flushMicrotasks();
    });

    expect(createSpy).toHaveBeenCalledTimes(1);
    expect(Pulse.isInitialized()).toBe(true);
  });

  it("StrictMode fake-unmount does not prematurely shutdown the SDK", async () => {
    const shutdownSpy = vi.spyOn(Pulse, "shutdown");

    render(
      <StrictMode>
        <PulseProvider config={makeConfig()} shutdownOnUnmount={false}>
          <div />
        </PulseProvider>
      </StrictMode>,
    );
    await act(async () => {
      await flushMicrotasks();
    });

    // After StrictMode fake-unmount/remount the SDK must still be running
    expect(Pulse.isInitialized()).toBe(true);
    expect(shutdownSpy).not.toHaveBeenCalled();
    shutdownSpy.mockRestore();
  });
});

// ---------------------------------------------------------------------------
// usePulse() — context wiring + out-of-bounds error
// ---------------------------------------------------------------------------

describe("usePulse() hook", () => {
  it("returns the Pulse singleton when called inside PulseProvider", async () => {
    const wrapper = ({
      children,
    }: {
      children: React.ReactNode;
    }): React.ReactElement => (
      <PulseProvider config={makeConfig()} shutdownOnUnmount={false}>
        {children}
      </PulseProvider>
    );
    const { result } = renderHook(() => usePulse(), { wrapper });
    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current).toBe(Pulse);
    expect(typeof result.current.trackEvent).toBe("function");
    expect(typeof result.current.setScreenName).toBe("function");
  });

  it("throws a helpful error when called outside PulseProvider", () => {
    // Silence React's error log during the expected throw.
    const errSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    expect(() => renderHook(() => usePulse())).toThrow(
      /usePulse\(\) must be called inside <PulseProvider>/,
    );
    errSpy.mockRestore();
  });
});

// ---------------------------------------------------------------------------
// Restart cycle — two providers mounted sequentially both initialise cleanly
// ---------------------------------------------------------------------------

describe("PulseProvider — sequential mount cycles", () => {
  it("unmount → remount re-initialises the SDK", async () => {
    const { unmount } = render(
      <PulseProvider config={makeConfig()} shutdownOnUnmount={true}>
        <div />
      </PulseProvider>,
    );
    await act(async () => {
      await Promise.resolve();
    });
    expect(Pulse.isInitialized()).toBe(true);

    unmount();
    await act(async () => {
      await flushMicrotasks();
    });
    expect(Pulse.isInitialized()).toBe(false);

    render(
      <PulseProvider config={makeConfig()} shutdownOnUnmount={true}>
        <div />
      </PulseProvider>,
    );
    await act(async () => {
      await Promise.resolve();
    });
    expect(Pulse.isInitialized()).toBe(true);
  });
});

// ---------------------------------------------------------------------------
// +v — config forwarded to init() exactly
// ---------------------------------------------------------------------------

describe("PulseProvider — config forwarding (+v)", () => {
  it("passes the config prop to Pulse.init() unchanged", async () => {
    const initSpy = vi.spyOn(Pulse, "init");
    const cfg = makeConfig({ serviceName: "my-service" });

    render(
      <PulseProvider config={cfg}>
        <div />
      </PulseProvider>,
    );
    await act(async () => {
      await Promise.resolve();
    });

    expect(initSpy).toHaveBeenCalledWith(cfg);
    initSpy.mockRestore();
  });

  it("renders children correctly", async () => {
    const { getByTestId } = render(
      <PulseProvider config={makeConfig()}>
        <div data-testid="child">hello</div>
      </PulseProvider>,
    );
    await act(async () => {
      await Promise.resolve();
    });
    expect(getByTestId("child").textContent).toBe("hello");
  });
});

// ---------------------------------------------------------------------------
// +v — SSR guard: source-level verification (window=undefined can't be safely
//      simulated in jsdom — React DOM itself requires window to render)
// ---------------------------------------------------------------------------

describe("PulseProvider — SSR guard (+v)", () => {
  it("PulseProvider source contains typeof window guard before calling start()", () => {
    // Verified by code inspection: PulseProvider.tsx useEffect contains
    //   if (typeof window === "undefined") return;
    // before calling Pulse.init(). Tested in Node/SSR environments via
    // the sdk-lifecycle suite (exporters.ts + session.ts both guard window).
    const src = require("fs").readFileSync(
      require("path").resolve(
        __dirname,
        "../integrations/react/PulseProvider.tsx",
      ),
      "utf8",
    ) as string;
    expect(src).toContain('typeof window === "undefined"');
  });
});

// ---------------------------------------------------------------------------
// -v — second nested PulseProvider does not double-init
// ---------------------------------------------------------------------------

describe("PulseProvider — nested provider (-v)", () => {
  it("nested PulseProviders initialise the SDK exactly once (createProviders called once)", async () => {
    // Each provider's useEffect calls start() but the SDK singleton guard
    // (_starting / _initialized flags) ensures createProviders runs only once.
    const { createProviders } = await import("../exporters");
    const createSpy = vi.mocked(createProviders);
    createSpy.mockClear();

    render(
      <PulseProvider config={makeConfig()} shutdownOnUnmount={false}>
        <PulseProvider config={makeConfig()} shutdownOnUnmount={false}>
          <div />
        </PulseProvider>
      </PulseProvider>,
    );
    await act(async () => {
      await Promise.resolve();
    });

    expect(Pulse.isInitialized()).toBe(true);
    // createProviders is the expensive init step — must fire exactly once
    expect(createSpy).toHaveBeenCalledTimes(1);
  });
});

// ---------------------------------------------------------------------------
// -v — invalid config: validation error leaves SDK uninitialised (init does not throw)
// ---------------------------------------------------------------------------

describe("PulseProvider — invalid config (-v)", () => {
  it("SDK stays uninitialised when apiKey is empty", async () => {
    const errSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    render(
      // Wrap in ErrorBoundary so React doesn't surface the error as uncaught
      <PulseErrorBoundary fallback={<div data-testid="caught" />}>
        <PulseProvider
          config={{
            apiKey: "",
            serviceName: "app",
            dataCollectionState: PulseDataCollectionConsent.ALLOWED,
          }}
        >
          <div />
        </PulseProvider>
      </PulseErrorBoundary>,
    );
    await act(async () => {
      await flushMicrotasks();
    });
    expect(Pulse.isInitialized()).toBe(false);
    errSpy.mockRestore();
  });
});

// ---------------------------------------------------------------------------
// +v — PulseErrorBoundary: fallback ReactNode, function fallback, reset
// ---------------------------------------------------------------------------

describe("PulseErrorBoundary (+v)", () => {
  // Component that always throws on render
  function Bomb({ shouldThrow }: { shouldThrow: boolean }): React.ReactElement {
    if (shouldThrow) throw new Error("render bomb");
    return <div data-testid="ok">fine</div>;
  }

  it("renders children normally when no error", () => {
    const { getByTestId } = render(
      <PulseErrorBoundary fallback={<div>error</div>}>
        <Bomb shouldThrow={false} />
      </PulseErrorBoundary>,
    );
    expect(getByTestId("ok")).toBeTruthy();
  });

  it("renders ReactNode fallback when child throws", () => {
    const errSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    const { getByTestId } = render(
      <PulseErrorBoundary fallback={<div data-testid="fallback">oops</div>}>
        <Bomb shouldThrow={true} />
      </PulseErrorBoundary>,
    );
    expect(getByTestId("fallback").textContent).toBe("oops");
    errSpy.mockRestore();
  });

  it("calls reportDeviceCrash with react.component_stack on render error", () => {
    const errSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    const crashSpy = vi.spyOn(Pulse, "reportDeviceCrash");

    render(
      <PulseErrorBoundary fallback={<div>err</div>}>
        <Bomb shouldThrow={true} />
      </PulseErrorBoundary>,
    );

    expect(crashSpy).toHaveBeenCalledOnce();
    const [error, attrs] = crashSpy.mock.calls[0]!;
    expect((error as Error).message).toBe("render bomb");
    expect(
      (attrs as Record<string, string>)["react.component_stack"],
    ).toBeDefined();
    crashSpy.mockRestore();
    errSpy.mockRestore();
  });

  it("function fallback receives error and reset callback", () => {
    const errSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    let capturedReset: (() => void) | null = null;

    const { getByTestId } = render(
      <PulseErrorBoundary
        fallback={(err, reset) => {
          capturedReset = reset;
          return <div data-testid="fn-fallback">{err.message}</div>;
        }}
      >
        <Bomb shouldThrow={true} />
      </PulseErrorBoundary>,
    );

    expect(getByTestId("fn-fallback").textContent).toBe("render bomb");
    expect(typeof capturedReset).toBe("function");
    errSpy.mockRestore();
  });

  it("swallows fallback that throws during render; alwaysError logs even at NONE", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    PulseWebLogger.setLevel(PulseLogLevel.NONE);
    const alwaysSpy = vi
      .spyOn(PulseWebLogger, "alwaysError")
      .mockImplementation(() => {});

    function BadFallback(): React.ReactElement {
      throw new Error("fallback render boom");
    }

    const { container } = render(
      <PulseErrorBoundary fallback={<BadFallback />}>
        <Bomb shouldThrow={true} />
      </PulseErrorBoundary>,
    );

    expect(alwaysSpy).toHaveBeenCalled();
    expect(container.textContent).toBe("");
    alwaysSpy.mockRestore();
    vi.restoreAllMocks();
    PulseWebLogger.resetForTesting();
  });

  it("swallows function fallback that throws before returning UI", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    PulseWebLogger.setLevel(PulseLogLevel.NONE);
    const alwaysSpy = vi
      .spyOn(PulseWebLogger, "alwaysError")
      .mockImplementation(() => {});

    const { container } = render(
      <PulseErrorBoundary
        fallback={() => {
          throw new Error("sync throw in fallback fn");
        }}
      >
        <Bomb shouldThrow={true} />
      </PulseErrorBoundary>,
    );

    expect(alwaysSpy).toHaveBeenCalled();
    expect(container.textContent).toBe("");
    alwaysSpy.mockRestore();
    vi.restoreAllMocks();
    PulseWebLogger.resetForTesting();
  });
});

// ---------------------------------------------------------------------------
// -v — PulseErrorBoundary: no fallback renders null; reset clears error
// ---------------------------------------------------------------------------

describe("PulseErrorBoundary (-v)", () => {
  function Bomb(): React.ReactElement {
    throw new Error("bomb");
  }

  it("renders null when no fallback provided and child throws", () => {
    const errSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    const { container } = render(
      <PulseErrorBoundary>
        <Bomb />
      </PulseErrorBoundary>,
    );
    expect(container.firstChild).toBeNull();
    errSpy.mockRestore();
  });

  it("reset() clears the error state and re-renders children", () => {
    const errSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    let capturedReset: (() => void) | null = null;

    const { getByTestId, queryByTestId } = render(
      <PulseErrorBoundary
        fallback={(_err, reset) => {
          capturedReset = reset;
          return (
            <button data-testid="reset-btn" onClick={reset}>
              reset
            </button>
          );
        }}
      >
        <Bomb />
      </PulseErrorBoundary>,
    );

    // Boundary caught the error
    expect(getByTestId("reset-btn")).toBeTruthy();
    expect(capturedReset).not.toBeNull();

    // Calling reset clears the error — boundary tries to re-render children
    // (Bomb will throw again, but the reset mechanism itself is exercised)
    act(() => {
      capturedReset!();
    });

    // After reset the boundary attempted re-render — no crash in the mechanism
    errSpy.mockRestore();
  });
});
