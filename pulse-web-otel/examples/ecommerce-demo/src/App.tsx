import React, { lazy, Suspense, useEffect, useMemo } from "react";
import {
  BrowserRouter,
  Routes,
  Route,
  Link,
  useLocation,
} from "react-router-dom";
import {
  PulseWeb,
  PulseDataCollectionConsent,
  PulseLogLevel,
} from "@dreamhorizon/pulse-web";
import { PulseProvider } from "@dreamhorizon/pulse-web/react";
import { PulseDebugPanel } from "./components/PulseDebugPanel";
import { CartProvider } from "./hooks/useCart";

const Home = lazy(() => import("./routes/Home"));
const Products = lazy(() => import("./routes/Products"));
const ProductDetail = lazy(() => import("./routes/ProductDetail"));
const Cart = lazy(() => import("./routes/Cart"));
const Checkout = lazy(() => import("./routes/Checkout"));
const ErrorDemo = lazy(() => import("./routes/ErrorDemo"));

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
        {link("/error-demo", "Error Demo")}
      </nav>
    </header>
  );
}

export default function App() {
  const pulseConfig = useMemo(() => {
    const searchParams = new URLSearchParams(window.location.search);
    const consentParam = searchParams.get("pulse_consent");
    const queryLogLevel = searchParams.get("pulse_log_level");
    const queryUserEnabled = searchParams.get("pulse_user_enabled");
    const queryUserId = searchParams.get("pulse_user_id");

    // Disk buffering defaults on (Android parity). Opt out with ?pulse_disk=0 or VITE_PULSE_DISK_BUFFER=false.
    const diskOffQuery = searchParams.get("pulse_disk") === "0";
    const diskOffEnv = import.meta.env["VITE_PULSE_DISK_BUFFER"] === "false";
    const diskBuffering =
      diskOffQuery || diskOffEnv ? { enabled: false as const } : undefined;

    const dataCollectionState =
      consentParam === "denied"
        ? PulseDataCollectionConsent.DENIED
        : consentParam === "pending"
          ? PulseDataCollectionConsent.PENDING
          : PulseDataCollectionConsent.ALLOWED;

    const formatEnv = import.meta.env["VITE_PULSE_FORMAT"] as
      | "json"
      | "protobuf"
      | undefined;
    const logLevelRaw = (
      queryLogLevel ??
      import.meta.env["VITE_PULSE_LOG_LEVEL"] ??
      ""
    )
      .toString()
      .trim()
      .toLowerCase();
    const legacyDebugLifecycle =
      import.meta.env["VITE_PULSE_DEBUG_LOG_LIFECYCLE"] === "true";
    const logLevelMap: Record<string, PulseLogLevel> = {
      verbose: PulseLogLevel.VERBOSE,
      debug: PulseLogLevel.DEBUG,
      info: PulseLogLevel.INFO,
      warn: PulseLogLevel.WARN,
      error: PulseLogLevel.ERROR,
      none: PulseLogLevel.NONE,
    };
    const logLevel =
      logLevelMap[logLevelRaw] ??
      (legacyDebugLifecycle ? PulseLogLevel.DEBUG : undefined);

    const serviceVersionRaw = import.meta.env["VITE_PULSE_SERVICE_VERSION"] as
      | string
      | undefined;
    const serviceVersion =
      serviceVersionRaw && String(serviceVersionRaw).trim() !== ""
        ? String(serviceVersionRaw).trim()
        : undefined;

    return {
      apiKey:
        import.meta.env["VITE_PULSE_API_KEY"] ?? "default-project_devkey01",
      serviceName:
        import.meta.env["VITE_PULSE_SERVICE_NAME"] ?? "ecommerce-demo",
      ...(serviceVersion !== undefined ? { serviceVersion } : {}),
      dataCollectionState,
      export: {
        format: (formatEnv ?? ("protobuf" as const)) as "json" | "protobuf",
        compression:
          (import.meta.env["VITE_PULSE_COMPRESSION"] as
            | "gzip"
            | "none"
            | undefined) ?? "gzip",
        batch: {
          scheduledDelayMillis: import.meta.env["VITE_PULSE_BATCH_DELAY_MS"]
            ? Number(import.meta.env["VITE_PULSE_BATCH_DELAY_MS"])
            : 5000,
        },
      },
      debugLogRecordLifecycle: legacyDebugLifecycle,
      ...(logLevel !== undefined ? { logLevel } : {}),
      ...(diskBuffering !== undefined ? { diskBuffering } : {}),
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
      <PulseProvider
        config={pulseConfig}
        shutdownOnUnmount={false}
        routerTracking={{ skipInitial: false }}
      >
        {/* Expose for E2E shutdown test (m1.spec.ts) */}
        <_PulseWebExpose />
        <_PulseWebDemoUserSetup config={userSetupConfig} />
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
                <Route path="/error-demo" element={<ErrorDemo />} />
              </Routes>
            </Suspense>
          </main>
          <PulseDebugPanel />
        </CartProvider>
      </PulseProvider>
    </BrowserRouter>
  );
}

/** Exposes PulseWeb on window for E2E tests. No UI rendered. */
function _PulseWebExpose(): null {
  React.useEffect(() => {
    (window as unknown as Record<string, unknown>)["PulseWeb"] = PulseWeb;
  }, []);
  return null;
}

type DemoUserSetupConfig = {
  enabled: boolean;
  userId: string;
  userProps: Record<string, string | null>;
};

function _PulseWebDemoUserSetup({
  config,
}: {
  config: DemoUserSetupConfig;
}): null {
  useEffect(() => {
    if (config.enabled) {
      PulseWeb.setUserId(config.userId);
      PulseWeb.setUserProperties(config.userProps);
    } else {
      PulseWeb.setUserId(null);
    }
  }, [config.enabled, config.userId, config.userProps]);

  return null;
}
