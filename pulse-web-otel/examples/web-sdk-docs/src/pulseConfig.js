import {
  PulseDataCollectionConsent,
  PulseLogLevel,
} from "@dreamhorizon/pulse-web";

/**
 * @returns {import("@dreamhorizon/pulse-web").PulseWebConfig}
 */
export function buildPulseConfig() {
  const searchParams = new URLSearchParams(window.location.search);
  const consentParam = searchParams.get("pulse_consent");
  const dataCollectionState =
    consentParam === "denied"
      ? PulseDataCollectionConsent.DENIED
      : consentParam === "pending"
        ? PulseDataCollectionConsent.PENDING
        : PulseDataCollectionConsent.ALLOWED;

  const formatEnv = import.meta.env["VITE_PULSE_FORMAT"];
  const logLevelRaw = (
    searchParams.get("pulse_log_level") ??
    import.meta.env["VITE_PULSE_LOG_LEVEL"] ??
    ""
  )
    .toString()
    .trim()
    .toLowerCase();
  const logLevelMap = {
    verbose: PulseLogLevel.VERBOSE,
    debug: PulseLogLevel.DEBUG,
    info: PulseLogLevel.INFO,
    warn: PulseLogLevel.WARN,
    error: PulseLogLevel.ERROR,
    none: PulseLogLevel.NONE,
  };
  const logLevel = logLevelMap[logLevelRaw];

  const diskOffQuery = searchParams.get("pulse_disk") === "0";
  const diskOffEnv = import.meta.env["VITE_PULSE_DISK_BUFFER"] === "false";
  const diskBuffering =
    diskOffQuery || diskOffEnv ? { enabled: false } : undefined;

  const batchDelayRaw = import.meta.env["VITE_PULSE_BATCH_DELAY_MS"];
  const scheduledDelayMillis = batchDelayRaw ? Number(batchDelayRaw) : 5000;

  return {
    apiKey: import.meta.env["VITE_PULSE_API_KEY"] ?? "default-project_devkey01",
    serviceName: import.meta.env["VITE_PULSE_SERVICE_NAME"] ?? "web-sdk-docs",
    dataCollectionState,
    export: {
      format: (formatEnv ?? "protobuf") === "json" ? "json" : "protobuf",
      compression:
        import.meta.env["VITE_PULSE_COMPRESSION"] === "none" ? "none" : "gzip",
      batch: {
        scheduledDelayMillis,
      },
    },
    ...(logLevel !== undefined ? { logLevel } : {}),
    ...(diskBuffering !== undefined ? { diskBuffering } : {}),
    instrumentations: {
      session: { enabled: true },
      webVitals: { enabled: true },
      interactions: { enabled: true },
      clicks: { enabled: true },
      network: { enabled: true },
      errors: { enabled: true },
    },
  };
}
