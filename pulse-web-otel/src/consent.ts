// M1: Consent guard — returns true if signals should be emitted.
// PulseDataCollectionConsent.DENIED → always returns false.
// See: docs/sdk-core/config-and-public-api/SPEC.md (consent — §5.2)

import { PulseDataCollectionConsent } from "./config";

export function isDataCollectionAllowed(
  state?: PulseDataCollectionConsent,
): boolean {
  // DENIED → false; ALLOWED or undefined → true; PENDING → false
  if (!state || state === PulseDataCollectionConsent.ALLOWED) return true;
  return false;
}
