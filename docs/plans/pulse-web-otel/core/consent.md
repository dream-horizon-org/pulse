# core/consent

## 1. Purpose

Gate every export on the user's data-collection consent. PENDING and DENIED suppress all signals; ALLOWED (or unset) lets data through.

## 2. Source location

- `pulse-web-otel/src/consent.ts` — `isDataCollectionAllowed`
- `pulse-web-otel/src/types/config.ts` — `PulseDataCollectionConsent` enum
- Used in `src/sdk.ts` (`setConsent`, exporter gates) and at processor / exporter layers

## 3. Public surface

```ts
export enum PulseDataCollectionConsent { ALLOWED, PENDING, DENIED }
export function isDataCollectionAllowed(state?: PulseDataCollectionConsent): boolean;
```

Plus `Pulse.setConsent(state)` on the SDK.

## 4. Internal design

```ts
if (!state || state === PulseDataCollectionConsent.ALLOWED) return true;
return false; // PENDING | DENIED
```

Behaviour:

- Default (unset) → true, so apps not using the consent enum are unaffected.
- PENDING acts as a soft block — host app calls `setConsent(ALLOWED)` later; signals captured before that point are not retroactively flushed.
- DENIED is permanent for the session; combined with `setUserId`/property clearing, the host app can build a "do not track" toggle.

## 5. Dependencies

`types/config.ts` only.

## 6. Data contracts

No attributes of its own; controls whether downstream signals (`session.start`, `app.click`, …) ever leave the device.

## 7. Tests

- Covered indirectly by `src/__tests__/sdk-public-methods.test.ts` and `sdk-lifecycle.test.ts`.

## 8. History / decisions

See `pulse-web-otel/docs/instrumentations/sdk-core/SPEC.md` § consent. The "absent state ⇒ allowed" choice was deliberate to keep the SDK opt-out, mirroring Android.

## 9. Rebuild recipe

1. Define the three-value enum.
2. Implement the one-line guard above.
3. Call it at the exporter `export()` entrypoint and at every fire-and-forget log emission.
