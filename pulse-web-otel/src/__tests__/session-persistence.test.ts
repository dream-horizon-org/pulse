/**
 * Unit tests for session persistence (localStorage) — two areas:
 *
 * 1. Session continuity (localStorage + sessionStorage flags)
 *    `wasSessionReused` is true when an unexpired session is resumed from
 *    localStorage (same-tab reload, new tab within inactivity window, or
 *    cross-origin return) — `emitInitialSession` then skips `session.start`.
 *    Tab / clone sessionStorage flags are still written for clone detection.
 *
 * 2. User identity persistence (setUserId / setUserProperty / clearUserIdentity)
 *    User ID and properties are stored in localStorage so they survive page
 *    refreshes and cross-origin redirects.
 *
 * Positive cases:
 *   - Valid localStorage + empty sessionStorage → same session id, `wasSessionReused` true
 *   - Tab / clone flags → still `wasSessionReused` true when session valid; `emitInitialSession` skips `session.start`
 *   - New tab with no localStorage → new session
 *   - setUserId persists across "reloads" (new SessionProvider instance)
 *   - setUserProperties merges, preserves existing keys
 *   - clearUserIdentity wipes both userId and properties
 *   - user.id injected into global attrs when set
 *   - user.* properties injected into global attrs
 *
 * Negative cases:
 *   - Expired session → new session even if localStorage has an ID
 *   - setUserId("") → removes userId from attrs
 *   - setUserProperties with null value → removes that key
 *   - localStorage unavailable → graceful no-op (no crash)
 *
 * TODO(future): session ID sessionStorage tier
 *   Currently session keys (SESSION_ID_KEY, SESSION_TS_KEY, SESSION_START_KEY) only use
 *   localStorage; _memSession is the in-memory fallback. Add sessionStorage as tier 2 so
 *   reloads within the same tab continue the same session when localStorage is blocked
 *   (WKWebView ITP, sandboxed iframe). Pattern: PostHog uses cookies, Sentry uses
 *   sessionStorage for session IDs. Same-tab reload = same session; new tab = new session.
 *   Tests to add when implemented:
 *     - localStorage blocked, SessionProvider reconstructed → same session.id via sessionStorage
 *     - localStorage + sessionStorage blocked, reload → new session (memory only)
 *     - session expiry with sessionStorage tier → rotation still fires once, not multiple times
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  SessionProvider,
  persistUserId as setPersistedUserId,
  getPersistedUserId,
  setPersistedUserProperties,
  getPersistedUserProperties,
  clearPersistedUserIdentity,
  _resetInstallationStateForTesting,
} from "../session";
import { PulseGlobalAttributesProcessor } from "../processors/global-attrs-processor";
import type { PulseWebConfig } from "../config";

// ─── Storage setup ──────────────────────────────────────────────────────────
// Use jsdom's real localStorage / sessionStorage (same approach as m1.test.ts).
// Clear between tests so each starts from a clean slate.

const SESSION_ID_KEY = "pulse_session_id";
const SESSION_TS_KEY = "pulse_session_ts";
const SESSION_START_KEY = "pulse_session_start";
const SESSION_TAB_KEY = "pulse_tab_session";
const SESSION_CLONE_FLAG_KEY = "pulse_session_clone_flag";
const USER_ID_KEY = "pulse_user_id";
const USER_PROPS_KEY = "pulse_user_properties";

beforeEach(() => {
  window.localStorage.clear();
  window.sessionStorage.clear();
  _resetInstallationStateForTesting();
});

afterEach(() => {
  vi.restoreAllMocks();
});

/**
 * Write a valid unexpired session to localStorage.
 * msSinceLastActivity: how long ago the last activity was (default 1 s — well within 30 min).
 */
function seedSession(id: string, msSinceLastActivity = 1000): void {
  const now = Date.now();
  const lastActivityMs = now - msSinceLastActivity;
  const sessionStartMs = now - msSinceLastActivity - 5000; // started a bit before last activity
  window.localStorage.setItem(SESSION_ID_KEY, id);
  window.localStorage.setItem(
    SESSION_TS_KEY,
    String(lastActivityMs * 1_000_000),
  );
  window.localStorage.setItem(
    SESSION_START_KEY,
    String(sessionStartMs * 1_000_000),
  );
}

/** Write an expired session (past 30 min inactivity timeout) to localStorage. */
function seedExpiredSession(id: string): void {
  const OVER_TIMEOUT_MS = 31 * 60 * 1000; // 31 min > 30 min default
  seedSession(id, OVER_TIMEOUT_MS);
}

