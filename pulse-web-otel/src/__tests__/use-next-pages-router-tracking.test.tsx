/**
 * Unit tests for useNextPagesRouterTracking (Pages Router hook).
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook } from "@testing-library/react";

// ─── Mocks ────────────────────────────────────────────────────────────────────

let activeHandler: ((url: string) => void) | null = null;
const mockOn = vi.fn((event: string, handler: (url: string) => void) => {
  if (event === "routeChangeComplete") activeHandler = handler;
});
const mockOff = vi.fn((event: string) => {
  if (event === "routeChangeComplete") activeHandler = null;
});

vi.mock("next/router.js", () => ({
  useRouter: () => ({
    events: { on: mockOn, off: mockOff },
  }),
}));

const mockSetScreenName = vi.fn();
const mockNotifySoftNavigation = vi.fn();
vi.mock("../sdk", () => ({
  Pulse: {
    setScreenName: (name: string) => mockSetScreenName(name),
    notifySoftNavigation: () => mockNotifySoftNavigation(),
  },
}));

import { useNextPagesRouterTracking } from "../integrations/next/useNextPagesRouterTracking";
import type { UseNextPagesRouterTrackingOptions } from "../types/next";

// ─── Tests ────────────────────────────────────────────────────────────────────

describe("useNextPagesRouterTracking", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    activeHandler = null;
  });

  it("registers routeChangeComplete listener on mount", () => {
    renderHook(() => useNextPagesRouterTracking());
    expect(mockOn).toHaveBeenCalledWith(
      "routeChangeComplete",
      expect.any(Function),
    );
  });

  it("deregisters listener on unmount (no leak)", () => {
    const { unmount } = renderHook(() => useNextPagesRouterTracking());
    unmount();
    expect(mockOff).toHaveBeenCalledWith(
      "routeChangeComplete",
      expect.any(Function),
    );
    // After unmount, activeHandler is null — calling it is a no-op
    activeHandler?.("/ghost");
    expect(mockSetScreenName).not.toHaveBeenCalled();
  });

  it("calls setScreenName with pathname on routeChangeComplete", () => {
    renderHook(() => useNextPagesRouterTracking());
    activeHandler?.("/products?foo=bar");
    expect(mockSetScreenName).toHaveBeenCalledWith("/products");
  });

  it("includes full URL when includeSearch=true", () => {
    renderHook(() => useNextPagesRouterTracking({ includeSearch: true }));
    activeHandler?.("/products?foo=bar");
    expect(mockSetScreenName).toHaveBeenCalledWith("/products?foo=bar");
  });

  it("uses format callback when provided", () => {
    const format = vi.fn(
      (loc: { pathname: string; search: string; hash: string }) =>
        `SCREEN:${loc.pathname}`,
    );
    renderHook(() => useNextPagesRouterTracking({ format }));
    activeHandler?.("/cart");
    expect(format).toHaveBeenCalledWith({
      pathname: "/cart",
      search: "",
      hash: "",
    });
    expect(mockSetScreenName).toHaveBeenCalledWith("SCREEN:/cart");
  });

  it("format receives search without leading ?", () => {
    const format = vi.fn((loc: { search: string }) => loc.search);
    renderHook(() => useNextPagesRouterTracking({ format }));
    activeHandler?.("/page?q=test");
    expect(format).toHaveBeenCalledWith(
      expect.objectContaining({ search: "q=test" }),
    );
  });

  it("does NOT call setScreenName on initial mount (routeChangeComplete not fired on load)", () => {
    renderHook(() => useNextPagesRouterTracking());
    expect(mockSetScreenName).not.toHaveBeenCalled();
  });

  // ─── Negative / edge ─────────────────────────────────────────────────────

  it("fires on each subsequent navigation (not just the first)", () => {
    renderHook(() => useNextPagesRouterTracking());

    activeHandler?.("/products");
    activeHandler?.("/cart");
    activeHandler?.("/");

    expect(mockSetScreenName).toHaveBeenCalledTimes(3);
    expect(mockSetScreenName).toHaveBeenNthCalledWith(1, "/products");
    expect(mockSetScreenName).toHaveBeenNthCalledWith(2, "/cart");
    expect(mockSetScreenName).toHaveBeenNthCalledWith(3, "/");
  });

  it("strips hash fragment from screen name (pathname only, no #section)", () => {
    renderHook(() => useNextPagesRouterTracking());
    activeHandler?.("/page#section");
    // hash is not part of pathname — screen name must be pathname only
    expect(mockSetScreenName).toHaveBeenCalledWith("/page");
  });

  it("format receives hash without leading #", () => {
    const format = vi.fn((loc: { hash: string }) => loc.hash);
    renderHook(() => useNextPagesRouterTracking({ format }));
    activeHandler?.("/page#section");
    expect(format).toHaveBeenCalledWith(
      expect.objectContaining({ hash: "section" }),
    );
  });

  it("format receives empty search when URL has no query", () => {
    const format = vi.fn((loc: { search: string }) => `s=${loc.search}`);
    renderHook(() => useNextPagesRouterTracking({ format }));
    activeHandler?.("/clean");
    expect(format).toHaveBeenCalledWith(
      expect.objectContaining({ search: "" }),
    );
    expect(mockSetScreenName).toHaveBeenCalledWith("s=");
  });

  it("picks up options changed after mount via optionsRef (no re-registration)", () => {
    const { rerender } = renderHook(
      (opts: UseNextPagesRouterTrackingOptions) =>
        useNextPagesRouterTracking(opts),
      { initialProps: { includeSearch: false } },
    );

    // First nav — options at mount (includeSearch=false)
    activeHandler?.("/a?x=1");
    expect(mockSetScreenName).toHaveBeenCalledWith("/a");

    // Change option to includeSearch=true after mount
    rerender({ includeSearch: true });
    // Listener must NOT be re-registered — still exactly one mockOn call
    expect(mockOn).toHaveBeenCalledTimes(1);

    // Next nav — handler reads updated optionsRef, applies new includeSearch
    activeHandler?.("/b?y=2");
    expect(mockSetScreenName).toHaveBeenLastCalledWith("/b?y=2");
  });

  it("includeSearch=true preserves hash in dependency (full url arg)", () => {
    renderHook(() => useNextPagesRouterTracking({ includeSearch: true }));
    // Next.js passes the full URL including hash to routeChangeComplete
    activeHandler?.("/page?q=1#section");
    // When includeSearch=true, dependency = url (the full string as received)
    expect(mockSetScreenName).toHaveBeenCalledWith("/page?q=1#section");
  });

  // ─── skipInitial ─────────────────────────────────────────────────────────

  it("skipInitial defaults to false — first navigation is tracked", () => {
    renderHook(() => useNextPagesRouterTracking());
    activeHandler?.("/first");
    expect(mockSetScreenName).toHaveBeenCalledTimes(1);
    expect(mockSetScreenName).toHaveBeenCalledWith("/first");
  });

  it("skipInitial=true — skips first routeChangeComplete, tracks subsequent", () => {
    renderHook(() => useNextPagesRouterTracking({ skipInitial: true }));
    activeHandler?.("/first"); // skipped
    expect(mockSetScreenName).not.toHaveBeenCalled();
    activeHandler?.("/second"); // tracked
    expect(mockSetScreenName).toHaveBeenCalledTimes(1);
    expect(mockSetScreenName).toHaveBeenCalledWith("/second");
  });

  it("skipInitial=false — all navigations tracked including first", () => {
    renderHook(() => useNextPagesRouterTracking({ skipInitial: false }));
    activeHandler?.("/first");
    activeHandler?.("/second");
    expect(mockSetScreenName).toHaveBeenCalledTimes(2);
    expect(mockSetScreenName).toHaveBeenNthCalledWith(1, "/first");
    expect(mockSetScreenName).toHaveBeenNthCalledWith(2, "/second");
  });

  // ─── notifySoftNavigation — flush buffered vitals on SPA route change ─────

  describe("notifySoftNavigation on SPA nav", () => {
    it("calls notifySoftNavigation once when routeChangeComplete fires", () => {
      renderHook(() => useNextPagesRouterTracking());
      activeHandler?.("/products");
      expect(mockNotifySoftNavigation).toHaveBeenCalledTimes(1);
    });

    it("skipInitial=true — does NOT call on first event, calls on second", () => {
      renderHook(() => useNextPagesRouterTracking({ skipInitial: true }));
      activeHandler?.("/first");
      expect(mockNotifySoftNavigation).not.toHaveBeenCalled();
      activeHandler?.("/second");
      expect(mockNotifySoftNavigation).toHaveBeenCalledTimes(1);
    });

    it("calls notifySoftNavigation same number of times as setScreenName across multiple navs", () => {
      renderHook(() => useNextPagesRouterTracking());
      activeHandler?.("/a");
      activeHandler?.("/b");
      activeHandler?.("/c");
      expect(mockNotifySoftNavigation).toHaveBeenCalledTimes(3);
      expect(mockNotifySoftNavigation).toHaveBeenCalledTimes(
        mockSetScreenName.mock.calls.length,
      );
    });
  });
});
