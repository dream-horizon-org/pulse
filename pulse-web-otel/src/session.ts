// M1: 3-tier identity storage (localStorage → sessionStorage → memory)
// + session rotation (inactivity / max-lifetime / page-hidden) + BFCache guard.
// Clone detection uses PostHog's beforeunload-flag pattern (no Navigation API dependency).
// See: web-sdk-plan/v1/01-foundation/identity.md

// Utility: convert milliseconds to nanoseconds (mirrors Android Clock.now()).
function msToNs(ms: number): number {
  return ms * 1_000_000;
}

// Utility: convert nanoseconds to milliseconds.
function nsToMs(ns: number): number {
  return Math.floor(ns / 1_000_000);
}

// Get current time in nanoseconds UTC (mirrors Android Clock.getDefault().now()).
function getCurrentTimeNanos(): number {
  return msToNs(Date.now());
}

// Storage keys
const INSTALL_KEY = 'pulse_installation_id';
const SESSION_ID_KEY = 'pulse_session_id';
const SESSION_TS_KEY = 'pulse_session_ts';  // last activity, stored as nanoseconds
const SESSION_START_KEY = 'pulse_session_start';  // session start, stored as nanoseconds

// Written to sessionStorage on every SDK init; removed on beforeunload so a genuine
// page reload doesn't trigger clone-detection on the next load.  A duplicated tab
// inherits this flag and detects it as a clone on its first init.
const SESSION_CLONE_FLAG_KEY = 'pulse_session_clone_flag';

const DEFAULT_INACTIVITY_TIMEOUT_MS = 30 * 60 * 1000;  // 30 minutes
const DEFAULT_INACTIVITY_TIMEOUT_NS = msToNs(DEFAULT_INACTIVITY_TIMEOUT_MS);
const DEFAULT_MAX_SESSION_LIFETIME_MS = 4 * 60 * 60 * 1000; // 4 hours  (mirrors Android)
const DEFAULT_MAX_SESSION_LIFETIME_NS = msToNs(DEFAULT_MAX_SESSION_LIFETIME_MS);
const DEFAULT_PAGE_HIDDEN_TIMEOUT_MS  = 15 * 60 * 1000; // 15 minutes hidden (mirrors Android)
const DEFAULT_PAGE_HIDDEN_TIMEOUT_NS = msToNs(DEFAULT_PAGE_HIDDEN_TIMEOUT_MS);

// In-memory fallback
let _memoryInstallationId: string | null = null;

// Track whether this is a first-ever install (no ID found in any storage tier).
// Set on first call to getOrCreateInstallationId(). Used to emit app.installation.start.
let _isNewInstall = false;
let _installationChecked = false;