// ─── Part 1: Session id + wasSessionReused (tab / clone flags vs localStorage only) ─

describe("session continuity: localStorage resume and tab/clone reuse", () => {
  it("localStorage-only resume: same session id and wasSessionReused true (analytics parity)", () => {
    // e.g. sessionStorage cleared — no tab or clone markers on first paint
    seedSession("session-abc");
    expect(window.sessionStorage.getItem(SESSION_TAB_KEY)).toBeNull();
    expect(window.sessionStorage.getItem(SESSION_CLONE_FLAG_KEY)).toBeNull();

    const provider = new SessionProvider();
    expect(provider.wasSessionReused()).toBe(true);
    expect(provider.getSessionId()).toBe("session-abc");
  });

  it("when wasSessionReused, emitInitialSession does not emit session.start for existing id", () => {
    seedSession("session-abc");
    const provider = new SessionProvider();
    const handler = vi.fn();
    provider.onSessionChange(handler);
    provider.emitInitialSession();

    const startEvents = handler.mock.calls.filter(([e]) => e.type === "start");
    expect(startEvents).toHaveLength(0);
  });

  it("reuses session on same-tab reload (sessionStorage tab flag present)", () => {
    seedSession("session-reload");
    window.sessionStorage.setItem(SESSION_TAB_KEY, "1");

    const provider = new SessionProvider();
    expect(provider.wasSessionReused()).toBe(true);
    expect(provider.getSessionId()).toBe("session-reload");
  });

  it("reuses session when tab is cloned (clone flag present)", () => {
    seedSession("session-clone");
    window.sessionStorage.setItem(SESSION_CLONE_FLAG_KEY, "1");

    const provider = new SessionProvider();
    expect(provider.wasSessionReused()).toBe(true);
    expect(provider.getSessionId()).toBe("session-clone");
  });

  it("new session when no localStorage session exists (brand-new user)", () => {
    // No session seeded, no sessionStorage flags
    const provider = new SessionProvider();
    expect(provider.wasSessionReused()).toBe(false);
    expect(provider.getSessionId()).toBeTruthy();
  });

  it("new session when localStorage session has expired", () => {
    seedExpiredSession("session-old");
    // sessionStorage also empty (simulates cross-origin redirect)

    const provider = new SessionProvider();
    // Old session should NOT be reused — it's past the inactivity window
    expect(provider.wasSessionReused()).toBe(false);
    // getSessionId() will rotate and create a new ID
    const id = provider.getSessionId();
    expect(id).not.toBe("session-old");
  });

  it("expired session causes rotation: new session ID differs from expired one", () => {
    seedExpiredSession("session-old");
    const provider = new SessionProvider();
    // wasSessionReused = false after rotation → emitInitialSession may emit session.start
    const newId = provider.getSessionId(); // rotation fires here for expired sessions
    // The new session must be a fresh UUID, not the expired one
    expect(newId).not.toBe("session-old");
    expect(newId).toMatch(/^[0-9a-f-]{36}$/i);
  });

  it("reuses session across multiple 'page loads' within inactivity window", () => {
    seedSession("session-persist", 5000); // 5 sec ago — well within 30 min timeout

    const provider1 = new SessionProvider();
    expect(provider1.getSessionId()).toBe("session-persist");

    // Simulate a second page load within the window
    const provider2 = new SessionProvider();
    expect(provider2.getSessionId()).toBe("session-persist");
  });

  it("concurrent getSessionId() calls on expired session emit exactly ONE session.start", () => {
    // Regression: unguarded rotation path caused duplicate session.start on re-entrant calls
    // from GlobalAttrsProcessor.onEmit during event emission.
    seedExpiredSession("session-expired");

    const provider = new SessionProvider();
    const events: string[] = [];
    provider.onSessionChange((e) => events.push(e.type));

    // Simulate multiple concurrent callers (e.g. navigation + click instrumentation)
    const id1 = provider.getSessionId();
    const id2 = provider.getSessionId();
    const id3 = provider.getSessionId();

    // All callers should return the same new session ID
    expect(id1).toBe(id2);
    expect(id2).toBe(id3);
    expect(id1).not.toBe("session-expired");

    // Exactly one session.end + one session.start — no duplicates
    expect(events.filter((e) => e === "end")).toHaveLength(1);
    expect(events.filter((e) => e === "start")).toHaveLength(1);
  });

  it("emitInitialSession() on expired session fires exactly ONE session.start for the new ID", () => {
    // Regression: emitInitialSession() was reading the old ID directly and emitting
    // session.start for it — then the first navigation triggered getSessionId() which
    // rotated again, producing a second session.start for a different new ID.
    seedExpiredSession("session-expired-init");

    const provider = new SessionProvider();
    const events: {
      type: string;
      sessionId?: string;
      newSessionId?: string;
    }[] = [];
    provider.onSessionChange((e) => events.push(e));
    provider.emitInitialSession();

    const starts = events.filter((e) => e.type === "start");
    expect(starts).toHaveLength(1);
    expect(starts[0]?.newSessionId).not.toBe("session-expired-init");

    // Subsequent getSessionId() calls must NOT rotate again
    const id1 = provider.getSessionId();
    const id2 = provider.getSessionId();
    expect(id1).toBe(id2);
    expect(events.filter((e) => e.type === "start")).toHaveLength(1);
  });

  it("zero-ts session (lastTs=0) emits exactly ONE session.start on concurrent calls", () => {
    // Regression: when pulse_session_ts='0', the code fell through to an unguarded
    // rotation path — multiple callers each created a new session simultaneously.
    window.localStorage.setItem("pulse_session_id", "session-zero-ts");
    window.localStorage.setItem("pulse_session_ts", "0");
    window.localStorage.setItem("pulse_session_start", "0");

    const provider = new SessionProvider();
    const events: string[] = [];
    provider.onSessionChange((e) => events.push(e.type));

    const id1 = provider.getSessionId();
    const id2 = provider.getSessionId();
    const id3 = provider.getSessionId();

    expect(id1).toBe(id2);
    expect(id2).toBe(id3);
    expect(id1).not.toBe("session-zero-ts");

    expect(events.filter((e) => e === "start")).toHaveLength(1);
  });
});

