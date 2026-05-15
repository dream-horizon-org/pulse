import React, {
  lazy,
  Suspense,
  useEffect,
  useLayoutEffect,
  useMemo,
  useState,
} from "react";
import { Routes, Route, Link, useLocation } from "react-router-dom";
import {
  Pulse,
  PulseDataCollectionConsent,
  PulseLogLevel,
} from "@dreamhorizonorg/pulse-web";
import { PulseRouterEvents } from "@dreamhorizonorg/pulse-web/react/router";
import { PulseDebugPanel } from "./components/PulseDebugPanel";
import { ScreenNavigationLogger } from "./components/ScreenNavigationLogger";
import { WebVitalsStressHarness } from "./components/WebVitalsStressHarness";
import { CartProvider } from "./hooks/useCart";

const Home = lazy(() => import("./routes/Home"));
const Products = lazy(() => import("./routes/Products"));
const ProductDetail = lazy(() => import("./routes/ProductDetail"));
const Cart = lazy(() => import("./routes/Cart"));
const Checkout = lazy(() => import("./routes/Checkout"));
const ErrorDemo = lazy(() => import("./routes/ErrorDemo"));
const NetworkLab = lazy(() => import("./routes/NetworkLab"));

function NavBar() {
  const location = useLocation();
  const link = (to: string, label: string) => (
    <Link
      to={to}
      style={{
        color: location.pathname === to ? "#4f46e5" : "#64748b",
        textDecoration: "none",
        fontWeight: location.pathname === to ? 700 : 400,
        padding: "4px 0",
      }}
    >
      {label}
    </Link>
  );
  return (
    <header
      style={{
        background: "#fff",
        borderBottom: "1px solid #e2e8f0",
        padding: "0 24px",
      }}
    >
      <nav
        style={{
          maxWidth: 1200,
          margin: "0 auto",
          display: "flex",
          alignItems: "center",
          gap: 28,
          height: 56,
        }}
      >
        <span
          style={{
            fontWeight: 800,
            fontSize: 18,
            color: "#4f46e5",
            marginRight: 8,
          }}
        >
          🛍 PulseStore
        </span>
        {link("/", "Home")}
        {link("/products", "Products")}
        {link("/cart", "Cart")}
        {link("/checkout", "Checkout")}
        {link("/network-lab", "Network Lab")}
        {link("/error-demo", "Error Demo")}
      </nav>
    </header>
  );
}

export default function App() {
  const [errorLabKey, setErrorLabKey] = useState(0);

  // Legacy pulseConfig useMemo (superseded): live URL overrides live in Root.tsx
  // (`useDemoUrlPulseOptions` + read-manual-web-vitals-instrumentation.ts).


  const userSetupConfig = useMemo(() => {
    const searchParams = new URLSearchParams(window.location.search);
    const queryUserEnabled = searchParams.get("pulse_user_enabled");
    const queryUserId = searchParams.get("pulse_user_id");
    const envUserEnabled =
      String(import.meta.env["VITE_PULSE_DEMO_USER_ENABLED"] ?? "")
        .trim()
        .toLowerCase() === "true";

    const enabled =
      queryUserEnabled == null
        ? envUserEnabled
        : queryUserEnabled === "1" || queryUserEnabled === "true";

    return {
      enabled,
      userId:
        (queryUserId && queryUserId.trim() !== "" ? queryUserId : undefined) ??
        (import.meta.env["VITE_PULSE_DEMO_USER_ID"] as string | undefined) ??
        "demo-user-001",
      userProps: {
        plan:
          (import.meta.env["VITE_PULSE_DEMO_USER_PLAN"] as
            | string
            | undefined) ?? "pro",
        cohort:
          (import.meta.env["VITE_PULSE_DEMO_USER_COHORT"] as
            | string
            | undefined) ?? "beta",
        region:
          (import.meta.env["VITE_PULSE_DEMO_USER_REGION"] as
            | string
            | undefined) ?? "us",
      } as Record<string, string | null>,
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <>
      {/* Expose for E2E shutdown test (m1.spec.ts) */}
      <_PulseExpose />
      <PulseRouterEvents skipInitial={false} />
      <ScreenNavigationLogger />
      <_PulseDemoUserSetup config={userSetupConfig} />
      <CartProvider>
        <NavBar />
        <main
          style={{
            maxWidth: 1200,
            margin: "0 auto",
            padding: "32px 24px",
            minHeight: "calc(100vh - 56px)",
          }}
        >
          <WebVitalsStressHarness>
            <Suspense
              fallback={
                <div
                  style={{
                    padding: 32,
                    textAlign: "center",
                    color: "#94a3b8",
                  }}
                >
                  Loading…
                </div>
              }
            >
              <Routes>
                <Route path="/" element={<Home />} />
                <Route path="/products" element={<Products />} />
                <Route path="/products/:id" element={<ProductDetail />} />
                <Route path="/cart" element={<Cart />} />
                <Route path="/checkout" element={<Checkout />} />
                <Route path="/network-lab" element={<NetworkLab />} />
                <Route
                  path="/error-demo"
                  element={<ErrorDemo key={errorLabKey} />}
                />
              </Routes>
            </Suspense>
          </WebVitalsStressHarness>
        </main>
        <PulseDebugPanel />
      </CartProvider>
    </>
  );
}

/** Exposes `Pulse` on window for E2E tests. No UI rendered. */
function _PulseExpose(): null {
  useLayoutEffect(() => {
    (window as unknown as Record<string, unknown>)["Pulse"] = Pulse;
  }, []);
  return null;
}

type DemoUserSetupConfig = {
  enabled: boolean;
  userId: string;
  userProps: Record<string, string | null>;
};

function _PulseDemoUserSetup({
  config,
}: {
  config: DemoUserSetupConfig;
}): null {
  useEffect(() => {
    if (config.enabled) {
      Pulse.setUserId(config.userId);
      Pulse.setUserProperties(config.userProps);
    } else {
      Pulse.setUserId(null);
    }
  }, [config.enabled, config.userId, config.userProps]);

  return null;
}
