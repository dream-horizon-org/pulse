// M1: 3-tier identity storage (localStorage → sessionStorage → memory)
// + 30-minute inactivity session rotation + BFCache guard.
// See: web-sdk-plan/v1/01-foundation/identity.md

import type {
  SessionChangeEvent,
  SessionEndReason,
  SessionStartReason,
} from "./types/session";
import { PulseWebLogger } from "./pulse-web-logger";
import { DomEventType } from "./constants/pulse-otel-runtime";

/** Storage access can throw (disabled, quota, sandbox). Never break the host app. */
function swallowStorageError(scope: string, err: unknown): void {
  const detail =
    err instanceof Error ? `${err.name}: ${err.message}` : String(err);
  PulseWebLogger.debug(`[session:${scope}] ${detail}`);
}

export type {
  SessionChangeEvent,
  SessionEndReason,
  SessionStartReason,
} from "./types/session";

// Storage keys
const INSTALL_KEY = "pulse_installation_id";
const SESSION_ID_KEY = "pulse_session_id";
const SESSION_TS_KEY = "pulse_session_ts";
const SESSION_START_KEY = "pulse_session_start";
const USER_ID_KEY = "pulse_user_id";
const USER_PROPS_KEY = "pulse_user_properties";

// Clone detection key (PostHog beforeunload flag pattern)
// Written to sessionStorage on init; removed on beforeunload so reload sees it gone.
// If flag is present on init → tab was cloned (duplicated tab) → session reused.
const SESSION_CLONE_FLAG_KEY = "pulse_session_clone_flag";

// Tab session key — written to sessionStorage on init and NOT removed on beforeunload.
// Survives page reload (sessionStorage persists across reload in the same tab).
// Absent in a brand-new tab (Cmd+T) → session.start must fire.
const SESSION_TAB_KEY = "pulse_tab_session";

// Written to sessionStorage when the page is hidden (visibilitychange → hidden).
// Survives Capacitor/WebView full-reload on background+resume so the constructor
// can apply pageHiddenTimeoutMs even after the in-memory _hiddenAtMs is lost.
const SESSION_HIDDEN_AT_KEY = "pulse_session_hidden_at";

const DEFAULT_INACTIVITY_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes
const DEFAULT_MAX_SESSION_LIFETIME_MS = 4 * 60 * 60 * 1000; // 4 hours
const DEFAULT_PAGE_HIDDEN_TIMEOUT_MS = 15 * 60 * 1000; // 15 minutes

// In-memory fallback
let _memoryInstallationId: string | null = null;

// Track whether this is a first-ever install (no ID found in any storage tier).
// Set on first call to getOrCreateInstallationId(). Used to emit app.installation.start.
let _isNewInstall = false;
let _installationChecked = false;

function generateUUID(): string {
  if (
    typeof crypto !== "undefined" &&
    typeof crypto.randomUUID === "function"
  ) {
    return crypto.randomUUID();
  }
  // Fallback UUID v4 implementation
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function tryLocalStorage(op: () => string | null): string | null {
  try {
    return op();
  } catch (err: unknown) {
    swallowStorageError("installationId:localStorage", err);
    return null;
  }
}

function trySessionStorage(op: () => string | null): string | null {
  try {
    return op();
  } catch (err: unknown) {
    swallowStorageError("installationId:sessionStorage", err);
    return null;
  }
}

/** Last persisted user id from localStorage (`pulse_user_id`). */
export function getPersistedUserId(): string | null {
  return tryLocalStorage(() => localStorage.getItem(USER_ID_KEY));
}

/** Persist user id; `null` clears storage (logout). */
export function persistUserId(id: string | null): void {
  try {
    if (id === null || id === "") {
      localStorage.removeItem(USER_ID_KEY);
    } else {
      localStorage.setItem(USER_ID_KEY, id);
    }
  } catch (err: unknown) {
    swallowStorageError("userId:localStorage", err);
  }
}

/** Parsed user properties blob; invalid JSON → `{}`. */
export function getPersistedUserProperties(): Record<string, string> {
  const raw = tryLocalStorage(() => localStorage.getItem(USER_PROPS_KEY));
  if (raw === null || raw === "") return {};
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (
      parsed === null ||
      typeof parsed !== "object" ||
      Array.isArray(parsed)
    ) {
      return {};
    }
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(parsed as Record<string, unknown>)) {
      if (typeof v === "string") out[k] = v;
    }
    return out;
  } catch {
    return {};
  }
}

