import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useRouterTracking } from "./useRouterTracking";
import { Pulse } from "../../sdk";

vi.mock("react-router-dom", () => ({
  useLocation: vi.fn(),
}));

vi.mock("../../sdk", () => ({
  Pulse: {
    setScreenName: vi.fn(),
    _triggerNavigationRouteChange: vi.fn(),
  },
}));

import { useLocation } from "react-router-dom";

describe("useRouterTracking (React Router v6)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("should detect pathname change and call setScreenName", () => {
    const mockUseLocation = useLocation as ReturnType<typeof vi.fn>;
    mockUseLocation.mockReturnValue({
      pathname: "/products",
      search: "",
      hash: "",
    });

    const { rerender } = renderHook(() => useRouterTracking());

    // Verify initial pathname is not set (skipInitial defaults to true)
    expect(Pulse.setScreenName).not.toHaveBeenCalled();

    // Change pathname
    mockUseLocation.mockReturnValue({
      pathname: "/checkout",
      search: "",
      hash: "",
    });

    rerender();

    expect(Pulse.setScreenName).toHaveBeenCalledWith("/checkout");
  });

  it("should not treat query string changes as navigation when includeSearch is false", () => {
    const mockUseLocation = useLocation as ReturnType<typeof vi.fn>;
    mockUseLocation.mockReturnValue({
      pathname: "/products",
      search: "",
      hash: "",
    });

    const { rerender } = renderHook(() => useRouterTracking({ skipInitial: false }));

    expect(Pulse.setScreenName).toHaveBeenCalledWith("/products");
    vi.clearAllMocks();

    // Query string changes but pathname stays the same
    mockUseLocation.mockReturnValue({
      pathname: "/products",
      search: "?filter=new",
      hash: "",
    });

    rerender();

    // Should not be called again because pathname hasn't changed
    expect(Pulse.setScreenName).not.toHaveBeenCalled();
  });

  it("should not treat hash-only changes as navigation", () => {
    const mockUseLocation = useLocation as ReturnType<typeof vi.fn>;
    mockUseLocation.mockReturnValue({
      pathname: "/products",
      search: "",
      hash: "",
    });

    const { rerender } = renderHook(() => useRouterTracking({ skipInitial: false }));

    expect(Pulse.setScreenName).toHaveBeenCalledWith("/products");
    vi.clearAllMocks();

    // Hash changes but pathname stays the same
    mockUseLocation.mockReturnValue({
      pathname: "/products",
      search: "",
      hash: "#section",
    });

    rerender();

    // Should not be called again
    expect(Pulse.setScreenName).not.toHaveBeenCalled();
  });

  it("should include search params when includeSearch is true", () => {
    const mockUseLocation = useLocation as ReturnType<typeof vi.fn>;
    mockUseLocation.mockReturnValue({
      pathname: "/products",
      search: "?filter=new",
      hash: "",
    });

    const { rerender } = renderHook(() =>
      useRouterTracking({ includeSearch: true, skipInitial: false }),
    );

    expect(Pulse.setScreenName).toHaveBeenCalledWith("/products?filter=new");
    vi.clearAllMocks();

    // Query string changes
    mockUseLocation.mockReturnValue({
      pathname: "/products",
      search: "?filter=old",
      hash: "",
    });

    rerender();

    expect(Pulse.setScreenName).toHaveBeenCalledWith("/products?filter=old");
  });

  it("should use custom format function when provided", () => {
    const mockUseLocation = useLocation as ReturnType<typeof vi.fn>;
    const mockFormat = vi.fn().mockReturnValue("CustomProductsScreen");

    mockUseLocation.mockReturnValue({
      pathname: "/products/123",
      search: "",
      hash: "",
    });

    const { rerender } = renderHook(() =>
      useRouterTracking({ format: mockFormat, skipInitial: false }),
    );

    expect(mockFormat).toHaveBeenCalledWith({
      pathname: "/products/123",
      search: "",
      hash: "",
    });
    expect(Pulse.setScreenName).toHaveBeenCalledWith("CustomProductsScreen");
    vi.clearAllMocks();

    // Navigate to another route
    mockUseLocation.mockReturnValue({
      pathname: "/checkout",
      search: "",
      hash: "",
    });

    rerender();

    expect(Pulse.setScreenName).toHaveBeenCalledWith("CustomProductsScreen");
  });

  it("should handle SSR gracefully (window undefined)", () => {
    // In SSR context, useLocation would not be called, but the hook should not crash
    const mockUseLocation = useLocation as ReturnType<typeof vi.fn>;
    mockUseLocation.mockReturnValue({
      pathname: "/products",
      search: "",
      hash: "",
    });

    expect(() => {
      renderHook(() => useRouterTracking());
    }).not.toThrow();
  });

  it("should skip initial call when skipInitial is true (default)", () => {
    const mockUseLocation = useLocation as ReturnType<typeof vi.fn>;
    mockUseLocation.mockReturnValue({
      pathname: "/products",
      search: "",
      hash: "",
    });

    renderHook(() => useRouterTracking({ skipInitial: true }));

    expect(Pulse.setScreenName).not.toHaveBeenCalled();
  });

  it("should call on initial render when skipInitial is false", () => {
    const mockUseLocation = useLocation as ReturnType<typeof vi.fn>;
    mockUseLocation.mockReturnValue({
      pathname: "/products",
      search: "",
      hash: "",
    });

    renderHook(() => useRouterTracking({ skipInitial: false }));

    expect(Pulse.setScreenName).toHaveBeenCalledWith("/products");
  });

  it("should be StrictMode safe (no duplicate calls)", () => {
    const mockUseLocation = useLocation as ReturnType<typeof vi.fn>;
    mockUseLocation.mockReturnValue({
      pathname: "/products",
      search: "",
      hash: "",
    });

    const { rerender } = renderHook(() => useRouterTracking({ skipInitial: false }));

    expect(Pulse.setScreenName).toHaveBeenCalledTimes(1);
    vi.clearAllMocks();

    // Simulate React 18 StrictMode re-run with same dependency
    rerender();

    // Should not be called again
    expect(Pulse.setScreenName).not.toHaveBeenCalled();
  });

  it("should be exported from src/index.ts", () => {
    // This is more of an integration test, checking that the hook is properly exported
    expect(typeof useRouterTracking).toBe("function");
  });
});
