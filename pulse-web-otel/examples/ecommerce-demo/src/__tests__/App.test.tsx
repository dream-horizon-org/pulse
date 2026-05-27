/**
 * Unit tests for ecommerce-demo {@link ../App.tsx} (inside PulseProvider + Router):
 * - PulseRouterEvents drives screen name on SPA navigations.
 * - Demo user bootstrap (setUserId / setUserProperties) from env + URL.
 * - Smoke: NavBar + default route render under MemoryRouter.
 */

import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, act } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";

const mockSetUserId = vi.fn();
const mockSetUserProperties = vi.fn();

vi.mock("@dreamhorizonorg/pulse-web", () => ({
  Pulse: {
    init: vi.fn(),
    shutdown: vi.fn().mockResolvedValue(undefined),
    setScreenName: vi.fn(),
    setUserId: mockSetUserId,
    setUserProperties: mockSetUserProperties,
    trackEvent: vi.fn(),
    isInitialized: vi.fn().mockReturnValue(true),
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

vi.mock("../routes/Home", () => ({
  default: () => React.createElement("div", { "data-testid": "home" }, "Home"),
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
vi.mock("../routes/NetworkLab", () => ({
  default: () =>
    React.createElement("div", { "data-testid": "network-lab" }, "NetworkLab"),
}));
vi.mock("../components/PulseDebugPanel", () => ({
  PulseDebugPanel: () => null,
}));

beforeEach(async () => {
  vi.stubEnv("VITE_PULSE_API_KEY", "vitest-default-pulse-key");
  vi.clearAllMocks();
  vi.resetModules();
  const sdk = await import("../../../../src/sdk");
  vi.spyOn(sdk.Pulse, "setScreenName").mockImplementation(() => {});
  vi.spyOn(sdk.Pulse, "notifySoftNavigation").mockImplementation(() => {});
});

afterEach(() => {
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
});

/**
 * App reads URL search params via `window.location` in a mount-only useMemo,
 * not via `useLocation()`, so align the jsdom URL with MemoryRouter entries.
 */
async function renderApp(initialEntries: string[] = ["/"]) {
  const pathWithSearch = initialEntries[0] ?? "/";
  window.history.replaceState({}, "", pathWithSearch);
  const App = (await import("../App")).default;
  let result: ReturnType<typeof render> | undefined;
  await act(async () => {
    result = render(
      React.createElement(
        MemoryRouter,
        { initialEntries },
        React.createElement(App),
      ),
    );
    await new Promise((r) => setTimeout(r, 50));
  });
  return result!;
}

describe("Demo App — PulseRouterEvents wiring", () => {
  it("calls setScreenName on initial mount when skipInitial=false", async () => {
    await renderApp(["/"]);
    const { Pulse } = await import("../../../../src/sdk");
    expect(Pulse.setScreenName).toHaveBeenCalledWith("/");
  });
});

describe("Demo App — demo user bootstrap", () => {
  it("clears user id when demo user is disabled (default)", async () => {
    vi.stubEnv("VITE_PULSE_DEMO_USER_ENABLED", "false");
    await renderApp(["/"]);
    expect(mockSetUserId).toHaveBeenCalledWith(null);
    expect(mockSetUserProperties).not.toHaveBeenCalled();
  });

  it("sets user id and properties when VITE_PULSE_DEMO_USER_ENABLED=true", async () => {
    vi.stubEnv("VITE_PULSE_DEMO_USER_ENABLED", "true");
    await renderApp(["/"]);
    expect(mockSetUserId).toHaveBeenCalledWith("demo-user-001");
    expect(mockSetUserProperties).toHaveBeenCalledWith(
      expect.objectContaining({
        plan: "pro",
        cohort: "beta",
        region: "us",
      }),
    );
  });

  it("enables demo user when pulse_user_enabled=1 in URL", async () => {
    vi.stubEnv("VITE_PULSE_DEMO_USER_ENABLED", "false");
    await renderApp(["/?pulse_user_enabled=1"]);
    expect(mockSetUserId).toHaveBeenCalledWith("demo-user-001");
  });

  it("uses pulse_user_id query when provided", async () => {
    vi.stubEnv("VITE_PULSE_DEMO_USER_ENABLED", "true");
    await renderApp(["/?pulse_user_id=from-query"]);
    expect(mockSetUserId).toHaveBeenCalledWith("from-query");
  });
});

describe("Demo App — smoke", () => {
  beforeEach(() => {
    vi.spyOn(console, "log").mockImplementation(() => {});
  });

  it("renders NavBar and Home route without crashing", async () => {
    const { getByText, getByTestId } = await renderApp(["/"]);
    expect(getByText("🛍 PulseStore")).toBeTruthy();
    expect(getByTestId("home").textContent).toBe("Home");
  });
});
