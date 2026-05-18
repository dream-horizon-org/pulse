export { Pulse } from "./sdk";
export type {
  PulseWebConfig,
  PulseWebDiskBufferingConfig,
  InstrumentationConfig,
  PulseBeforeSendResult,
  PulseExportSignal,
  PulseWebBeforeSendCallbacks,
  PulseWebBeforeSendConfig,
} from "./config";
export { PulseDataCollectionConsent, PulseLogLevel } from "./config";
export { SDK_VERSION } from "./version";
export { PulseWebSemconv } from "./semconv";
/** Google CWV rating boundary tuples from the pinned `web-vitals` major — for host UI parity with `Metric.rating` (see `docs/instrumentations/web-vitals/SPEC.md` §5.3). */
export {
  CLSThresholds,
  FCPThresholds,
  INPThresholds,
  LCPThresholds,
  TTFBThresholds,
} from "web-vitals";
