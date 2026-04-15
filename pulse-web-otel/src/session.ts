// M1: 3-tier identity storage (localStorage → sessionStorage → memory)
// + 30-minute inactivity session rotation + BFCache guard.
// See: web-sdk-plan/v1/01-foundation/identity.md

// Storage keys
const INSTALL_KEY = 'pulse_installation_id';
const SESSION_ID_KEY = 'pulse_session_id';
const SESSION_TS_KEY = 'pulse_session_ts';
const SESSION_START_KEY = 'pulse_session_start';

const DEFAULT_INACTIVITY_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

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
  durationMs?: number;
  reason: SessionStartReason | SessionEndReason;
}

type SessionChangeHandler = (event: SessionChangeEvent) => void;

export class SessionProvider {
  private readonly inactivityTimeoutMs: number;
  private handlers: SessionChangeHandler[] = [];
  private pagehideListener?: (e: PageTransitionEvent) => void;
  private pageshowListener?: (e: PageTransitionEvent) => void;
  private _sessionWasReused = false;

  constructor(inactivityTimeoutMs?: number) {
    this.inactivityTimeoutMs = inactivityTimeoutMs ?? DEFAULT_INACTIVITY_TIMEOUT_MS;

    if (typeof window !== 'undefined') {
      // Must run before event listeners — detects reload vs cloned tab
      this._initializeSession();

      this.pagehideListener = (e: PageTransitionEvent) => {
        if (!e.persisted) {
          // Emit session.end but preserve sessionStorage:
          // - Real tab close: browser discards sessionStorage automatically.
          // - Page reload: _initializeSession() will detect reload and reuse the session.
          this.emitSessionEnd('page_unload', true /* skipClear */);
        }
      };

      this.pageshowListener = (e: PageTransitionEvent) => {
        if (e.persisted) {
          // BFCache restore: update activity to keep session alive
          this.updateActivity();
        }
      };

      window.addEventListener('pagehide', this.pagehideListener);
      window.addEventListener('pageshow', this.pageshowListener);
    }
  }

  /**
   * Returns true if the current navigation type is a page reload or back/forward.
   * Uses the Navigation Timing API; returns false when unavailable (e.g. JSDOM).
   */
  private isPageReload(): boolean {
    try {
      const entries = performance.getEntriesByType('navigation') as PerformanceNavigationTiming[];
      if (entries.length === 0) return false;
      return entries[0].type === 'reload' || entries[0].type === 'back_forward';
    } catch {
      return false;
    }
  }

  /**
   * Called once from the constructor. Determines whether an existing session in
   * sessionStorage should be reused (reload) or discarded (cloned tab).
   *
   * When the Navigation API returns no entries (JSDOM / old browsers) this is a
   * no-op so existing test behaviour is completely unchanged.
   */
  private _initializeSession(): void {
    const existingId = this.readSessionId();
    if (!existingId) return;

    const lastTs = this.readSessionTs();
    const now = Date.now();
    const isActive = lastTs > 0 && now - lastTs <= this.inactivityTimeoutMs;
    if (!isActive) return; // expired — getSessionId() will rotate normally

    try {
      const entries = performance.getEntriesByType('navigation') as PerformanceNavigationTiming[];
      if (entries.length === 0) return; // API unavailable — leave sessionStorage untouched

      const navType = entries[0].type;
      if (navType === 'reload' || navType === 'back_forward') {
        // Genuine reload or history navigation: reuse the existing session
        this._sessionWasReused = true;
        this.updateActivity();
      } else {
        // navType === 'navigate': existing session in sessionStorage on a fresh
        // navigation means this tab was cloned (Cmd+click, Duplicate, window.open).
        // Discard the inherited session so each tab gets its own.
        this.clearSession();
      }
    } catch {
      // Navigation API threw — leave sessionStorage untouched
    }
  }

  /** Whether the session was reused from a page reload rather than freshly created. */
  wasSessionReused(): boolean {
    return this._sessionWasReused;
  }

  private readSessionId(): string | null {
    if (typeof window === 'undefined') return null;
    try {
      return sessionStorage.getItem(SESSION_ID_KEY);
    } catch {
      return null;
    }
  }

  private readSessionTs(): number {
    if (typeof window === 'undefined') return 0;
    try {
      const ts = sessionStorage.getItem(SESSION_TS_KEY);
      return ts ? parseInt(ts, 10) : 0;
    } catch {
      return 0;
    }
  }

  private readSessionStart(): number {
    if (typeof window === 'undefined') return 0;
    try {
      const ts = sessionStorage.getItem(SESSION_START_KEY);
      return ts ? parseInt(ts, 10) : 0;
    } catch {
      return 0;
    }
  }

  private writeSession(id: string, startTs?: number): void {
    if (typeof window === 'undefined') return;
    const now = Date.now();
    try {
      sessionStorage.setItem(SESSION_ID_KEY, id);
      sessionStorage.setItem(SESSION_TS_KEY, String(now));
      sessionStorage.setItem(SESSION_START_KEY, String(startTs ?? now));
    } catch {
      // ignore storage errors
    }
  }

  private clearSession(): void {
    if (typeof window === 'undefined') return;
    try {
      sessionStorage.removeItem(SESSION_ID_KEY);
      sessionStorage.removeItem(SESSION_TS_KEY);
      sessionStorage.removeItem(SESSION_START_KEY);
    } catch {
      // ignore
    }
  }

  private emitSessionEnd(reason: SessionEndReason, skipClear = false): void {
    const sessionId = this.readSessionId();
    if (!sessionId) return;

    const startTs = this.readSessionStart();
    const durationMs = startTs > 0 ? Date.now() - startTs : 0;

    const event: SessionChangeEvent = {
      type: 'end',
      sessionId,
      durationMs,
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
    const now = Date.now();
    const existingId = this.readSessionId();
    const lastTs = this.readSessionTs();

    if (existingId && lastTs > 0 && now - lastTs <= this.inactivityTimeoutMs) {
      // Update activity timestamp
      this.updateActivity();
      return existingId;
    }

    // Need to rotate or create
    const previousSessionId = existingId ?? '';
    if (existingId) {
      // Emit end for the existing session before rotating
      const startTs = this.readSessionStart();
      const durationMs = startTs > 0 ? now - startTs : 0;
      this.emit({
        type: 'end',
        sessionId: existingId,
        durationMs,
        reason: 'inactivity_timeout',
      });
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
      sessionStorage.setItem(SESSION_TS_KEY, String(Date.now()));
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
    const now = Date.now();
    if (!sessionId) {
      // No session exists yet — create one
      const newId = generateUUID();
      this.writeSession(newId, now);
      this.emitSessionStart(newId, '', 'sdk_init');
    } else {
      // Session already exists from getSessionId() call — emit start event for it
      this.emitSessionStart(sessionId, '', 'sdk_init');
    }
  }

  shutdown(): void {
    this.emitSessionEnd('shutdown');

    if (typeof window !== 'undefined') {
      if (this.pagehideListener) {
        window.removeEventListener('pagehide', this.pagehideListener);
      }
      if (this.pageshowListener) {
        window.removeEventListener('pageshow', this.pageshowListener);
      }
    }

    this.handlers = [];
  }

  getSessionStartTimestamp(): number {
    return this.readSessionStart();
  }
}
