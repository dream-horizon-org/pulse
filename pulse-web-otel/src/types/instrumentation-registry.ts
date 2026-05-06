import type { Logger } from "@opentelemetry/api-logs";
import type { Tracer } from "@opentelemetry/api";

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
}

export interface PulseInstrumentation {
  readonly name: string;
  install(sdk: SdkContext): void;
  uninstall(): void;
}
