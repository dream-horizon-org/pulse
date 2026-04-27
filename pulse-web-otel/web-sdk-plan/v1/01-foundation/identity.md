# 01.B — Identity Management

**What this covers:** How the SDK identifies a unique browser installation (`installation.id`) and tracks the current session (`session.id`). This is the Session Provider — the core identity engine. Signal emission for session lifecycle events (`session.start` / `session.end`) lives separately in [session.md](./session.md).

**Files produced:** `src/session.ts` (Session Provider), `src/utils/installation-id.ts`


**Android equivalent:** `InstallationId` + `SessionIdProvider`

---

## Three Tiers of Identity

| ID | Lifetime | Set by | Purpose |
|---|---|---|---|
| `installation.id` | Browser profile lifetime | SDK auto-generated | Unique browser-device tracking, retention, session linking |
| `session.id` | 30 min inactivity timeout | SDK auto-generated | Group a single usage session |
| `user.id` | Until logout / explicit set | App developer | Link to your own user account |

---

## Installation ID

On **Android/iOS**, Installation ID is a UUID stored in SharedPreferences / UserDefaults — survives app restarts, only resets on uninstall. It answers: *"which specific install of the app on which device is this?"*

On **web**, the equivalent is a UUID in `localStorage` — identifies a **specific browser profile on a specific device**. Same analytical purpose:
- Counting unique browsers (equivalent to counting unique installs)
- Linking sessions across time ("this browser was here last Tuesday")
- Retention analysis without requiring login

**Web-specific durability:** Resets if user clears browser data or uses incognito. Different browsers on the same device get different IDs. Most users do not clear localStorage regularly — treat as "unique browser-device", not "unique user".

### Three-tier storage fallback

Mirrors Android's graceful degradation when Keystore is unavailable:

```typescript
// src/utils/installation-id.ts

const STORAGE_KEY = 'pulse_installation_id';

export function getOrCreateInstallationId(): string {
  // Tier 1: localStorage — persists across browser restarts (best)
  try {
    const existing = localStorage.getItem(STORAGE_KEY);
    if (existing) return existing;
    const id = crypto.randomUUID();
    localStorage.setItem(STORAGE_KEY, id);
    return id;
  } catch {
    // localStorage blocked: incognito strict mode, storage quota, sandboxed iframe
  }

  // Tier 2: sessionStorage — persists within tab session only
  try {
    const existing = sessionStorage.getItem(STORAGE_KEY);
    if (existing) return existing;
    const id = crypto.randomUUID();
    sessionStorage.setItem(STORAGE_KEY, id);
    return id;
  } catch {
    // sessionStorage also blocked
  }

  // Tier 3: in-memory — lost on tab close (incognito strict mode)
  if (!_memoryInstallationId) {
    _memoryInstallationId = crypto.randomUUID();
  }
  return _memoryInstallationId;
}

let _memoryInstallationId: string | null = null;
```

| Storage tier | Survives browser restart | Survives tab close | Works in incognito |
|---|---|---|---|
| `localStorage` | ✅ | ✅ | ⚠️ resets each session |
| `sessionStorage` | ❌ | ❌ | ✅ within tab |
| memory | ❌ | ❌ | ✅ within tab |

---

## Session Provider

The Session Provider manages session IDs and publishes lifecycle events that the Session Instrumentation ([session.md](./session.md)) subscribes to.

### Storage keys

```
sessionStorage['pulse_session_id']   — current session UUID
sessionStorage['pulse_session_ts']   — last activity timestamp (ms)
```

### Session rotation logic

```
On any signal emitted:
  └─ updateActivity()
       ├─ if (now - lastTs) > inactivityTimeoutMs (30 min):
       │     emit 'end' for old session
       │     generate new session.id
       │     set session.previous_id = old ID
       │     emit 'start' for new session
       │     persist new ID + timestamp
       └─ else: update timestamp only
```

### Session Provider interface