/** Replace persisted user properties JSON; empty object removes the key. */
export function persistUserProperties(props: Record<string, string>): void {
  try {
    if (Object.keys(props).length === 0) {
      localStorage.removeItem(USER_PROPS_KEY);
    } else {
      localStorage.setItem(USER_PROPS_KEY, JSON.stringify(props));
    }
  } catch (err: unknown) {
    swallowStorageError("userProps:localStorage", err);
  }
}

/**
 * Merge-patch persisted user properties.
 * Null values remove the corresponding key; all other values are written.
 */
export function setPersistedUserProperties(
  props: Record<string, string | null>,
): void {
  const current = getPersistedUserProperties();
  for (const [k, v] of Object.entries(props)) {
    if (v === null) {
      delete current[k];
    } else {
      current[k] = v;
    }
  }
  persistUserProperties(current);
}

/** Clear all persisted user identity (userId + properties). Call on logout. */
export function clearPersistedUserIdentity(): void {
  try {
    localStorage.removeItem(USER_ID_KEY);
    localStorage.removeItem(USER_PROPS_KEY);
  } catch (err: unknown) {
    swallowStorageError("clearUserIdentity", err);
  }
}

/**
 * Returns true if the installation ID was freshly generated on this page load
 * (i.e. not found in any storage tier). Used to emit the app.installation.start signal.
 * Only valid after getOrCreateInstallationId() has been called at least once.
 */
export function wasNewInstallation(): boolean {
  return _isNewInstall;
}

/** Reset installation state — for unit tests only. */
export function _resetInstallationStateForTesting(): void {
  _isNewInstall = false;
  _installationChecked = false;
  _memoryInstallationId = null;
}

export function getOrCreateInstallationId(): string {
  if (typeof window === "undefined") {
    if (!_memoryInstallationId) {
      _memoryInstallationId = generateUUID();
      if (!_installationChecked) {
        _isNewInstall = true;
        _installationChecked = true;
      }
    }
    return _memoryInstallationId;
  }

  // Tier 1: localStorage
  const fromLocal = tryLocalStorage(() => {
    const existing = localStorage.getItem(INSTALL_KEY);
    if (existing) {
      if (!_installationChecked) {
        _isNewInstall = false;
        _installationChecked = true;
      }
      return existing;
    }
    const newId = generateUUID();
    localStorage.setItem(INSTALL_KEY, newId);
    if (!_installationChecked) {
      _isNewInstall = true;
      _installationChecked = true;
    }
    return newId;
  });
  if (fromLocal) return fromLocal;

  // Tier 2: sessionStorage
  const fromSession = trySessionStorage(() => {
    const existing = sessionStorage.getItem(INSTALL_KEY);
    if (existing) {
      if (!_installationChecked) {
        _isNewInstall = false;
        _installationChecked = true;
      }
      return existing;
    }
    const newId = generateUUID();
    sessionStorage.setItem(INSTALL_KEY, newId);
    if (!_installationChecked) {
      _isNewInstall = true;
      _installationChecked = true;
    }
    return newId;
  });
  if (fromSession) return fromSession;

  // Tier 3: in-memory
  if (!_memoryInstallationId) {
    _memoryInstallationId = generateUUID();
    if (!_installationChecked) {
      _isNewInstall = true;
      _installationChecked = true;
    }
  }
  return _memoryInstallationId;
}

type SessionChangeHandler = (event: SessionChangeEvent) => void;

export class SessionProvider {
  private readonly inactivityTimeoutMs: number;
  private readonly maxSessionLifetimeMs: number;
  private readonly pageHiddenTimeoutMs: number;
  private handlers: SessionChangeHandler[] = [];
  private pagehideListener?: (e: PageTransitionEvent) => void;
  private pageshowListener?: (e: PageTransitionEvent) => void;
  private beforeunloadListener?: () => void;
  private visibilityChangeListener?: () => void;

  /** In-memory window ID — unique per page load, not persisted */
  private readonly _windowId: string;

  /** Whether the session was reused (reload or clone) */
  private _sessionReused = false;

