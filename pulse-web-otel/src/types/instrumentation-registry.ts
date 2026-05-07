import type { Logger } from "@opentelemetry/api-logs";
import type { Tracer } from "@opentelemetry/api";
import type { LoggerProvider } from "@opentelemetry/sdk-logs";
import type { WebTracerProvider } from "@opentelemetry/sdk-trace-web";

import type { PulseWebConfig } from "../config";
import type { FeatureGate } from "../feature-gate";
import type { PulseGlobalAttributesProcessor } from "../processors/global-attrs-processor";
import type { SessionProvider } from "../session";

export interface SdkContext {
  endpointBaseUrl: string;
  gate: FeatureGate;
  sessionProvider: SessionProvider;
  logger: Logger;
  tracer: Tracer;
  config: PulseWebConfig;
  globalAttrsProcessor: PulseGlobalAttributesProcessor;
  /** For instrumentations that must flush logs between batches (e.g. Web Vitals). */
  loggerProvider?: LoggerProvider;
  /** OTel SDK tracer provider — required for Fetch / XHR auto-instrumentation registration. */
  tracerProvider?: WebTracerProvider;
}

export interface PulseInstrumentation {
  readonly name: string;
  install(sdk: SdkContext): void;
  uninstall(): void;
}
