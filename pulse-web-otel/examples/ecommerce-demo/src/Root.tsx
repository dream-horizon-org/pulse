import React, { useMemo } from "react";
import {
  PulseDataCollectionConsent,
  PulseLogLevel,
} from "@dreamhorizonorg/pulse-web";
import type {
  InstrumentationConfig,
  PulseWebConfig,
} from "@dreamhorizonorg/pulse-web";
import App from "./App";
import { EcommerceErrorFallback } from "./components/EcommerceErrorFallback";
import { PulseProvider } from "@dreamhorizonorg/pulse-web/react";
import { readManualWebVitalsInstrumentation } from "./read-manual-web-vitals-instrumentation";

const otlpExportFormat: "json" | "protobuf" =
  import.meta.env.VITE_PULSE_FORMAT === "json" ? "json" : "protobuf";

const LOG_LEVEL_MAP: Record<string, PulseLogLevel> = {
  verbose: PulseLogLevel.VERBOSE,
  debug: PulseLogLevel.DEBUG,
  info: PulseLogLevel.INFO,
  warn: PulseLogLevel.WARN,
  error: PulseLogLevel.ERROR,
  none: PulseLogLevel.NONE,
};

/**
 * URL overrides for E2E / manual QA (see e2e/m1.spec.ts, m4-network.spec.ts).
 * Read once per document load — full navigation is required for query changes to apply.
 */
function useDemoUrlPulseOptions(): Pick<
  PulseWebConfig,
  "dataCollectionState" | "instrumentations" | "logLevel"
> {
  return useMemo(() => {
    if (typeof window === "undefined") {
      return { dataCollectionState: PulseDataCollectionConsent.ALLOWED };
    }
    const q = new URLSearchParams(window.location.search);
    const consent = q.get("pulse_consent");
    const dataCollectionState =
      consent === "denied"
        ? PulseDataCollectionConsent.DENIED
        : consent === "pending"
          ? PulseDataCollectionConsent.PENDING
          : PulseDataCollectionConsent.ALLOWED;

    const networkOff =
      q.get("pulse_network_enabled") === "0" ||
      q.get("pulse_network_enabled") === "false";
    const captureQueryParams = q.get("pulse_capture_query") === "1";
    const blockedUrlParam = q.get("pulse_blocked_url");
    const peerHost = q.get("pulse_peer_host");
    const peerService = q.get("pulse_peer_service");
    const propagateCors = q.get("pulse_propagate_cors");
    const captureReqHeadersRaw = q.get("pulse_capture_req_headers");
    const captureReqHeaders = captureReqHeadersRaw
      ? captureReqHeadersRaw.split(",").map((h) => h.trim().toLowerCase())
      : undefined;

    const hasNetworkConfig =
      networkOff ||
      captureQueryParams ||
      Boolean(blockedUrlParam) ||
      Boolean(peerHost && peerService) ||
      Boolean(propagateCors) ||
      Boolean(captureReqHeaders);

    const manualWebVitals = readManualWebVitalsInstrumentation(q);
    let instrumentations: InstrumentationConfig | undefined;
    if (hasNetworkConfig || manualWebVitals !== undefined) {
      instrumentations = {
        ...(manualWebVitals ?? {}),
        ...(hasNetworkConfig
          ? {
              network: {
                enabled: !networkOff,
                ...(captureQueryParams ? { captureQueryParams: true } : {}),
                ...(blockedUrlParam ? { blockedUrls: [blockedUrlParam] } : {}),
                ...(peerHost && peerService
                  ? { peerServiceMap: { [peerHost]: peerService } }
                  : {}),
                ...(propagateCors
                  ? { propagateTraceHeaderCorsUrls: [propagateCors] }
                  : {}),
                ...(captureReqHeaders
                  ? { capturedRequestHeaders: captureReqHeaders }
                  : {}),
              },
            }
          : {}),
      };
    }

    const logLevelRaw = (
      q.get("pulse_log_level") ??
      import.meta.env["VITE_PULSE_LOG_LEVEL"] ??
      ""
    )
      .toString()
      .trim()
      .toLowerCase();
    const logLevel =
      logLevelRaw !== "" ? LOG_LEVEL_MAP[logLevelRaw] : undefined;

    return {
      dataCollectionState,
      ...(instrumentations !== undefined ? { instrumentations } : {}),
      ...(logLevel !== undefined ? { logLevel } : {}),
    };
  }, []);
}

export function Root(): React.ReactElement {
  const urlOpts = useDemoUrlPulseOptions();

  return (
    <PulseProvider
      config={{
        apiKey: import.meta.env.VITE_PULSE_API_KEY!,
        serviceName:
          String(import.meta.env.VITE_PULSE_SERVICE_NAME ?? "").trim() ||
          "my-app",
        dataCollectionState: urlOpts.dataCollectionState,
        logLevel: urlOpts.logLevel ?? PulseLogLevel.DEBUG,
        serviceVersion: "1.0.0",
        export: {
          format: otlpExportFormat,
        },
        ...(urlOpts.instrumentations !== undefined
          ? { instrumentations: urlOpts.instrumentations }
          : {}),
      }}
      shutdownOnUnmount={false}
      errorBoundaryFallback={(error, reset) => (
        <EcommerceErrorFallback
          error={error}
          reset={reset}
          onRecover={() => {}}
        />
      )}
    >
      <App />
    </PulseProvider>
  );
}
