import React, { lazy, Suspense, useEffect, useMemo, useState } from "react";
import {
  BrowserRouter,
  Routes,
  Route,
  Link,
  useLocation,
} from "react-router-dom";
import {
  Pulse,
  PulseDataCollectionConsent,
  PulseLogLevel,
} from "@dreamhorizonorg/pulse-web";
import { PulseProvider } from "@dreamhorizonorg/pulse-web/react";
import { PulseRouterEvents } from "@dreamhorizonorg/pulse-web/react/router";
import { PulseDebugPanel } from "./components/PulseDebugPanel";
import { ScreenNavigationLogger } from "./components/ScreenNavigationLogger";
import { EcommerceErrorFallback } from "./components/EcommerceErrorFallback";
import { CartProvider } from "./hooks/useCart";

/**
 * Manual Web Vitals QA: optional local disable via env or URL (`pulse_wv_enabled`, `VITE_PULSE_WEB_VITALS_ENABLED`).
 * FCP/FID/TTFB register with other vitals whenever instrumentation installs — no separate demo knobs.
 * Remote `web_vitals` gate still comes from SDK config (mock JSON / server), not from here.
 */
type ManualWebVitalsInstrumentation = {
  webVitals: {
    enabled?: boolean;
  };
};

function readManualWebVitalsInstrumentation(
  searchParams: URLSearchParams,
): ManualWebVitalsInstrumentation | undefined {
  const q = (key: string): string | null => searchParams.get(key);
  const truthy = (v: string | null): boolean =>
    v === "1" || v === "true" || v === "yes";
  const falsy = (v: string | null): boolean => v === "0" || v === "false";

  let enabled: boolean | undefined;
  let touchedEnabled = false;
  if (falsy(q("pulse_wv_enabled"))) {
    enabled = false;
    touchedEnabled = true;
  } else if (import.meta.env["VITE_PULSE_WEB_VITALS_ENABLED"] === "false") {
    enabled = false;
    touchedEnabled = true;
  } else if (truthy(q("pulse_wv_enabled"))) {
    enabled = true;
    touchedEnabled = true;
  }

  if (!touchedEnabled) {
    return undefined;
  }

  return { webVitals: { enabled } };
}

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

  // const pulseConfig = useMemo(() => {
  //   const searchParams = new URLSearchParams(window.location.search);
  //   const consentParam = searchParams.get("pulse_consent");
  //   const queryLogLevel = searchParams.get("pulse_log_level");

  //   // Disk buffering defaults on (Android parity). Opt out with ?pulse_disk=0 or VITE_PULSE_DISK_BUFFER=false.
  //   const diskOffQuery = searchParams.get("pulse_disk") === "0";
  //   const diskOffEnv = import.meta.env["VITE_PULSE_DISK_BUFFER"] === "false";
  //   const diskBuffering =
  //     diskOffQuery || diskOffEnv ? { enabled: false as const } : undefined;

  //   const dataCollectionState =
  //     consentParam === "denied"
  //       ? PulseDataCollectionConsent.DENIED
  //       : consentParam === "pending"
  //         ? PulseDataCollectionConsent.PENDING
  //         : PulseDataCollectionConsent.ALLOWED;

  //   const formatEnv = import.meta.env["VITE_PULSE_FORMAT"] as
  //     | "json"
  //     | "protobuf"
  //     | undefined;
  //   const logLevelRaw = (
  //     queryLogLevel ??
  //     import.meta.env["VITE_PULSE_LOG_LEVEL"] ??
  //     ""
  //   )
  //     .toString()
  //     .trim()
  //     .toLowerCase();
  //   const logLevelMap: Record<string, PulseLogLevel> = {
  //     verbose: PulseLogLevel.VERBOSE,
  //     debug: PulseLogLevel.DEBUG,
  //     info: PulseLogLevel.INFO,
  //     warn: PulseLogLevel.WARN,
  //     error: PulseLogLevel.ERROR,
  //     none: PulseLogLevel.NONE,
  //   };
  //   const logLevel = logLevelMap[logLevelRaw];

  //   const serviceVersionRaw = import.meta.env["VITE_PULSE_SERVICE_VERSION"] as
  //     | string
  //     | undefined;
  //   const serviceVersion =
  //     serviceVersionRaw && String(serviceVersionRaw).trim() !== ""
  //       ? String(serviceVersionRaw).trim()
  //       : undefined;

  //   const manualInstrumentations =
  //     readManualWebVitalsInstrumentation(searchParams);
  //   /** E2E: `?pulse_network_enabled=0` disables network instrumentation while remote gate may stay on. */
  //   const pulseNetworkDisabled =
  //     searchParams.get("pulse_network_enabled") === "0" ||
  //     searchParams.get("pulse_network_enabled") === "false";
  //   const instrumentationsPartial =
  //     manualInstrumentations !== undefined || pulseNetworkDisabled
  //       ? {
  //           ...(manualInstrumentations ?? {}),
  //           ...(pulseNetworkDisabled
  //             ? { network: { enabled: false as const } }
  //             : {}),
  //         }
  //       : undefined;

  //   const apiKey = import.meta.env["VITE_PULSE_API_KEY"];
  //   if (!apiKey) {
  //     throw new Error(
  //       "Missing VITE_PULSE_API_KEY for ecommerce-demo Pulse integration",
  //     );
  //   }

  //   return {
  //     apiKey,
  //     serviceName:
  //       import.meta.env["VITE_PULSE_SERVICE_NAME"] ?? "ecommerce-demo",
  //     ...(serviceVersion !== undefined ? { serviceVersion } : {}),
  //     dataCollectionState,
  //     export: {
  //       format: (formatEnv ?? ("protobuf" as const)) as "json" | "protobuf",
  //     },
  //     ...(logLevel !== undefined ? { logLevel } : {}),
  //     ...(diskBuffering !== undefined ? { diskBuffering } : {}),
  //     ...(instrumentationsPartial !== undefined
  //       ? { instrumentations: instrumentationsPartial }
  //       : {}),
  //   };
  //   // eslint-disable-next-line react-hooks/exhaustive-deps
  // }, []);

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
    <BrowserRouter>
        {/* Expose for E2E shutdown test (m1.spec.ts) */}
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
            <Suspense
              fallback={
                <div
                  style={{ padding: 32, textAlign: "center", color: "#94a3b8" }}
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
          </main>
          <PulseDebugPanel />
        </CartProvider>
    </BrowserRouter>
  );
}

/** Exposes `Pulse` on window for E2E tests. No UI rendered. */
function _PulseExpose(): null {
  React.useEffect(() => {
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