```typescript
// src/session.ts

export interface SessionChangeEvent {
  type: 'start' | 'end';
  newSessionId?: string;       // on 'start'
  previousSessionId?: string;  // on 'start' if rotation
  sessionId?: string;          // on 'end'
  durationMs?: number;         // on 'end'
  reason: SessionStartReason | SessionEndReason;
}

export type SessionStartReason =
  | 'sdk_init'          // first session on page load
  | 'inactivity_timeout'
  | 'max_lifetime';

export type SessionEndReason =
  | 'inactivity_timeout'
  | 'shutdown'
  | 'page_unload'
  | 'max_lifetime';

export class SessionProvider {
  onSessionChange(handler: (event: SessionChangeEvent) => void): () => void { ... }
  getSessionId(): string { ... }
  getPreviousSessionId(): string { ... }
  updateActivity(): void { ... }   // called by every signal processor
}
```

### BFCache edge case

| Event | Handling |
|---|---|
| `pagehide` (real unload) | Emit `session.end` with reason `page_unload` |
| `pagehide` with `persisted: true` | Do NOT emit end — session continues on restore |
| `pageshow` with `persisted: true` | Update activity timestamp, no `session.start` |

---

## User id and user properties (Android parity)

**Public API (on `PulseWeb` singleton):**

| Method | Role |
|--------|------|
| `setUserId(id: string \| null)` | Sets OTel `user.id` on all subsequent spans, logs, and metrics. `null` / `""` clears. Persists to `localStorage['pulse_user_id']`. Emits lifecycle logs when the **value changes** (see below). |
| `setUserProperty(key, value)` | Adds `pulse.user.<key>` = string value; `null` removes that key. Persists the full map to `localStorage['pulse_user_properties']`. |
| `setUserProperties(props)` | Batch update; `null` removes keys. Same persistence as `setUserProperty`. |

**Storage:**

| Key | Contents |
|-----|-----------|
| `pulse_user_id` | Last logged-in user id string; survives reloads when localStorage allowed. |
| `pulse_user_properties` | JSON object of `{ [key]: string }` mapped to `pulse.user.<key>`. |

On `PulseWeb.start()`, persisted user id + properties are **rehydrated** into the global attributes processor **without** emitting lifecycle logs (cold start restore).

**Lifecycle OTLP logs** (same `pulse.type` / body strings as Android):

| Event | When | Attributes |
|-------|------|---------------|
| `pulse.user.session.end` | Previous user id ending (clear or switch) | `pulse.type`, `user.id` = ended user |
| `pulse.user.session.start` | New non-null user id | `pulse.type`, `user.id`; `pulse.user.previous_id` when switching from another user |

No lifecycle events when `setUserId` is called with the **same** id as currently active, or solely on persistence rehydrate at startup.

**OTel attribute reference:** `user.id`, `pulse.user.<name>`, `pulse.user.previous_id`.

---

## Done Criteria

- [ ] `installation.id` generated on first load; persists across page reloads from localStorage
- [ ] Falls back to sessionStorage if localStorage blocked; then to in-memory
- [ ] `session.id` is a valid UUID on `PulseWeb.start()`
- [ ] `session.id` rotates after 30 min inactivity; `session.previous_id` set correctly
- [ ] `session.id` cleared on tab close (sessionStorage behaviour)
- [ ] `pagehide(persisted=true)` does NOT rotate session
- [ ] `pageshow(persisted=true)` resumes session without emitting `session.start`
- [ ] Unit tests: installation ID persists across calls; session rotates after gap; new ID on first call
- [ ] `PulseWeb.setUserId('u1')` → `user.id` = `u1` on subsequent spans/logs/metrics
- [ ] `PulseWeb.setUserId(null)` → `user.id` absent from subsequent signals
- [ ] `PulseWeb.setUserId(...)` before `PulseWeb.start()` is a silent no-op
- [ ] User id persists across reloads; rehydrated on `PulseWeb.start()` without lifecycle logs
- [ ] User id change emits `pulse.user.session.start` / `pulse.user.session.end` as specified; no-op when same id
- [ ] `setUserProperty` / `setUserProperties` stamp `pulse.user.*` and persist; `null` removes keys
