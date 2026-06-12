import type { ParsedUA } from "./ua";

/** OTLP signal kind for Pulse remote sampling conditions (Android `PulseSignalScope`). */
export type PulseSignalScope = "LOGS" | "TRACES" | "METRICS";

/** Constructor options for {@link ExportSamplingGate}. */
export type ExportSamplingGateInit = {
  /** Same string as OTEL resource `service.version` (PulseWebConfig.serviceVersion). */
  serviceVersion?: string;
};

/** Values aligned with RUM resource / global attrs where applicable (snapshot at match time). */
export interface SessionSamplingRuleMatchContext {
  serviceVersion: string;
  parsedUa: ParsedUA;
  networkType: string;
  networkEffectiveType: string;
}
