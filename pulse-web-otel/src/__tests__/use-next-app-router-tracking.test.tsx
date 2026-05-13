/**
 * Unit tests for useNextAppRouterTracking (App Router hook).
 */
import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook } from "@testing-library/react";

// ─── Mocks ────────────────────────────────────────────────────────────────────

const mockPathname = vi.fn<() => string | null>(() => "/");
const mockSearchParams = vi.fn(() => new URLSearchParams());

vi.mock("next/navigation", () => ({
  usePathname: () => mockPathname(),
  useSearchParams: () => mockSearchParams(),
}));

const mockSetScreenName = vi.fn();
const mockNotifySoftNavigation = vi.fn();
vi.mock("../sdk", () => ({
  Pulse: {
    setScreenName: (name: string) => mockSetScreenName(name),
    notifySoftNavigation: () => mockNotifySoftNavigation(),
  },
}));

// ─── Import after mocks ───────────────────────────────────────────────────────

import {
  useNextAppRouterTracking,
  type UseNextAppRouterTrackingOptions,
} from "../integrations/next/useNextAppRouterTracking";

// Stable component at module level — preserves useRef across rerenders.
const TestHook: React.FC<{ options?: UseNextAppRouterTrackingOptions }> = ({
  options = {},
}) => {
  useNextAppRouterTracking(options);
  return null;
};

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("useNextAppRouterTracking", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPathname.mockReturnValue("/");
    mockSearchParams.mockReturnValue(new URLSearchParams());
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("does NOT call setScreenName on initial mount when skipInitial=true (default)", () => {
    renderHook(() => useNextAppRouterTracking());
    expect(mockSetScreenName).not.toHaveBeenCalled();
  });

  it("calls setScreenName on initial mount when skipInitial=false", () => {
    renderHook(() => useNextAppRouterTracking({ skipInitial: false }));
    expect(mockSetScreenName).toHaveBeenCalledWith("/");
  });

  it("calls setScreenName when pathname changes", () => {
    const hook = renderHook(
      (opts: UseNextAppRouterTrackingOptions) => useNextAppRouterTracking(opts),
      { initialProps: {} },
    );

    // First render — skipInitial=true, no call
    expect(mockSetScreenName).not.toHaveBeenCalled();

    // Navigate to /products
    mockPathname.mockReturnValue("/products");
    hook.rerender({});
    expect(mockSetScreenName).toHaveBeenCalledWith("/products");
  });

  it("skips setScreenName when pathname is null (static pre-render)", () => {
    mockPathname.mockReturnValue(null);
    renderHook(() => useNextAppRouterTracking({ skipInitial: false }));
    expect(mockSetScreenName).not.toHaveBeenCalled();
  });

  it("includes search in dependency when includeSearch=true", () => {
    mockSearchParams.mockReturnValue(new URLSearchParams("q=test"));
    renderHook(() =>
      useNextAppRouterTracking({ skipInitial: false, includeSearch: true }),
    );
    expect(mockSetScreenName).toHaveBeenCalledWith("/?q=test");
  });

  it("calls format callback with pathname/search/hash", () => {
    const format = vi.fn(
      (loc: { pathname: string; search: string; hash: string }) =>
        `SCREEN:${loc.pathname}`,
    );
    renderHook(() => useNextAppRouterTracking({ skipInitial: false, format }));
    expect(format).toHaveBeenCalledWith({
      pathname: "/",
      search: "",
      hash: "",
    });
    expect(mockSetScreenName).toHaveBeenCalledWith("SCREEN:/");
  });

  it("does not call setScreenName again when same pathname re-renders (StrictMode safety)", () => {
    const { rerender } = renderHook(
      (opts: UseNextAppRouterTrackingOptions) => useNextAppRouterTracking(opts),
      { initialProps: { skipInitial: false } },
    );
    expect(mockSetScreenName).toHaveBeenCalledTimes(1);
    // Same pathname — no additional call
    rerender({ skipInitial: false });
    expect(mockSetScreenName).toHaveBeenCalledTimes(1);
  });

  it("format receives empty hash in App Router", () => {
    const format = vi.fn(() => "custom");
    renderHook(() => useNextAppRouterTracking({ skipInitial: false, format }));
    expect(format).toHaveBeenCalledWith(expect.objectContaining({ hash: "" }));
  });

  // ─── Negative: includeSearch=false isolation ──────────────────────────────

  it("does NOT include search when includeSearch=false (default) even if params present", () => {
    mockSearchParams.mockReturnValue(new URLSearchParams("q=test&page=2"));
    renderHook(() =>
      useNextAppRouterTracking({ skipInitial: false, includeSearch: false }),
    );
    // screen name must be pathname only — no query string
    expect(mockSetScreenName).toHaveBeenCalledWith("/");
    expect(mockSetScreenName).not.toHaveBeenCalledWith(
      expect.stringContaining("?"),
    );
  });

  it("search-params-only change does NOT fire when includeSearch=false", () => {
    const hook = renderHook(
      (opts: UseNextAppRouterTrackingOptions) => useNextAppRouterTracking(opts),
      {
        initialProps: { skipInitial: false } as UseNextAppRouterTrackingOptions,
      },
    );
    expect(mockSetScreenName).toHaveBeenCalledTimes(1); // initial mount
    vi.clearAllMocks();

    // Same pathname, different search params — dependency unchanged (pathname only)
    mockSearchParams.mockReturnValue(new URLSearchParams("filter=new"));
    hook.rerender({ skipInitial: false });
    expect(mockSetScreenName).not.toHaveBeenCalled();
  });

  it("search-params change DOES fire when includeSearch=true", () => {
    mockSearchParams.mockReturnValue(new URLSearchParams());
    const hook = renderHook(
      (opts: UseNextAppRouterTrackingOptions) => useNextAppRouterTracking(opts),
      {
        initialProps: {
          skipInitial: false,
          includeSearch: true,
        } as UseNextAppRouterTrackingOptions,
      },
    );
    expect(mockSetScreenName).toHaveBeenCalledWith("/?");
    vi.clearAllMocks();

    // Same pathname, new search params — dependency changes
    mockSearchParams.mockReturnValue(new URLSearchParams("filter=new"));
    hook.rerender({ skipInitial: false, includeSearch: true });
    expect(mockSetScreenName).toHaveBeenCalledWith("/?filter=new");
  });

  // ─── Resilience: navigation instrumentation disabled ─────────────────────
  // When PulseProvider is configured with `navigation: { enabled: false }`,
  // the SDK's setScreenName becomes a no-op (guarded by _initialized check).
  // The hook must not crash and must still attempt the call — the SDK is
  // responsible for no-oping when navigation is disabled / not yet init'd.

  it("does NOT throw when setScreenName is a no-op (simulates navigation instrumentation disabled)", () => {
    // mockSetScreenName is already a jest.fn() no-op by default — this mirrors
    // what the SDK does when it isn't initialized or navigation is disabled.
    expect(() =>
      renderHook(() => useNextAppRouterTracking({ skipInitial: false })),
    ).not.toThrow();
    // Hook still invokes setScreenName — guarding belongs in the SDK, not here.
    expect(mockSetScreenName).toHaveBeenCalledWith("/");
  });

  it("resumes tracking after setScreenName transitions from no-op to active", () => {
    // First render: setScreenName is a no-op (SDK not ready / navigation disabled).
    // After SDK init, setScreenName starts recording. The hook must pick up the
    // next navigation normally.
    mockSetScreenName.mockImplementationOnce(() => {
      /* no-op — SDK not ready */
    });

    const hook = renderHook(
      (opts: UseNextAppRouterTrackingOptions) => useNextAppRouterTracking(opts),
      {
        initialProps: { skipInitial: false } as UseNextAppRouterTrackingOptions,
      },
    );
    // First call was a no-op but the hook still called through.
    expect(mockSetScreenName).toHaveBeenCalledTimes(1);
    vi.clearAllMocks();

    // SDK is now active — navigate to /dashboard.
    mockPathname.mockReturnValue("/dashboard");
    hook.rerender({ skipInitial: false });
    expect(mockSetScreenName).toHaveBeenCalledWith("/dashboard");
  });

  // ─── Edge: null → real pathname transition ────────────────────────────────

  it("fires setScreenName when pathname recovers from null to a real path", () => {
    mockPathname.mockReturnValue(null);
    const hook = renderHook(
      (opts: UseNextAppRouterTrackingOptions) => useNextAppRouterTracking(opts),
      {
        initialProps: { skipInitial: false } as UseNextAppRouterTrackingOptions,
      },
    );
    // null → skipped entirely (prevDependency stays null)
    expect(mockSetScreenName).not.toHaveBeenCalled();

    // Pathname becomes real (SSR fallback resolved)
    mockPathname.mockReturnValue("/products");
    hook.rerender({ skipInitial: false });
    expect(mockSetScreenName).toHaveBeenCalledWith("/products");
  });

  // ─── notifySoftNavigation — flush buffered vitals on SPA route change ─────

  describe("notifySoftNavigation on SPA nav", () => {
    it("calls notifySoftNavigation when pathname changes", () => {
      const hook = renderHook(
        (opts: UseNextAppRouterTrackingOptions) =>
          useNextAppRouterTracking(opts),
        { initialProps: {} },
      );
      expect(mockNotifySoftNavigation).not.toHaveBeenCalled();

      mockPathname.mockReturnValue("/products");
      hook.rerender({});
      expect(mockNotifySoftNavigation).toHaveBeenCalledTimes(1);
    });

    it("does NOT call notifySoftNavigation when same pathname re-renders", () => {
      const { rerender } = renderHook(
        (opts: UseNextAppRouterTrackingOptions) =>
          useNextAppRouterTracking(opts),
        { initialProps: { skipInitial: false } },
      );
      expect(mockNotifySoftNavigation).toHaveBeenCalledTimes(1);

      rerender({ skipInitial: false });
      expect(mockNotifySoftNavigation).toHaveBeenCalledTimes(1);
    });

    it("does NOT call notifySoftNavigation when pathname is null (static pre-render)", () => {
      mockPathname.mockReturnValue(null);
      renderHook(() => useNextAppRouterTracking({ skipInitial: false }));
      expect(mockNotifySoftNavigation).not.toHaveBeenCalled();
    });

    it("does NOT call notifySoftNavigation on initial mount when skipInitial=true", () => {
      renderHook(() => useNextAppRouterTracking());
      expect(mockNotifySoftNavigation).not.toHaveBeenCalled();
    });

    it("calls notifySoftNavigation on initial mount when skipInitial=false", () => {
      renderHook(() => useNextAppRouterTracking({ skipInitial: false }));
      expect(mockNotifySoftNavigation).toHaveBeenCalledTimes(1);
    });
  });
});
