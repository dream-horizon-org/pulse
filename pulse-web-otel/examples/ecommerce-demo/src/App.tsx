import React, { lazy, Suspense, useMemo } from "react";
import {
  BrowserRouter,
  Routes,
  Route,
  Link,
  useLocation,
} from "react-router-dom";
import { PulseWeb, PulseDataCollectionConsent } from "@dreamhorizon/pulse-web";
import {
  PulseProvider,
  PulseErrorBoundary,
} from "@dreamhorizon/pulse-web/react";
import { PulseDebugPanel } from "./components/PulseDebugPanel";

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

function AppRoutes() {
  return (
    <>
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
            <div style={{ padding: 32, textAlign: "center", color: "#94a3b8" }}>
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
    </>
  );
}

export default function App() {
  const pulseConfig = useMemo(() => {
    const searchParams = new URLSearchParams(window.location.search);
    const consentParam = searchParams.get("pulse_consent");

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
    const debugLifecycle =
      import.meta.env["VITE_PULSE_DEBUG_LOG_LIFECYCLE"] === "true";

    return {
      apiKey: import.meta.env["VITE_PULSE_API_KEY"] ?? "dev-key",
      serviceName:
        import.meta.env["VITE_PULSE_SERVICE_NAME"] ?? "ecommerce-demo",
      dataCollectionState,
      ...(formatEnv ? { export: { format: formatEnv } } : {}),
      ...(debugLifecycle ? { debugLogRecordLifecycle: true } : {}),
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    // shutdownOnUnmount=false: SDK lives for the full page lifetime.
    // PulseErrorBoundary at root catches any SDK-adjacent render errors.
    <PulseProvider config={pulseConfig} shutdownOnUnmount={false}>
      <PulseErrorBoundary
        fallback={(error) => (
          <div
            style={{
              padding: 32,
              color: "#b91c1c",
              fontFamily: "monospace",
              fontSize: 14,
            }}
          >
            <strong>App error caught by PulseErrorBoundary:</strong>
            <pre>{error.message}</pre>
          </div>
        )}
      >
        <BrowserRouter>
          {/* Expose for E2E shutdown test (m1.spec.ts) */}
          <_PulseWebExpose />
          <AppRoutes />
          <PulseDebugPanel />
        </BrowserRouter>
      </PulseErrorBoundary>
    </PulseProvider>
  );
}

/** Exposes PulseWeb on window for E2E tests. No UI rendered. */
function _PulseWebExpose(): null {
  React.useEffect(() => {
    (window as unknown as Record<string, unknown>)["PulseWeb"] = PulseWeb;
  }, []);
  return null;
}