function generateUUID(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  // Fallback UUID v4 implementation
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function tryLocalStorage(op: () => string | null): string | null {
  try {
    return op();
  } catch {
    return null;
  }
}

function trySessionStorage(op: () => string | null): string | null {
  try {
    return op();
  } catch {
    return null;
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
  if (typeof window === 'undefined') {
    if (!_memoryInstallationId) {
      _memoryInstallationId = generateUUID();
      if (!_installationChecked) { _isNewInstall = true; _installationChecked = true; }
    }
    return _memoryInstallationId;
  }

  // Tier 1: localStorage
  const fromLocal = tryLocalStorage(() => {
    const existing = localStorage.getItem(INSTALL_KEY);
    if (existing) {
      if (!_installationChecked) { _isNewInstall = false; _installationChecked = true; }
      return existing;
    }
    const newId = generateUUID();
    localStorage.setItem(INSTALL_KEY, newId);
    if (!_installationChecked) { _isNewInstall = true; _installationChecked = true; }
    return newId;
  });
  if (fromLocal) return fromLocal;

  // Tier 2: sessionStorage
  const fromSession = trySessionStorage(() => {
    const existing = sessionStorage.getItem(INSTALL_KEY);
    if (existing) {
      if (!_installationChecked) { _isNewInstall = false; _installationChecked = true; }
      return existing;
    }
    const newId = generateUUID();
    sessionStorage.setItem(INSTALL_KEY, newId);
    if (!_installationChecked) { _isNewInstall = true; _installationChecked = true; }
    return newId;
  });
  if (fromSession) return fromSession;

  // Tier 3: in-memory
  if (!_memoryInstallationId) {
    _memoryInstallationId = generateUUID();
    if (!_installationChecked) { _isNewInstall = true; _installationChecked = true; }
  }
  return _memoryInstallationId;
}

export type SessionStartReason = 'sdk_init' | 'inactivity_timeout' | 'max_lifetime';
export type SessionEndReason = 'inactivity_timeout' | 'shutdown' | 'page_unload' | 'max_lifetime';

export interface SessionChangeEvent {
  type: 'start' | 'end';
  newSessionId?: string;
  previousSessionId?: string;
  sessionId?: string;
  durationNs?: number;  // nanoseconds UTC (mirrors Android)
  reason: SessionStartReason | SessionEndReason;
}

type SessionChangeHandler = (event: SessionChangeEvent) => void;

export class SessionProvider {
  private readonly inactivityTimeoutMs: number;
  private readonly maxSessionLifetimeMs: number;
  private readonly pageHiddenTimeoutMs: number;
  private handlers: SessionChangeHandler[] = [];
  private pagehideListener?: (e: PageTransitionEvent) => void;
  private pageshowListener?: (e: PageTransitionEvent) => void;
  private _beforeunloadListener?: () => void;
  private _visibilityChangeListener?: () => void;
  private _sessionWasReused = false;
  private _hiddenAtMs: number | null = null;  // milliseconds (for calculation)
  // Unique per browser tab / page-load lifetime — never persisted.
  // Mirrors PostHog's window_id: cloned tabs share the same session.id but each
  // gets a distinct window.id so per-tab activity can be isolated in queries.
  private readonly _windowId: string = generateUUID();

  constructor(
    inactivityTimeoutMs?: number,
    maxSessionLifetimeMs?: number,
    pageHiddenTimeoutMs?: number,
  ) {
    this.inactivityTimeoutMs  = inactivityTimeoutMs  ?? DEFAULT_INACTIVITY_TIMEOUT_MS;
    this.maxSessionLifetimeMs = maxSessionLifetimeMs ?? DEFAULT_MAX_SESSION_LIFETIME_MS;
    this.pageHiddenTimeoutMs  = pageHiddenTimeoutMs  ?? DEFAULT_PAGE_HIDDEN_TIMEOUT_MS;

    if (typeof window !== 'undefined') {
      // Step 1: detect clone vs reload before registering any listeners.
      this._initializeSession();

      // Step 2: pagehide — emit session.end, keep sessionStorage intact.
      //   On tab close  → browser clears sessionStorage automatically.
      //   On reload     → beforeunload will remove the clone flag so the next
      //                   load knows it's a reload and reuses the session.
      this.pagehideListener = (e: PageTransitionEvent) => {
        if (!e.persisted) {
          this.emitSessionEnd('page_unload', true /* skipClear */);
        }
      };

      // Step 3: pageshow (BFCache restore) — keep session alive.
      this.pageshowListener = (e: PageTransitionEvent) => {
        if (e.persisted) {
          this._hiddenAtMs = null;
          this.updateActivity();
        }
      };

      // Step 4: beforeunload — remove the clone flag so a genuine reload of
      //   THIS tab does not trigger clone-detection on the next load.
      this._beforeunloadListener = () => {
        try { sessionStorage.removeItem(SESSION_CLONE_FLAG_KEY); } catch { /* ignore */ }
      };

      // Step 5: visibilitychange — rotate session after 15 min hidden (mirrors Android).
      this._visibilityChangeListener = () => {
        if (typeof document === 'undefined') return;
        if (document.hidden) {
          this._hiddenAtMs = Date.now();
        } else {
          if (this._hiddenAtMs !== null) {
            const hiddenMs = Date.now() - this._hiddenAtMs;
            if (hiddenMs >= this.pageHiddenTimeoutMs) {
              this._rotateSessionAfterHidden(this._hiddenAtMs);
            }
            this._hiddenAtMs = null;
          }
        }
      };

      window.addEventListener('pagehide',     this.pagehideListener);
      window.addEventListener('pageshow',     this.pageshowListener);
      window.addEventListener('beforeunload', this._beforeunloadListener);
      document.addEventListener('visibilitychange', this._visibilityChangeListener);
    }
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  /**
   * Called once from the constructor (before event-listener registration).
   *
   * Uses PostHog's beforeunload-flag pattern — no Navigation API required:
   *
   *   Clone flag present  → tab was duplicated / opened via window.open()
   *                         → discard inherited session, start fresh.
   *   Clone flag absent   → genuine reload (beforeunload removed it) OR fresh new tab
   *                         → reuse session if one exists and is still active.
   *
   * After the check, the flag is always set so the NEXT init (whether in this
   * tab or in a future clone) can make the same determination.
   */
  private _initializeSession(): void {
    if (typeof window === 'undefined') return;
    try {
      // Whether this tab was reloaded OR cloned, reuse the inherited session if it is
      // still within its inactivity and max-lifetime windows.
      //
      // PostHog model: clone → same session.id, new window.id (generated in constructor).
      // The clone flag is still written/removed so callers can detect the clone case
      // for future instrumentation, but it no longer affects session continuity.
      const existingId = this.readSessionId();
      const lastTs     = this.readSessionTs();  // nanoseconds
      const startTs    = this.readSessionStart();  // nanoseconds
      const now        = getCurrentTimeNanos();

      const notInactivityExpired  = lastTs  > 0 && now - lastTs  <= msToNs(this.inactivityTimeoutMs);
      const notMaxLifetimeExpired = startTs > 0 && now - startTs <  msToNs(this.maxSessionLifetimeMs);

      if (existingId && notInactivityExpired && notMaxLifetimeExpired) {
        this._sessionWasReused = true;
        this.updateActivity();
      }

      // Always write the clone flag for this tab.
      // beforeunload will remove it on navigate/reload so a genuine reload won't see it.
      // A cloned tab inherits it and can use it to detect that it is a clone.
      sessionStorage.setItem(SESSION_CLONE_FLAG_KEY, '1');
    } catch {
      // sessionStorage unavailable — skip entirely.
    }
  }

  /**
   * Rotate the session after the page was hidden longer than pageHiddenTimeoutMs.
   * The session end timestamp is anchored to when the page was hidden (mirrors Android).
   */
  private _rotateSessionAfterHidden(hiddenAtMs: number): void {
    const existingId = this.readSessionId();
    if (!existingId) return;

    const startTs    = this.readSessionStart();  // nanoseconds
    const hiddenAtNs = msToNs(hiddenAtMs);
    const durationNs = startTs > 0 ? hiddenAtNs - startTs : 0;

    this.emit({ type: 'end', sessionId: existingId, durationNs, reason: 'inactivity_timeout' });
    this.clearSession();

    const newId = generateUUID();
    this.writeSession(newId, getCurrentTimeNanos());
    this.emitSessionStart(newId, existingId, 'inactivity_timeout');
  }

  /** Whether the session was reused from a page reload or tab clone rather than freshly created. */
  wasSessionReused(): boolean {
    return this._sessionWasReused;
  }

  /** Unique ID for this browser tab / page-load. Never persisted. */
  getWindowId(): string {
    return this._windowId;
  }

  private readSessionId(): string | null {
    if (typeof window === 'undefined') return null;
    try {
      return localStorage.getItem(SESSION_ID_KEY);
    } catch {
      return null;
    }
  }

  private readSessionTs(): number {
    if (typeof window === 'undefined') return 0;
    try {
      const ts = localStorage.getItem(SESSION_TS_KEY);
      return ts ? parseInt(ts, 10) : 0;  // nanoseconds
    } catch {
      return 0;
    }
  }

  private readSessionStart(): number {
    if (typeof window === 'undefined') return 0;
    try {
      const ts = localStorage.getItem(SESSION_START_KEY);
      return ts ? parseInt(ts, 10) : 0;  // nanoseconds
    } catch {
      return 0;
    }
  }

  private writeSession(id: string, startNs?: number): void {
    if (typeof window === 'undefined') return;
    const nowNs = getCurrentTimeNanos();
    try {
      localStorage.setItem(SESSION_ID_KEY, id);
      localStorage.setItem(SESSION_TS_KEY, String(nowNs));
      localStorage.setItem(SESSION_START_KEY, String(startNs ?? nowNs));
    } catch {
      // ignore storage errors
    }
  }

  private clearSession(): void {
    if (typeof window === 'undefined') return;
    try {
      localStorage.removeItem(SESSION_ID_KEY);
      localStorage.removeItem(SESSION_TS_KEY);
      localStorage.removeItem(SESSION_START_KEY);
    } catch {
      // ignore
    }
  }

  private emitSessionEnd(reason: SessionEndReason, skipClear = false): void {
    const sessionId = this.readSessionId();
    if (!sessionId) return;

    const startTs = this.readSessionStart();  // nanoseconds
    const durationNs = startTs > 0 ? getCurrentTimeNanos() - startTs : 0;

    const event: SessionChangeEvent = {
      type: 'end',
      sessionId,
      durationNs,
      reason,
    };

    this.emit(event);
    if (!skipClear) this.clearSession();
  }

  private emitSessionStart(newSessionId: string, previousSessionId: string, reason: SessionStartReason): void {
    const event: SessionChangeEvent = {
      type: 'start',
      newSessionId,
      previousSessionId,
      reason,
    };
    this.emit(event);
  }

  private emit(event: SessionChangeEvent): void {
    for (const handler of this.handlers) {
      try {
        handler(event);
      } catch {
        // ignore handler errors
      }
    }
  }

  getSessionId(): string {
    const now        = getCurrentTimeNanos();  // nanoseconds
    const existingId = this.readSessionId();
    const lastTs     = this.readSessionTs();  // nanoseconds
    const startTs    = this.readSessionStart();  // nanoseconds

    if (existingId && lastTs > 0 && now - lastTs <= msToNs(this.inactivityTimeoutMs)) {
      // Check hard max lifetime (mirrors Android SessionManager).
      if (startTs > 0 && now - startTs >= msToNs(this.maxSessionLifetimeMs)) {
        // Session has lived too long — rotate with reason 'max_lifetime'.
        const durationNs = now - startTs;
        this.emit({ type: 'end', sessionId: existingId, durationNs, reason: 'max_lifetime' });
        this.clearSession();
        const newId = generateUUID();
        this.writeSession(newId);
        this.emitSessionStart(newId, existingId, 'max_lifetime');
        return newId;
      }

      // Active and within max lifetime — refresh the activity timestamp and reuse.
      this.updateActivity();
      return existingId;
    }

    // Need to rotate (inactivity) or create (first call).
    const previousSessionId = existingId ?? '';
    if (existingId) {
      const durationNs = startTs > 0 ? now - startTs : 0;
      this.emit({ type: 'end', sessionId: existingId, durationNs, reason: 'inactivity_timeout' });
      this.clearSession();
    }

    const newId = generateUUID();
    this.writeSession(newId);
    this.emitSessionStart(newId, previousSessionId, existingId ? 'inactivity_timeout' : 'sdk_init');
    return newId;
  }

  getPreviousSessionId(): string {
    // Returns the previous session ID tracked in memory (before current rotation)
    // This is used externally; we store it in localStorage if available
    if (typeof window === 'undefined') return '';
    try {
      return localStorage.getItem('pulse_prev_session_id') ?? '';
    } catch {
      return '';
    }
  }

  updateActivity(): void {
    if (typeof window === 'undefined') return;
    try {
      localStorage.setItem(SESSION_TS_KEY, String(getCurrentTimeNanos()));
    } catch {
      // ignore
    }
  }

  onSessionChange(handler: SessionChangeHandler): () => void {
    this.handlers.push(handler);
    return () => {
      this.handlers = this.handlers.filter((h) => h !== handler);
    };
  }

  emitInitialSession(): void {
    // Called by SessionInstrumentation after install
    // Ensures the initial session.start is emitted
    if (this._sessionWasReused) {
      // Session survived a page reload — do not re-emit session.start for the same session
      return;
    }
    const sessionId = this.readSessionId();
    const nowNs = getCurrentTimeNanos();
    if (!sessionId) {
      // No session exists yet — create one
      const newId = generateUUID();
      this.writeSession(newId, nowNs);
      this.emitSessionStart(newId, '', 'sdk_init');
    } else {
      // Session already exists from getSessionId() call — emit start event for it
      this.emitSessionStart(sessionId, '', 'sdk_init');
    }
  }

  shutdown(): void {
    this.emitSessionEnd('shutdown');

    if (typeof window !== 'undefined') {
      if (this.pagehideListener)          window.removeEventListener('pagehide',     this.pagehideListener);
      if (this.pageshowListener)          window.removeEventListener('pageshow',     this.pageshowListener);
      if (this._beforeunloadListener)     window.removeEventListener('beforeunload', this._beforeunloadListener);
      if (this._visibilityChangeListener) document.removeEventListener('visibilitychange', this._visibilityChangeListener);
    }

    this._hiddenAtMs = null;
    this.handlers  = [];
  }

  getSessionStartTimestamp(): number {
    return this.readSessionStart();  // nanoseconds UTC
  }
}