// ─── Part 2: User identity persistence ───────────────────────────────────────

describe("setPersistedUserId / getPersistedUserId", () => {
  it("stores user ID in localStorage", () => {
    setPersistedUserId("user-123");
    expect(window.localStorage.getItem(USER_ID_KEY)).toBe("user-123");
  });

  it("reads back the same user ID", () => {
    setPersistedUserId("user-abc");
    expect(getPersistedUserId()).toBe("user-abc");
  });

  it("survives a simulated page reload (read by new code instance)", () => {
    setPersistedUserId("user-xyz");
    // Simulate reload: call getPersistedUserId again (same localStorage)
    const reloaded = getPersistedUserId();
    expect(reloaded).toBe("user-xyz");
  });

  it("setPersistedUserId('') removes the key", () => {
    setPersistedUserId("user-123");
    setPersistedUserId("");
    expect(window.localStorage.getItem(USER_ID_KEY)).toBeNull();
    expect(getPersistedUserId()).toBeNull();
  });

  it("returns null when no user ID set", () => {
    expect(getPersistedUserId()).toBeNull();
  });
});

describe("setPersistedUserProperties / getPersistedUserProperties", () => {
  it("stores properties in localStorage", () => {
    setPersistedUserProperties({ plan: "pro", locale: "en-IN" });
    expect(window.localStorage.getItem(USER_PROPS_KEY)).toBeTruthy();
  });

  it("reads back the correct properties", () => {
    setPersistedUserProperties({ plan: "pro", locale: "en-IN" });
    const props = getPersistedUserProperties();
    expect(props.plan).toBe("pro");
    expect(props.locale).toBe("en-IN");
  });

  it("merges new props with existing — does not overwrite unrelated keys", () => {
    setPersistedUserProperties({ plan: "pro" });
    setPersistedUserProperties({ locale: "en-IN" }); // second call
    const props = getPersistedUserProperties();
    expect(props.plan).toBe("pro");
    expect(props.locale).toBe("en-IN");
  });

  it("overwrites existing key with new value", () => {
    setPersistedUserProperties({ plan: "free" });
    setPersistedUserProperties({ plan: "pro" });
    expect(getPersistedUserProperties().plan).toBe("pro");
  });

  it("null value removes a key from the bag", () => {
    setPersistedUserProperties({ plan: "pro", temp: "x" });
    setPersistedUserProperties({ temp: null });
    const props = getPersistedUserProperties();
    expect(props.plan).toBe("pro");
    expect("temp" in props).toBe(false);
  });

  it("returns empty object when nothing stored", () => {
    expect(getPersistedUserProperties()).toEqual({});
  });

  it("returns empty object when localStorage value is malformed JSON", () => {
    window.localStorage.setItem(USER_PROPS_KEY, "not-json{{");
    expect(getPersistedUserProperties()).toEqual({});
  });
});