  /** Reentrancy guard: prevent nested getSessionId() calls from triggering another rotation */
  private _rotatingSession = false;

  /** Dedup guard: tracks which session ID has already had session.end emitted. */
  private _emittedEndForSession: string | null = null;

  /** Timestamp when page was hidden (ms), or null if not hidden */
  _hiddenAtMs: number | null = null;

  constructor(
    inactivityTimeoutMs?: number,
    maxSessionLifetimeMs?: number,
    pageHiddenTimeoutMs?: number,
  ) {
    this.inactivityTimeoutMs =
      inactivityTimeoutMs ?? DEFAULT_INACTIVITY_TIMEOUT_MS;
    this.maxSessionLifetimeMs =
      maxSessionLifetimeMs ?? DEFAULT_MAX_SESSION_LIFETIME_MS;
    this.pageHiddenTimeoutMs =
      pageHiddenTimeoutMs ?? DEFAULT_PAGE_HIDDEN_TIMEOUT_MS;

    // Generate a unique window ID for this page load
    this._windowId = generateUUID();

    if (typeof window !== "undefined") {
      // Clone detection: check if the clone flag is present in sessionStorage
      // If so, this tab was duplicated (cloned) from another tab → reuse session
      const hasCloneFlag = this._readCloneFlag();

      // Tab session detection: present when this is the same tab reloading.
      // Absent in a brand-new tab (Cmd+T) because sessionStorage is not inherited.
      const hasTabSession = this._readTabSession();

      // Check for active session in localStorage
      const existingId = this._readSessionId();
      const existingTs = this._readSessionTs();
      const existingStart = this._readSessionStart();
      const now = Date.now();

      if (existingId && existingTs > 0) {
        // Active session found. Determine if it's fresh enough.
        const age = existingStart > 0 ? now - existingStart : 0;
        const inactivityOk = now - existingTs <= this.inactivityTimeoutMs;
        const lifetimeOk = age <= this.maxSessionLifetimeMs;

        // Check if page was hidden long enough to expire the session.
        // The in-memory _hiddenAtMs is lost when Capacitor/WebView destroys the JS
        // context on background, so we persist the timestamp to sessionStorage on
        // visibilitychange=hidden and read it back here on cold-start.
        const hiddenAt = this._readHiddenAt();
        this._clearHiddenAt();
        const pageHiddenOk =
          hiddenAt === null ||
          now - hiddenAt <= this.pageHiddenTimeoutMs;

        if (inactivityOk && lifetimeOk && pageHiddenOk) {
          // Reuse any unexpired session from localStorage, regardless of how this
          // page load arrived (same-tab reload, new tab, or cross-origin redirect).
          //
          // Previously this was gated on `hasCloneFlag || hasTabSession` (sessionStorage
          // flags), which correctly distinguished reloads from new tabs but broke payment
          // flows: a payment gateway redirect clears sessionStorage, so returning users
          // would get a duplicate session.start for the same session ID.
          //
          // Standard web analytics behaviour (PostHog, Sentry, Mixpanel) is: any page
          // load within the inactivity window continues the existing session regardless
          // of how the navigation arrived.  The sessionStorage flags are still written so
          // clone detection works for other purposes.
          this._sessionReused = true;
          void hasCloneFlag; // read above, still useful for future clone-specific logic
          void hasTabSession;
        }
        // If session expired (by inactivity or lifetime), rotation happens lazily in getSessionId()
      }

      // Always write the clone flag to sessionStorage so any future clone of THIS tab
      // will detect that it was cloned.
      this._writeCloneFlag();

      // Always write the tab session key so page reloads in this same tab are detected.
      // This key is intentionally NOT removed on beforeunload (unlike the clone flag).
      this._writeTabSession();

      // Set up beforeunload: remove clone flag so page reload sees it gone
      this.beforeunloadListener = () => {
        this._removeCloneFlag();
      };
      window.addEventListener(
        DomEventType.BEFORE_UNLOAD,
        this.beforeunloadListener,
      );

      // Set up pagehide listener
      this.pagehideListener = (e: PageTransitionEvent) => {
        if (!e.persisted) {
          // Real unload — emit session.end but don't clear localStorage
          // (so reload can reuse session)
          this._emitSessionEndSkipClear("page_unload");
        }
      };

      // Set up pageshow listener
      this.pageshowListener = (e: PageTransitionEvent) => {
        if (e.persisted) {
          // BFCache restore: update activity to keep session alive
          this.updateActivity();
        }
      };

      // Set up visibility change listener for page-hidden timeout
      this.visibilityChangeListener = () => {
        if (document.hidden) {
          // Page is being hidden — record the timestamp both in memory and
          // sessionStorage so Capacitor/WebView cold-start can read it back.
          this._hiddenAtMs = Date.now();
          this._writeHiddenAt(this._hiddenAtMs);
        } else {
          // Page is becoming visible again — check if too much time passed
          this._clearHiddenAt();
          if (this._hiddenAtMs !== null) {
            const hiddenDuration = Date.now() - this._hiddenAtMs;
            this._hiddenAtMs = null;

            if (hiddenDuration > this.pageHiddenTimeoutMs) {
              // Rotate session due to page-hidden inactivity
              const currentId = this._readSessionId();
              if (currentId) {
                const startTs = this._readSessionStart();
                const durationNs =
                  startTs > 0 ? (Date.now() - startTs) * 1_000_000 : 0;
                this._emitEvent({
                  type: "end",
                  sessionId: currentId,
                  durationNs,
                  reason: "inactivity_timeout",
                });
                this._clearSession();

                const newId = generateUUID();
                this._writeSession(newId);
                this._emitEvent({
                  type: "start",
                  newSessionId: newId,
                  previousSessionId: currentId,
                  reason: "inactivity_timeout",
                });
              }
            }
          }
        }
      };

      window.addEventListener(DomEventType.PAGEHIDE, this.pagehideListener);
      window.addEventListener(DomEventType.PAGESHOW, this.pageshowListener);
      document.addEventListener(
        DomEventType.VISIBILITY_CHANGE,
        this.visibilityChangeListener,
      );
    }
  }

