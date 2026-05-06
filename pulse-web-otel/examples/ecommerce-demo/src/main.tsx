import React from "react";
import ReactDOM from "react-dom/client";
import { PulseWeb, PulseDataCollectionConsent, PulseLogLevel } from "@dreamhorizon/pulse-web";
import App from "./App";
import { maybeLoadMockPulseSdkConfig } from "./maybeLoadMockPulseSdkConfig";

void maybeLoadMockPulseSdkConfig().then(() => {
  const searchParams = new URLSearchParams(window.location.search);
  const consentParam = searchParams.get("pulse_consent");
  const diskOffQuery = searchParams.get("pulse_disk") === "0";
  const diskOffEnv = import.meta.env["VITE_PULSE_DISK_BUFFER"] === "false";
  const diskBuffering = diskOffQuery || diskOffEnv ? { enabled: false as const } : undefined;
  const formatEnv = import.meta.env["VITE_PULSE_FORMAT"] as "json" | "protobuf" | undefined;
  const serviceVersionRaw = import.meta.env["VITE_PULSE_SERVICE_VERSION"] as string | undefined;
  const serviceVersion = serviceVersionRaw?.trim() || undefined;
  const debugLifecycle = import.meta.env["VITE_PULSE_DEBUG_LOG_LIFECYCLE"] === "true";
  const dataCollectionState =
    consentParam === "denied" ? PulseDataCollectionConsent.DENIED
    : consentParam === "pending" ? PulseDataCollectionConsent.PENDING
    : PulseDataCollectionConsent.ALLOWED;

  PulseWeb.start({
    apiKey: import.meta.env["VITE_PULSE_API_KEY"] ?? "dev-key",
    serviceName: import.meta.env["VITE_PULSE_SERVICE_NAME"] ?? "ecommerce-demo",
    ...(serviceVersion !== undefined ? { serviceVersion } : {}),
    dataCollectionState,
    export: {
      format: (formatEnv ?? "protobuf") as "json" | "protobuf",
    },
    ...(debugLifecycle ? { logLevel: PulseLogLevel.DEBUG } : {}),
    ...(diskBuffering !== undefined ? { diskBuffering } : {}),
  });

  ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
});
