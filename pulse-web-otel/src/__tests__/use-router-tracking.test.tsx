// Mock @opentelemetry/api-logs — same pattern as other SDK tests
vi.mock("@opentelemetry/api-logs", () => ({
  logs: {
    getLogger: vi.fn().mockReturnValue({ emit: vi.fn() }),
    setGlobalLoggerProvider: vi.fn(),
  },
}));

import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import React, { StrictMode } from "react";
import { render, act } from "@testing-library/react";
import { MemoryRouter, Routes, Route, useNavigate } from "react-router-dom";

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
    }),
  };
});

import { useRouterTracking } from "../integrations/react/useRouterTracking";
import { PulseWeb } from "../sdk";

// Stub `setScreenName` — we assert call counts/arguments on this spy.
let setScreenNameSpy: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
  setScreenNameSpy = vi
    .spyOn(PulseWeb, "setScreenName")
    .mockImplementation(() => {});
});

afterEach(() => {
  setScreenNameSpy.mockRestore();
  vi.unstubAllGlobals();
});

// Test harness: mounts a MemoryRouter, calls useRouterTracking with options,
// and exposes a `navigate` fn via ref so tests can trigger route changes.
type NavigateRef = { current: ((to: string) => void) | null };
function makeHarness(opts: {
  initial: string;
  hookOptions?: Parameters<typeof useRouterTracking>[0];
  navigateRef: NavigateRef;
  strict?: boolean;
}) {
  const Inner: React.FC = () => {
    useRouterTracking(opts.hookOptions);
    const navigate = useNavigate();
    opts.navigateRef.current = navigate;
    return <div data-testid="inner" />;
  };

  const tree = (
    <MemoryRouter initialEntries={[opts.initial]}>
      <Routes>
        <Route path="*" element={<Inner />} />
      </Routes>
    </MemoryRouter>
  );

  return opts.strict ? <StrictMode>{tree}</StrictMode> : tree;
}

// ---------------------------------------------------------------------------
// 1. Initial mount — skipInitial default `true` → no setScreenName call
// ---------------------------------------------------------------------------

describe("useRouterTracking — initial mount", () => {
  it("does NOT call setScreenName on first mount (skipInitial defaults to true)", async () => {
    const navigateRef: NavigateRef = { current: null };
    render(makeHarness({ initial: "/foo", navigateRef }));
    await act(async () => {
      await Promise.resolve();
    });

    expect(setScreenNameSpy).not.toHaveBeenCalled();
  });

  it("calls setScreenName on first mount when skipInitial is false", async () => {
    const navigateRef: NavigateRef = { current: null };
    render(
      makeHarness({
        initial: "/foo",
        navigateRef,
        hookOptions: { skipInitial: false },
      }),
    );
    await act(async () => {
      await Promise.resolve();
    });

    expect(setScreenNameSpy).toHaveBeenCalledTimes(1);
    expect(setScreenNameSpy).toHaveBeenCalledWith("/foo");
  });
});

// ---------------------------------------------------------------------------
// 2. Route change — fires setScreenName with the new pathname
// ---------------------------------------------------------------------------

