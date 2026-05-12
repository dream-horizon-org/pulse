# core/session

## 1. Purpose

Manage installation id and session id across page reloads, BFCache restores, cloned tabs, and OS-level WebView resumes; emit `SessionChangeEvent`s consumed by `SessionInstrumentation` to turn into `session.start` / `session.end` OTLP logs.

## 2. Source location

- `pulse-web-otel/src/session.ts` — `SessionProvider`, persistence helpers, BFCache guard
- `pulse-web-otel/src/types/session.ts` — `SessionChangeEvent`, `SessionStartReason`, `SessionEndReason`
- `pulse-web-otel/src/instrumentations/session.ts` — translates events to OTLP logs

## 3. Public surface

```ts
export class SessionProvider {
  onSessionChange(cb: (e: SessionChangeEvent) => void): () => void;
  getSessionId(): string;
  getWindowId(): string;
  rotate(reason: SessionEndReason): void;
}

export function getOrCreateInstallationId(): string;
export function wasNewInstallation(): boolean;
export function getPersistedUserId(): string | null;
export function getPersistedUserProperties(): Record<string, unknown> | null;
export function persistUserId(id: string): void;
export function persistUserProperties(props: Record<string, unknown>): void;
export function clearPersistedUserIdentity(): void;
```

## 4. Internal design

- 3-tier storage: `localStorage` → `sessionStorage` → in-memory fallback (all storage access wrapped in `swallowStorageError`).
- Keys: `pulse_installation_id`, `pulse_session_id`, `pulse_session_ts`, `pulse_session_start`, `pulse_user_id`, `pulse_user_properties`, plus three guard flags (`pulse_session_clone_flag`, `pulse_tab_session`, `pulse_session_hidden_at`).
- Rotation triggers:
  - 30-minute inactivity (`DEFAULT_INACTIVITY_TIMEOUT_MS`)
  - 4-hour max lifetime (`DEFAULT_MAX_SESSION_LIFETIME_MS`)
  - 15-minute page-hidden timeout (`DEFAULT_PAGE_HIDDEN_TIMEOUT_MS`), survives WebView background reload via `pulse_session_hidden_at`
- BFCache: `pageshow` with `persisted=true` keeps the existing session id (no rotation).
- Clone-tab detection: PostHog-style beforeunload flag. Flag present on init ⇒ tab was duplicated ⇒ reuse session; absent ⇒ brand-new tab, emit `session.start`.

## 5. Dependencies

- `pulse-web-logger.ts` for diagnostics
- `constants/pulse-otel-runtime.ts` for `DomEventType` constants

## 6. Data contracts

Drives these `pulse.type` values via `SessionInstrumentation`:

- `session.start` (log body `session.start`), attrs: `session.id`, `session.previous_id`, `session.start_reason`
- `session.end` (log body `session.end`), attrs: `session.id`, `session.duration_ms`, `session.duration`, `session.end_reason`

Global attribute injection (in `processors/global-attrs-processor.ts`) reads `SessionProvider.getSessionId()` and stamps `session.id` on every span and log.

## 7. Tests

- `src/__tests__/session-persistence.test.ts`
- `src/__tests__/user-identity.test.ts`
- `src/__tests__/sdk-lifecycle.test.ts`

## 8. History / decisions

Canonical SPEC: `pulse-web-otel/docs/instrumentations/sdk-core/SPEC.md` § session lifecycle. The BFCache + Capacitor WebView reload guards were added after observing double `session.start` in mobile WebViews; the trailing-debounce sister fix lives in `instrumentations/navigation.ts`.

## 9. Rebuild recipe

1. Implement tiered storage with try/catch around every read/write.
2. On init: read or mint installation id; read or mint session id; cross-check tab flag and clone flag; honour `pageshow.persisted`.
3. Schedule a single timer-based inactivity check, plus listeners for `visibilitychange`, `pagehide`, `pageshow`, `beforeunload`.
4. Provide `onSessionChange` pub-sub; emit `{type: "end", reason}` then `{type: "start", reason, newSessionId, previousSessionId}` on rotation.