  // ---- Private storage helpers (use localStorage for cross-tab sharing) ----

  private _readSessionId(): string | null {
    if (typeof window === "undefined") return null;
    try {
      return localStorage.getItem(SESSION_ID_KEY);
    } catch (err: unknown) {
      swallowStorageError("readSessionId", err);
      return null;
    }
  }

  private _readSessionTs(): number {
    if (typeof window === "undefined") return 0;
    try {
      const ts = localStorage.getItem(SESSION_TS_KEY);
      // Stored as nanoseconds; convert to ms
      return ts ? Math.floor(parseInt(ts, 10) / 1_000_000) : 0;
    } catch (err: unknown) {
      swallowStorageError("readSessionTs", err);
      return 0;
    }
  }

  private _readSessionStart(): number {
    if (typeof window === "undefined") return 0;
    try {
      const ts = localStorage.getItem(SESSION_START_KEY);
      // Stored as nanoseconds; convert to ms
      return ts ? Math.floor(parseInt(ts, 10) / 1_000_000) : 0;
    } catch (err: unknown) {
      swallowStorageError("readSessionStart", err);
      return 0;
    }
  }

  private _writeSession(id: string, startTs?: number): void {
    if (typeof window === "undefined") return;
    const nowNs = Date.now() * 1_000_000;
    try {
      localStorage.setItem(SESSION_ID_KEY, id);
      localStorage.setItem(SESSION_TS_KEY, String(nowNs));
      localStorage.setItem(SESSION_START_KEY, String(startTs ?? nowNs));
    } catch (err: unknown) {
      swallowStorageError("writeSession", err);
    }
    this._emittedEndForSession = null;
  }

  private _clearSession(): void {
    if (typeof window === "undefined") return;
    try {
      localStorage.removeItem(SESSION_ID_KEY);
      localStorage.removeItem(SESSION_TS_KEY);
      localStorage.removeItem(SESSION_START_KEY);
    } catch (err: unknown) {
      swallowStorageError("clearSession", err);
    }
    // Align dedupe latch with storage: after clear, a new id may emit session.end again.
    this._emittedEndForSession = null;
  }

  private _readCloneFlag(): boolean {
    try {
      return sessionStorage.getItem(SESSION_CLONE_FLAG_KEY) === "1";
    } catch (err: unknown) {
      swallowStorageError("readCloneFlag", err);
      return false;
    }
  }

