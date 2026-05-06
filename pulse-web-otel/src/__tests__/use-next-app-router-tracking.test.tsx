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
vi.mock("../sdk", () => ({
  PulseWeb: { setScreenName: (name: string) => mockSetScreenName(name) },
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
    const format = vi.fn((loc: { pathname: string; search: string; hash: string }) =>
      `SCREEN:${loc.pathname}`,
    );
    renderHook(() =>
      useNextAppRouterTracking({ skipInitial: false, format }),
    );
    expect(format).toHaveBeenCalledWith({ pathname: "/", search: "", hash: "" });
    expect(mockSetScreenName).toHaveBeenCalledWith("SCREEN:/");
  });

  it("does not call setScreenName again when same pathname re-renders (StrictMode safety)", () => {
    const { rerender } = renderHook(
      (opts: UseNextAppRouterTrackingOptions) =>
        useNextAppRouterTracking(opts),
      { initialProps: { skipInitial: false } },
    );
    expect(mockSetScreenName).toHaveBeenCalledTimes(1);
    // Same pathname — no additional call
    rerender({ skipInitial: false });
    expect(mockSetScreenName).toHaveBeenCalledTimes(1);
  });

  it("format receives empty hash in App Router", () => {
    const format = vi.fn(() => "custom");
    renderHook(() =>
      useNextAppRouterTracking({ skipInitial: false, format }),
    );
    expect(format).toHaveBeenCalledWith(
      expect.objectContaining({ hash: "" }),
    );
  });

  // ─── Negative: includeSearch=false isolation ──────────────────────────────

  it("does NOT include search when includeSearch=false (default) even if params present", () => {
    mockSearchParams.mockReturnValue(new URLSearchParams("q=test&page=2"));
    renderHook(() =>
      useNextAppRouterTracking({ skipInitial: false, includeSearch: false }),
    );
    // screen name must be pathname only — no query string
    expect(mockSetScreenName).toHaveBeenCalledWith("/");
    expect(mockSetScreenName).not.toHaveBeenCalledWith(expect.stringContaining("?"));
  });

  it("search-params-only change does NOT fire when includeSearch=false", () => {
    const hook = renderHook(
      (opts: UseNextAppRouterTrackingOptions) => useNextAppRouterTracking(opts),
      { initialProps: { skipInitial: false } as UseNextAppRouterTrackingOptions },
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
      { initialProps: { skipInitial: false, includeSearch: true } as UseNextAppRouterTrackingOptions },
    );
    expect(mockSetScreenName).toHaveBeenCalledWith("/?");
    vi.clearAllMocks();

    // Same pathname, new search params — dependency changes
    mockSearchParams.mockReturnValue(new URLSearchParams("filter=new"));
    hook.rerender({ skipInitial: false, includeSearch: true });
    expect(mockSetScreenName).toHaveBeenCalledWith("/?filter=new");
  });

  // ─── Edge: null → real pathname transition ────────────────────────────────

  it("fires setScreenName when pathname recovers from null to a real path", () => {
    mockPathname.mockReturnValue(null);
    const hook = renderHook(
      (opts: UseNextAppRouterTrackingOptions) => useNextAppRouterTracking(opts),
      { initialProps: { skipInitial: false } as UseNextAppRouterTrackingOptions },
    );
    // null → skipped entirely (prevDependency stays null)
    expect(mockSetScreenName).not.toHaveBeenCalled();

    // Pathname becomes real (SSR fallback resolved)
    mockPathname.mockReturnValue("/products");
    hook.rerender({ skipInitial: false });
    expect(mockSetScreenName).toHaveBeenCalledWith("/products");
  });
});
