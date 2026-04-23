/**
 * Consent enum only. Full init surface: {@link PulseWebConfig}, {@link InstrumentationConfig},
 * {@link PulseWebDiskBufferingConfig} in `../config.ts` (package re-exports from `./config`).
 */
export enum PulseDataCollectionConsent {
  ALLOWED = "ALLOWED",
  DENIED = "DENIED",
  PENDING = "PENDING",
}