  private _writeCloneFlag(): void {
    try {
      sessionStorage.setItem(SESSION_CLONE_FLAG_KEY, "1");
    } catch (err: unknown) {
      swallowStorageError("writeCloneFlag", err);
    }
  }

  private _removeCloneFlag(): void {
    try {
      sessionStorage.removeItem(SESSION_CLONE_FLAG_KEY);
    } catch (err: unknown) {
      swallowStorageError("removeCloneFlag", err);
    }
  }

  private _readTabSession(): boolean {
    try {
      return sessionStorage.getItem(SESSION_TAB_KEY) === "1";
    } catch (err: unknown) {
      swallowStorageError("readTabSession", err);
      return false;
    }
  }

  private _writeTabSession(): void {
    try {
      sessionStorage.setItem(SESSION_TAB_KEY, "1");
    } catch (err: unknown) {
      swallowStorageError("writeTabSession", err);
    }
  }

  private _readHiddenAt(): number | null {
    try {
      const val = sessionStorage.getItem(SESSION_HIDDEN_AT_KEY);
      if (val === null) return null;
      const n = Number(val);
      return isNaN(n) ? null : n;
    } catch (err: unknown) {
      swallowStorageError("readHiddenAt", err);
      return null;
    }
  }

  private _writeHiddenAt(ts: number): void {
    try {
      sessionStorage.setItem(SESSION_HIDDEN_AT_KEY, String(ts));
    } catch (err: unknown) {
      swallowStorageError("writeHiddenAt", err);
    }
  }

  private _clearHiddenAt(): void {
    try {
      sessionStorage.removeItem(SESSION_HIDDEN_AT_KEY);
    } catch (err: unknown) {
      swallowStorageError("clearHiddenAt", err);
    }
  }

  private _emitEvent(event: SessionChangeEvent): void {
    for (const handler of this.handlers) {
      try {
        handler(event);
      } catch {
        // ignore handler errors
      }
    }
  }

  private _emitSessionEndSkipClear(reason: SessionEndReason): void {
    const sessionId = this._readSessionId();
    if (!sessionId || this._emittedEndForSession === sessionId) return;
    this._emittedEndForSession = sessionId;

    const startTs = this._readSessionStart();
    const durationNs = startTs > 0 ? (Date.now() - startTs) * 1_000_000 : 0;

    const event: SessionChangeEvent = {
      type: "end",
      sessionId,
      durationNs,
      reason,
    };

    this._emitEvent(event);
    // NOTE: intentionally does NOT call _clearSession() so reload can reuse session
  }

  private _emitSessionEnd(reason: SessionEndReason): void {
    const sessionId = this._readSessionId();
    if (!sessionId) return;

    // Dedupe session.end for the same id (e.g. page_unload then shutdown). Shutdown still clears
    // storage so teardown does not depend on emitting again.
    if (this._emittedEndForSession === sessionId) {
      this._clearSession();
      return;
    }

    this._emittedEndForSession = sessionId;

    const startTs = this._readSessionStart();
    const durationNs = startTs > 0 ? (Date.now() - startTs) * 1_000_000 : 0;

    const event: SessionChangeEvent = {
      type: "end",
      sessionId,
      durationNs,
      reason,
    };

    this._emitEvent(event);
    this._clearSession();
  }

  private _emitSessionStart(
    newSessionId: string,
    previousSessionId: string,
    reason: SessionStartReason,
  ): void {
    const event: SessionChangeEvent = {
      type: "start",
      newSessionId,
      previousSessionId,
      reason,
    };
    this._emitEvent(event);
  }

  // ---- Public API ----

