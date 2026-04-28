// M1: Consent guard — returns true if signals should be emitted.
// PulseDataCollectionConsent.DENIED → always returns false.
// See: web-sdk-plan/v1/01-foundation/scaffold.md

import { PulseDataCollectionConsent } from './config';

export function isDataCollectionAllowed(state?: PulseDataCollectionConsent): boolean {
  // DENIED → false; ALLOWED or undefined → true; PENDING → false
  if (!state || state === PulseDataCollectionConsent.ALLOWED) return true;
  return false;
}
