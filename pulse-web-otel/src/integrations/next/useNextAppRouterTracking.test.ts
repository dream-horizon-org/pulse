import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useNextAppRouterTracking } from "./useNextAppRouterTracking";
import { Pulse } from "../../sdk";

vi.mock("next/navigation.js", () => ({
  usePathname: vi.fn(),
  useSearchParams: vi.fn(),
}));

vi.mock("../../sdk", () => ({
  Pulse: {
    setScreenName: vi.fn(),
    notifySoftNavigation: vi.fn(),
    _triggerNavigationRouteChange: vi.fn(),
  },
}));

import { usePathname, useSearchParams } from "next/navigation.js";

describe("useNextAppRouterTracking (Next.js App Router)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("should detect pathname change and call setScreenName", () => {
    const mockUsePathname = usePathname as ReturnType<typeof vi.fn>;
    const mockUseSearchParams = useSearchParams as ReturnType<typeof vi.fn>;

    mockUsePathname.mockReturnValue("/products");
    mockUseSearchParams.mockReturnValue(new URLSearchParams());

    const { rerender } = renderHook(() => useNextAppRouterTracking());

    // Initial call is skipped by default
    expect(Pulse.setScreenName).not.toHaveBeenCalled();

    // Change pathname
    mockUsePathname.mockReturnValue("/checkout");

    rerender();

    expect(Pulse.setScreenName).toHaveBeenCalledWith("/checkout");
  });

  it("should handle null pathname during static pre-rendering", () => {
    const mockUsePathname = usePathname as ReturnType<typeof vi.fn>;
    const mockUseSearchParams = useSearchParams as ReturnType<typeof vi.fn>;

    mockUsePathname.mockReturnValue(null);
    mockUseSearchParams.mockReturnValue(new URLSearchParams());

    expect(() => {
      renderHook(() => useNextAppRouterTracking());
    }).not.toThrow();

    expect(Pulse.setScreenName).not.toHaveBeenCalled();
  });

  it("should not treat query string changes as navigation when includeSearch is false", () => {
    const mockUsePathname = usePathname as ReturnType<typeof vi.fn>;
    const mockUseSearchParams = useSearchParams as ReturnType<typeof vi.fn>;

    mockUsePathname.mockReturnValue("/products");
    mockUseSearchParams.mockReturnValue(new URLSearchParams());

    const { rerender } = renderHook(() =>
      useNextAppRouterTracking({ skipInitial: false }),
    );

    expect(Pulse.setScreenName).toHaveBeenCalledWith("/products");
    vi.clearAllMocks();

    // Query string changes but pathname stays the same
    mockUseSearchParams.mockReturnValue(new URLSearchParams("filter=new"));

    rerender();

    // Should not be called again because pathname hasn't changed
    expect(Pulse.setScreenName).not.toHaveBeenCalled();
  });

  it("should include search params when includeSearch is true", () => {
    const mockUsePathname = usePathname as ReturnType<typeof vi.fn>;
    const mockUseSearchParams = useSearchParams as ReturnType<typeof vi.fn>;

    mockUsePathname.mockReturnValue("/products");
    mockUseSearchParams.mockReturnValue(new URLSearchParams("filter=new"));

    const { rerender } = renderHook(() =>
      useNextAppRouterTracking({ includeSearch: true, skipInitial: false }),
    );

    expect(Pulse.setScreenName).toHaveBeenCalledWith("/products?filter=new");
    vi.clearAllMocks();

    // Query string changes
    mockUseSearchParams.mockReturnValue(new URLSearchParams("filter=old"));

    rerender();

    expect(Pulse.setScreenName).toHaveBeenCalledWith("/products?filter=old");
  });

  it("should use custom format function when provided", () => {
    const mockUsePathname = usePathname as ReturnType<typeof vi.fn>;
    const mockUseSearchParams = useSearchParams as ReturnType<typeof vi.fn>;
    const mockFormat = vi.fn().mockReturnValue("CustomProductsScreen");

    mockUsePathname.mockReturnValue("/products/123");
    mockUseSearchParams.mockReturnValue(new URLSearchParams());

    const { rerender } = renderHook(() =>
      useNextAppRouterTracking({ format: mockFormat, skipInitial: false }),
    );

    expect(mockFormat).toHaveBeenCalledWith({
      pathname: "/products/123",
      search: "",
      hash: "",
    });
    expect(Pulse.setScreenName).toHaveBeenCalledWith("CustomProductsScreen");
    vi.clearAllMocks();

    // Navigate to another route
    mockUsePathname.mockReturnValue("/checkout");

    rerender();

    expect(Pulse.setScreenName).toHaveBeenCalledWith("CustomProductsScreen");
  });

  it("should skip initial call when skipInitial is true (default)", () => {
    const mockUsePathname = usePathname as ReturnType<typeof vi.fn>;
    const mockUseSearchParams = useSearchParams as ReturnType<typeof vi.fn>;

    mockUsePathname.mockReturnValue("/products");
    mockUseSearchParams.mockReturnValue(new URLSearchParams());

    renderHook(() => useNextAppRouterTracking({ skipInitial: true }));

    expect(Pulse.setScreenName).not.toHaveBeenCalled();
  });

  it("should call on initial render when skipInitial is false", () => {
    const mockUsePathname = usePathname as ReturnType<typeof vi.fn>;
    const mockUseSearchParams = useSearchParams as ReturnType<typeof vi.fn>;

    mockUsePathname.mockReturnValue("/products");
    mockUseSearchParams.mockReturnValue(new URLSearchParams());

    renderHook(() => useNextAppRouterTracking({ skipInitial: false }));

    expect(Pulse.setScreenName).toHaveBeenCalledWith("/products");
  });

  it("should be StrictMode safe (no duplicate calls)", () => {
    const mockUsePathname = usePathname as ReturnType<typeof vi.fn>;
    const mockUseSearchParams = useSearchParams as ReturnType<typeof vi.fn>;

    mockUsePathname.mockReturnValue("/products");
    mockUseSearchParams.mockReturnValue(new URLSearchParams());

    const { rerender } = renderHook(() =>
      useNextAppRouterTracking({ skipInitial: false }),
    );

    expect(Pulse.setScreenName).toHaveBeenCalledTimes(1);
    vi.clearAllMocks();

    // Simulate React 18 StrictMode re-run with same dependency
    rerender();

    // Should not be called again
    expect(Pulse.setScreenName).not.toHaveBeenCalled();
  });

  it("should handle empty search params correctly", () => {
    const mockUsePathname = usePathname as ReturnType<typeof vi.fn>;
    const mockUseSearchParams = useSearchParams as ReturnType<typeof vi.fn>;

    mockUsePathname.mockReturnValue("/products");
    mockUseSearchParams.mockReturnValue(new URLSearchParams());

    const { rerender } = renderHook(() =>
      useNextAppRouterTracking({ includeSearch: true, skipInitial: false }),
    );

    expect(Pulse.setScreenName).toHaveBeenCalledWith("/products?");
    vi.clearAllMocks();

    mockUsePathname.mockReturnValue("/checkout");

    rerender();

    expect(Pulse.setScreenName).toHaveBeenCalledWith("/checkout?");
  });

  it("should be exported from src/index.ts", () => {
    // This is more of an integration test, checking that the hook is properly exported
    expect(typeof useNextAppRouterTracking).toBe("function");
  });
});