  getSessionId(): string {
    // Reentrancy guard
    if (this._rotatingSession) {
      return this._readSessionId() ?? generateUUID();
    }

    const now = Date.now();
    const existingId = this._readSessionId();
    const lastTs = this._readSessionTs();
    const sessionStartMs = this._readSessionStart();

    if (existingId && lastTs > 0) {
      const inactivityOk = now - lastTs <= this.inactivityTimeoutMs;
      const age = sessionStartMs > 0 ? now - sessionStartMs : 0;
      const lifetimeOk = age <= this.maxSessionLifetimeMs;

      if (inactivityOk && lifetimeOk) {
        // Session is valid — update activity timestamp
        this._updateActivityTs();
        return existingId;
      }

      // Determine reason for rotation
      const rotationReason: SessionEndReason = lifetimeOk
        ? "inactivity_timeout"
        : "max_lifetime";
      const startReason: SessionStartReason = lifetimeOk
        ? "inactivity_timeout"
        : "max_lifetime";

      // Rotate session
      this._rotatingSession = true;
      try {
        const durationNs =
          sessionStartMs > 0 ? (now - sessionStartMs) * 1_000_000 : 0;
        this._emitEvent({
          type: "end",
          sessionId: existingId,
          durationNs,
          reason: rotationReason,
        });
        this._clearSession();

        const newId = generateUUID();
        this._writeSession(newId);
        this._emitSessionStart(newId, existingId, startReason);
        return newId;
      } finally {
        this._rotatingSession = false;
      }
    }

    // No valid session — create a fresh one
    if (existingId) {
      // Expired session — emit end before creating new
      const durationNs =
        sessionStartMs > 0 ? (now - sessionStartMs) * 1_000_000 : 0;
      this._emitEvent({
        type: "end",
        sessionId: existingId,
        durationNs,
        reason: "inactivity_timeout",
      });
      this._clearSession();
    }

    const newId = generateUUID();
    this._writeSession(newId);
    this._emitSessionStart(
      newId,
      existingId ?? "",
      existingId ? "inactivity_timeout" : "sdk_init",
    );
    return newId;
  }

  /**
   * Non-rotating read accessor — returns the current session ID without triggering
   * any rotation or activity update.
   */
  currentSessionId(): string | null {
    return this._readSessionId();
  }

  /**
   * Returns the window ID — a unique UUID generated per page load, not persisted.
   * This distinguishes tabs that have the same session ID (clone detection).
   */
  getWindowId(): string {
    return this._windowId;
  }

  /**
   * Returns true if the session was reused from a previous page load or cloned tab.
   */
  wasSessionReused(): boolean {
    return this._sessionReused;
  }

  getPreviousSessionId(): string {
    // Returns the previous session ID tracked in memory (before current rotation)
    // This is used externally; we store it in localStorage if available
    if (typeof window === "undefined") return "";
    try {
      return localStorage.getItem("pulse_prev_session_id") ?? "";
    } catch (err: unknown) {
      swallowStorageError("getPreviousSessionId", err);
      return "";
    }
  }

  private _updateActivityTs(): void {
    if (typeof window === "undefined") return;
    try {
      localStorage.setItem(SESSION_TS_KEY, String(Date.now() * 1_000_000));
    } catch (err: unknown) {
      swallowStorageError("updateActivityTs", err);
    }
  }

  updateActivity(): void {
    this._updateActivityTs();
  }

  onSessionChange(handler: SessionChangeHandler): () => void {
    this.handlers.push(handler);
    return () => {
      this.handlers = this.handlers.filter((h) => h !== handler);
    };
  }

  emitInitialSession(): void {
    // Called by SessionInstrumentation after install.
    // If the session was reused (reload or clone), do NOT emit session.start.
    if (this._sessionReused) {
      return;
    }

    const sessionId = this._readSessionId();
    if (!sessionId) {
      // No session exists yet — create one
      const newId = generateUUID();
      this._writeSession(newId);
      this._emitSessionStart(newId, "", "sdk_init");
    } else {
      // Session already exists from getSessionId() call — emit start event for it
      this._emitSessionStart(sessionId, "", "sdk_init");
    }
  }

  shutdown(): void {
    this._emitSessionEnd("shutdown");

    if (typeof window !== "undefined") {
      if (this.pagehideListener) {
        window.removeEventListener(
          DomEventType.PAGEHIDE,
          this.pagehideListener,
        );
      }
      if (this.pageshowListener) {
        window.removeEventListener(
          DomEventType.PAGESHOW,
          this.pageshowListener,
        );
      }
      if (this.beforeunloadListener) {
        window.removeEventListener(
          DomEventType.BEFORE_UNLOAD,
          this.beforeunloadListener,
        );
      }
      if (this.visibilityChangeListener) {
        document.removeEventListener(
          DomEventType.VISIBILITY_CHANGE,
          this.visibilityChangeListener,
        );
      }
    }

    this.handlers = [];
  }

  getSessionStartTimestamp(): number {
    return this._readSessionStart();
  }
}