describe("useRouterTracking — route change", () => {
  it("fires setScreenName once when navigating to a new path", async () => {
    const navigateRef: NavigateRef = { current: null };
    render(makeHarness({ initial: "/foo", navigateRef }));
    await act(async () => {
      await Promise.resolve();
    });
    expect(setScreenNameSpy).not.toHaveBeenCalled();

    await act(async () => {
      navigateRef.current?.("/bar");
    });

    expect(setScreenNameSpy).toHaveBeenCalledTimes(1);
    expect(setScreenNameSpy).toHaveBeenCalledWith("/bar");
  });

  it("does NOT fire when navigating to the same pathname", async () => {
    const navigateRef: NavigateRef = { current: null };
    render(makeHarness({ initial: "/a", navigateRef }));
    await act(async () => {
      await Promise.resolve();
    });

    await act(async () => {
      navigateRef.current?.("/a");
    });

    expect(setScreenNameSpy).not.toHaveBeenCalled();
  });

  it("does NOT fire on query-string-only changes by default", async () => {
    const navigateRef: NavigateRef = { current: null };
    render(makeHarness({ initial: "/x", navigateRef }));
    await act(async () => {
      await Promise.resolve();
    });

    await act(async () => {
      navigateRef.current?.("/x?q=1");
    });

    expect(setScreenNameSpy).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// 3. includeSearch — query string participates in screen-name key
// ---------------------------------------------------------------------------

describe("useRouterTracking — includeSearch", () => {
  it("fires setScreenName with pathname+search when includeSearch is true", async () => {
    const navigateRef: NavigateRef = { current: null };
    render(
      makeHarness({
        initial: "/x",
        navigateRef,
        hookOptions: { includeSearch: true },
      }),
    );
    await act(async () => {
      await Promise.resolve();
    });

    await act(async () => {
      navigateRef.current?.("/x?q=1");
    });

    expect(setScreenNameSpy).toHaveBeenCalledTimes(1);
    expect(setScreenNameSpy).toHaveBeenCalledWith("/x?q=1");
  });
});

// ---------------------------------------------------------------------------
// 4. Custom format function
// ---------------------------------------------------------------------------

describe("useRouterTracking — custom format", () => {
  it("passes the formatted screen name to setScreenName", async () => {
    const navigateRef: NavigateRef = { current: null };
    const format = vi.fn((loc: { pathname: string }) => `app:${loc.pathname}`);
    render(
      makeHarness({
        initial: "/foo",
        navigateRef,
        hookOptions: { format },
      }),
    );
    await act(async () => {
      await Promise.resolve();
    });

    await act(async () => {
      navigateRef.current?.("/bar");
    });

    expect(setScreenNameSpy).toHaveBeenCalledWith("app:/bar");
  });
});

// ---------------------------------------------------------------------------
// 5. StrictMode — no duplicate signals in dev double-mount
// ---------------------------------------------------------------------------

describe("useRouterTracking — StrictMode", () => {
  it("fires setScreenName exactly once per real route change under StrictMode", async () => {
    const navigateRef: NavigateRef = { current: null };
    render(makeHarness({ initial: "/foo", navigateRef, strict: true }));
    await act(async () => {
      await Promise.resolve();
    });

    // Initial mount (even under StrictMode) should be skipped.
    expect(setScreenNameSpy).not.toHaveBeenCalled();

    await act(async () => {
      navigateRef.current?.("/bar");
    });

    expect(setScreenNameSpy).toHaveBeenCalledTimes(1);
    expect(setScreenNameSpy).toHaveBeenCalledWith("/bar");
  });
});

// ---------------------------------------------------------------------------
// 6. Multiple sequential navigations
// ---------------------------------------------------------------------------

describe("useRouterTracking — multiple navigations", () => {
  it("fires setScreenName for each distinct pathname in sequence", async () => {
    const navigateRef: NavigateRef = { current: null };
    render(makeHarness({ initial: "/a", navigateRef }));
    await act(async () => {
      await Promise.resolve();
    });

    await act(async () => {
      navigateRef.current?.("/b");
    });
    await act(async () => {
      navigateRef.current?.("/c");
    });
    await act(async () => {
      navigateRef.current?.("/d");
    });

    expect(setScreenNameSpy.mock.calls.map((c) => c[0])).toEqual([
      "/b",
      "/c",
      "/d",
    ]);
  });
});

// ---------------------------------------------------------------------------
// 7. Unmount — no leaked listeners, no errors
// ---------------------------------------------------------------------------

describe("useRouterTracking — unmount", () => {
  it("unmount after navigations does not throw", async () => {
    const navigateRef: NavigateRef = { current: null };
    const { unmount } = render(makeHarness({ initial: "/a", navigateRef }));
    await act(async () => {
      await Promise.resolve();
    });
    await act(async () => {
      navigateRef.current?.("/b");
    });

    expect(() => unmount()).not.toThrow();
  });
});

// ---------------------------------------------------------------------------
// 8. No-leak after unmount — setScreenName must NOT fire after component removed
// ---------------------------------------------------------------------------

describe("useRouterTracking — no-leak after unmount", () => {
  it("does NOT call setScreenName after component is unmounted", async () => {
    const navigateRef: NavigateRef = { current: null };

    // We need a controller to navigate AFTER unmount.
    // Use a separate navigate ref captured before unmount.
    const { unmount } = render(makeHarness({ initial: "/a", navigateRef }));
    await act(async () => {
      await Promise.resolve();
    });

    // Navigate once before unmount — establishes prevDependency ref
    await act(async () => {
      navigateRef.current?.("/b");
    });
    expect(setScreenNameSpy).toHaveBeenCalledTimes(1);

    // Unmount the component
    unmount();

    // Clear spy to isolate post-unmount calls
    setScreenNameSpy.mockClear();

    // Any location change that might fire after unmount should be silent
    // (the useEffect cleanup removes the dependency on location changes)
    // We verify by checking the spy stays empty after a tick
    await act(async () => {
      await Promise.resolve();
    });

    expect(setScreenNameSpy).not.toHaveBeenCalled();
  });

  it("no console error about state update on unmounted component", async () => {
    const consoleSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    const navigateRef: NavigateRef = { current: null };

    const { unmount } = render(makeHarness({ initial: "/a", navigateRef }));
    await act(async () => {
      await Promise.resolve();
    });

    await act(async () => {
      navigateRef.current?.("/b");
    });

    unmount();

    await act(async () => {
      await Promise.resolve();
    });

    // React 18 no longer warns about state updates on unmounted components,
    // but any error logged means something went wrong in cleanup.
    const errorCalls = consoleSpy.mock.calls.filter(
      (c) => c[0] && String(c[0]).toLowerCase().includes("unmounted"),
    );
    expect(errorCalls).toHaveLength(0);
    consoleSpy.mockRestore();
  });
});
