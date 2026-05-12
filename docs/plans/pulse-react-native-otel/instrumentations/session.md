# RN · Session

Emits `session.start` / `session.end` and propagates `session.id` onto every signal.

Brief: [../../../components/pulse-react-native-otel.md](../../../components/pulse-react-native-otel.md) · Peers: [../core/semconv](../core/semconv.md), [screen](./screen.md).

## Source location

- `pulse-react-native-otel/src/sessionState.ts` — in-memory + AsyncStorage persistence of session id.
- `pulse-react-native-otel/src/user.ts` — user id → session attribution.

## Public surface

- `Pulse.setUser(user | null)` — ties user to the active session.
- Lifecycle is automatic; no manual start/end API.

## Internal design

1. On `init` (or app foreground if no session): create UUIDv7, persist, emit `session.start`.
2. Listen for `AppState.addEventListener('change', ...)`:
   - `background` → arm 30-min idle timer.
   - `active` within window → keep same session.
3. On idle expiry → emit `session.end` with `duration_ms` + drop session id.
4. `session.id` merged into `globalAttributes` (see [../core/facade.md](../core/facade.md)) so every downstream span/log carries it.

## Data contracts

| Signal | `pulse.type` | Attrs |
|---|---|---|
| start | `session.start` | `session.id`, `user.id?`, `platform=react-native`, `app.build_name` |
| end | `session.end` | `session.id`, `session.duration_ms` |
| all others | — | `session.id` injected |

## Tests

`src/__tests__/sessionState.test.ts` — tests cold start, backgrounding, idle expiry, setUser attribution.

## History / decisions

30-min idle window matches other SDKs. AsyncStorage used for persistence so a JS reload (fast refresh, OTA) preserves session.

## Rebuild recipe

1. Wrap AsyncStorage with a typed `SessionStore`.
2. Hook `AppState` + Timer for idle decay.
3. Merge `session.id` into `globalAttributes`.
4. Emit logs via `emitLog` helper.
