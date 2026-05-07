## 0.1.0-alpha.2 (unreleased)

### Breaking changes — public API rename (alpha)

The package is **alpha** (`0.1.0-alpha.x`). Per SemVer, breaking changes in
`0.x` releases are permitted and called out here so the next alpha bump is
unambiguous.

| Old name (≤ 0.1.0-alpha.1) | New name (this release) | Module |
|----------------------------|--------------------------|--------|
| `PulseWeb` (singleton)     | `Pulse`                  | `@dreamhorizonorg/pulse-web` |
| `Pulse.start(config)`      | `Pulse.init(config)`     | `@dreamhorizonorg/pulse-web` |
| `PulseNavigationEvents`    | `PulseRouterEvents`      | `@dreamhorizonorg/pulse-web/react` |

#### Why no compatibility shim

This SDK has not had an external 1.0 (or non-alpha) release; all known
consumers are tracked inside the monorepo (`pulse-web-otel/examples/*`,
`pulse-ui` once integrated) and were migrated in the same commit
(`4cbec8e4c — refactor(web-sdk): rename Pulse API surface across SDK and demos`).
A compat shim would cost public-API noise (deprecated symbols re-exported
forever, plus per-call `console.warn`s) for zero known external upgraders.

#### Migration

```diff
- import { PulseWeb } from "@dreamhorizonorg/pulse-web";
+ import { Pulse }    from "@dreamhorizonorg/pulse-web";

- await PulseWeb.start({ apiKey: "…" });
+ await Pulse.init({ apiKey: "…" });
```

```diff
- import { PulseNavigationEvents } from "@dreamhorizonorg/pulse-web/react";
+ import { PulseRouterEvents }     from "@dreamhorizonorg/pulse-web/react";

- <PulseNavigationEvents />
+ <PulseRouterEvents />
```

A repo-wide `git grep -nE 'PulseWeb\.|PulseNavigationEvents|\.start\('` is
sufficient to verify migration.

### Other changes

- `InstrumentationRegistry.installAll()` no longer flips its single-owner gate
  before installing. Per-instrumentation `install()` calls are now wrapped in
  try/catch (errors logged via `diag.error`) and the gate flips only after the
  full sweep, so a transient throw in one instrumentation no longer silently
  skips the rest on subsequent installs.
- `createPulseSendBeaconTransport`: explicit `BlobPart` cast on the OTLP
  payload to satisfy TS 5.7+ `Uint8Array<ArrayBufferLike>` typing (no runtime
  change).
