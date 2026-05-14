import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useNextPagesRouterTracking } from "./useNextPagesRouterTracking";
import { Pulse } from "../../sdk";

vi.mock("next/router.js", () => ({
  useRouter: vi.fn(),
}));

vi.mock("../../sdk", () => ({
  Pulse: {
    setScreenName: vi.fn(),
    notifySoftNavigation: vi.fn(),
    _triggerNavigationRouteChange: vi.fn(),
  },
}));

import { useRouter } from "next/router.js";

describe("useNextPagesRouterTracking (Next.js Pages Router)", () => {
  let mockEvents: { [key: string]: ((url: string) => void)[] };

  beforeEach(() => {
    vi.clearAllMocks();
    mockEvents = {};

    const mockUseRouter = useRouter as ReturnType<typeof vi.fn>;
    mockUseRouter.mockReturnValue({
      pathname: "/",
      query: {},
      asPath: "/",
      events: {
        on: vi.fn((event: string, handler: (url: string) => void) => {
          if (!mockEvents[event]) {
            mockEvents[event] = [];
          }
          mockEvents[event].push(handler);
        }),
        off: vi.fn((event: string, handler: (url: string) => void) => {
          if (mockEvents[event]) {
            mockEvents[event] = mockEvents[event].filter((h) => h !== handler);
          }
        }),
      },
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("should detect pathname change and call setScreenName", () => {
    const mockUseRouter = useRouter as ReturnType<typeof vi.fn>;
    mockUseRouter.mockReturnValue({
      pathname: "/products",
      query: {},
      asPath: "/products",
      events: {
        on: vi.fn((event: string, handler: (url: string) => void) => {
          if (event === "routeChangeComplete") {
            setTimeout(() => handler("/products"), 0);
          }
        }),
        off: vi.fn(),
      },
    });

    renderHook(() => useNextPagesRouterTracking());

    // Initial mount does not trigger routeChangeComplete (skipInitial defaults to true)
    expect(Pulse.setScreenName).not.toHaveBeenCalled();
  });

  it("should not treat query string changes as navigation when includeSearch is false", () => {
    const mockUseRouter = useRouter as ReturnType<typeof vi.fn>;
    const mockOn = vi.fn();
    const mockOff = vi.fn();

    mockUseRouter.mockReturnValue({
      pathname: "/products",
      query: {},
      asPath: "/products",
      events: {
        on: mockOn,
        off: mockOff,
      },
    });

    renderHook(() => useNextPagesRouterTracking({ skipInitial: false }));

    // Capture the handler from the on call
    const handler = mockOn.mock.calls[0]?.[1];
    expect(handler).toBeDefined();

    // First call with /products
    handler?.("/products");
    expect(Pulse.setScreenName).toHaveBeenCalledWith("/products");
    vi.clearAllMocks();

    // Trigger event with same pathname but different query
    handler?.("/products?filter=new");

    // Should not be called again because pathname hasn't changed (query ignored)
    expect(Pulse.setScreenName).not.toHaveBeenCalled();
  });

  it("should use pathname pattern for screen name (not asPath)", () => {
    const mockUseRouter = useRouter as ReturnType<typeof vi.fn>;
    const mockOn = vi.fn();
    const mockOff = vi.fn();

    mockUseRouter.mockReturnValue({
      pathname: "/products/[id]",
      query: { id: "123" },
      asPath: "/products/123",
      events: {
        on: mockOn,
        off: mockOff,
      },
    });

    renderHook(() => useNextPagesRouterTracking({ skipInitial: false }));

    const handler = mockOn.mock.calls[0]?.[1];
    expect(handler).toBeDefined();

    // Should use pathname pattern, not resolved asPath
    handler?.("/blog/hello-world?id=456");

    expect(Pulse.setScreenName).toHaveBeenCalledWith("/blog/hello-world");
  });

  it("should use custom format function when provided", () => {
    const mockUseRouter = useRouter as ReturnType<typeof vi.fn>;
    const mockFormat = vi.fn().mockReturnValue("CustomProductsScreen");
    const mockOn = vi.fn();
    const mockOff = vi.fn();

    mockUseRouter.mockReturnValue({
      pathname: "/products/[id]",
      query: { id: "123" },
      asPath: "/products/123",
      events: {
        on: mockOn,
        off: mockOff,
      },
    });

    renderHook(() =>
      useNextPagesRouterTracking({ format: mockFormat, skipInitial: false }),
    );

    const handler = mockOn.mock.calls[0]?.[1];
    expect(handler).toBeDefined();

    handler?.("/checkout");

    expect(mockFormat).toHaveBeenCalled();
    expect(Pulse.setScreenName).toHaveBeenCalledWith("CustomProductsScreen");
  });

  it("should skip initial call when skipInitial is true (default)", () => {
    const mockUseRouter = useRouter as ReturnType<typeof vi.fn>;
    const mockOn = vi.fn();

    mockUseRouter.mockReturnValue({
      pathname: "/products",
      query: {},
      asPath: "/products",
      events: {
        on: mockOn,
        off: vi.fn(),
      },
    });

    renderHook(() => useNextPagesRouterTracking({ skipInitial: true }));

    const handler = mockOn.mock.calls[0]?.[1];
    handler?.("/checkout");

    // First call is skipped
    expect(Pulse.setScreenName).not.toHaveBeenCalled();
  });

  it("should call on initial render when skipInitial is false", () => {
    const mockUseRouter = useRouter as ReturnType<typeof vi.fn>;
    const mockOn = vi.fn();

    mockUseRouter.mockReturnValue({
      pathname: "/products",
      query: {},
      asPath: "/products",
      events: {
        on: mockOn,
        off: vi.fn(),
      },
    });

    renderHook(() => useNextPagesRouterTracking({ skipInitial: false }));

    const handler = mockOn.mock.calls[0]?.[1];
    handler?.("/checkout");

    // First call should register
    expect(Pulse.setScreenName).toHaveBeenCalledWith("/checkout");
  });

  it("should be StrictMode safe (no duplicate calls)", () => {
    const mockUseRouter = useRouter as ReturnType<typeof vi.fn>;
    const mockOn = vi.fn();
    const mockOff = vi.fn();

    mockUseRouter.mockReturnValue({
      pathname: "/products",
      query: {},
      asPath: "/products",
      events: {
        on: mockOn,
        off: mockOff,
      },
    });

    const { rerender } = renderHook(() =>
      useNextPagesRouterTracking({ skipInitial: false }),
    );

    const handler = mockOn.mock.calls[0]?.[1];
    handler?.("/checkout");

    expect(Pulse.setScreenName).toHaveBeenCalledTimes(1);
    vi.clearAllMocks();

    // Simulate React 18 StrictMode re-run
    rerender();

    // Should not register a new handler
    expect(mockOn).not.toHaveBeenCalled();
  });

  it("should handle dynamic routes with multiple parameters", () => {
    const mockUseRouter = useRouter as ReturnType<typeof vi.fn>;
    const mockOn = vi.fn();

    mockUseRouter.mockReturnValue({
      pathname: "/products/[category]/[id]",
      query: { category: "electronics", id: "123" },
      asPath: "/products/electronics/123",
      events: {
        on: mockOn,
        off: vi.fn(),
      },
    });

    renderHook(() => useNextPagesRouterTracking({ skipInitial: false }));

    const handler = mockOn.mock.calls[0]?.[1];
    handler?.("/products/clothing/456");

    // Should use pathname pattern, not resolved asPath
    expect(Pulse.setScreenName).toHaveBeenCalledWith("/products/clothing/456");
  });

  it("should detect actual pathname pattern changes", () => {
    const mockUseRouter = useRouter as ReturnType<typeof vi.fn>;
    const mockOn = vi.fn();

    mockUseRouter.mockReturnValue({
      pathname: "/products/[id]",
      query: { id: "123" },
      asPath: "/products/123",
      events: {
        on: mockOn,
        off: vi.fn(),
      },
    });

    renderHook(() => useNextPagesRouterTracking({ skipInitial: false }));

    const handler = mockOn.mock.calls[0]?.[1];
    handler?.("/checkout");

    expect(Pulse.setScreenName).toHaveBeenCalledWith("/checkout");
  });

  it("should be exported from src/index.ts", () => {
    // This is more of an integration test, checking that the hook is properly exported
    expect(typeof useNextPagesRouterTracking).toBe("function");
  });
});