describe("clearPersistedUserIdentity", () => {
  it("removes both userId and properties from localStorage", () => {
    setPersistedUserId("user-123");
    setPersistedUserProperties({ plan: "pro" });
    clearPersistedUserIdentity();
    expect(window.localStorage.getItem(USER_ID_KEY)).toBeNull();
    expect(window.localStorage.getItem(USER_PROPS_KEY)).toBeNull();
  });

  it("getPersistedUserId returns null after clear", () => {
    setPersistedUserId("user-123");
    clearPersistedUserIdentity();
    expect(getPersistedUserId()).toBeNull();
  });

  it("getPersistedUserProperties returns {} after clear", () => {
    setPersistedUserProperties({ plan: "pro" });
    clearPersistedUserIdentity();
    expect(getPersistedUserProperties()).toEqual({});
  });

  it("is safe to call when nothing was set", () => {
    expect(() => clearPersistedUserIdentity()).not.toThrow();
  });
});

// ─── Part 3: Global attrs processor injects user identity ─────────────────────

function makeProcessor(): PulseGlobalAttributesProcessor {
  const mockSession = {
    getSessionId: vi.fn().mockReturnValue("sess-test"),
    getWindowId: vi.fn().mockReturnValue("win-test"),
    updateActivity: vi.fn(),
    currentSessionId: vi.fn().mockReturnValue("sess-test"),
  } as unknown as InstanceType<typeof SessionProvider>;

  const proc = new PulseGlobalAttributesProcessor(
    mockSession,
    {} as PulseWebConfig,
    "",
  );
  proc.hydrateUserIdentity(getPersistedUserId(), getPersistedUserProperties());
  return proc;
}

function getAttrs(
  proc: PulseGlobalAttributesProcessor,
): Record<string, unknown> {
  const logRecord = {
    attributes: {} as Record<string, unknown>,
    setAttribute(k: string, v: unknown) {
      this.attributes[k] = v;
    },
  };
  proc.onEmit(logRecord as Parameters<typeof proc.onEmit>[0]);
  return logRecord.attributes;
}

describe("PulseGlobalAttributesProcessor user identity injection", () => {
  it("injects user.id when setUserId is called", () => {
    setPersistedUserId("user-abc");
    const proc = makeProcessor();
    const attrs = getAttrs(proc);
    expect(attrs["user.id"]).toBe("user-abc");
  });

  it("does not inject user.id when not set", () => {
    const proc = makeProcessor();
    const attrs = getAttrs(proc);
    expect("user.id" in attrs).toBe(false);
  });

  it("injects user.* properties from localStorage", () => {
    setPersistedUserProperties({ plan: "pro", locale: "en-IN" });
    const proc = makeProcessor();
    const attrs = getAttrs(proc);
    expect(attrs["pulse.user.plan"]).toBe("pro");
    expect(attrs["pulse.user.locale"]).toBe("en-IN");
  });

  it("in-memory setUserId overrides localStorage value", () => {
    setPersistedUserId("old-user");
    const proc = makeProcessor();
    proc.setUserId("new-user");
    const attrs = getAttrs(proc);
    expect(attrs["user.id"]).toBe("new-user");
  });

  it("setUserId('') clears user.id from attrs", () => {
    setPersistedUserId("user-abc");
    const proc = makeProcessor();
    proc.setUserId("");
    const attrs = getAttrs(proc);
    expect("user.id" in attrs).toBe(false);
  });

  it("setUserProperties null value removes key from injected attrs", () => {
    setPersistedUserProperties({ plan: "pro", temp: "x" });
    const proc = makeProcessor();
    proc.setUserProperties({ temp: null });
    const attrs = getAttrs(proc);
    expect(attrs["pulse.user.plan"]).toBe("pro");
    expect("pulse.user.temp" in attrs).toBe(false);
  });

  it("in-memory user props override localStorage props", () => {
    setPersistedUserProperties({ plan: "free" });
    const proc = makeProcessor();
    proc.setUserProperties({ plan: "pro" });
    const attrs = getAttrs(proc);
    expect(attrs["pulse.user.plan"]).toBe("pro");
  });
});
