// M1: Consent guard — returns true if signals should be emitted.
// PulseDataCollectionConsent.DENIED → always returns false.
// See: docs/sdk-core/config-and-consent/SPEC.md (consent)

import { PulseDataCollectionConsent } from "./config";

export function isDataCollectionAllowed(
  state?: PulseDataCollectionConsent,
): boolean {
  // DENIED → false; ALLOWED or undefined → true; PENDING → false
  if (!state || state === PulseDataCollectionConsent.ALLOWED) return true;
  return false;
}
