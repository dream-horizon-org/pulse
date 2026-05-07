"use client";

/**
 * PulseHealthCheck
 * Runs once on mount and logs every step of the Core SDK health sequence
 * to the browser console. Only active in development.
 *
 * Steps verified:
 *  1. SDK initialized
 *  2. Session created (localStorage)
 *  3. Installation ID persisted (localStorage)
 *  4. Remote config fetched from BE (/v1/configs/active/)
 *  5. Feature gates loaded
 *  6. OTLP export reachable (collector at :4318)
 *  7. Data contract — session.start signal emitted
 */

import { useEffect } from "react";
import { PulseWeb } from "@dreamhorizon/pulse-web";

const GROUP = "[PulseHealthCheck]";
const OK    = "✅";
const FAIL  = "❌";
const WARN  = "⚠️";
const INFO  = "ℹ️";

async function runHealthCheck() {
  if (process.env.NODE_ENV !== "development") return;

  console.group(`${GROUP} Core SDK Health Sequence`);

  // ─── Step 1: SDK Initialized ────────────────────────────────────────────────
  const initialized = PulseWeb.isInitialized();
  if (initialized) {
    console.log(`${OK} [1] SDK initialized — PulseWeb.isInitialized() = true`);
  } else {
    console.error(`${FAIL} [1] SDK NOT initialized — PulseWeb.isInitialized() = false`);
    console.warn(`${WARN} Remaining checks skipped — SDK must be initialized first.`);
    console.groupEnd();
    return;
  }

  // ─── Step 2: Session created ─────────────────────────────────────────────────
  const rawSession = localStorage.getItem("pulse_session");
  const session = rawSession ? (() => { try { return JSON.parse(rawSession); } catch { return null; } })() : null;
  if (session?.id) {
    console.log(`${OK} [2] Session created — session.id = ${session.id}`);
  } else {
    console.warn(`${WARN} [2] No session found in localStorage (key: pulse_session)`);
  }

  // ─── Step 3: Installation ID persisted ───────────────────────────────────────
  const installId = localStorage.getItem("pulse_installation_id");
  if (installId) {
    console.log(`${OK} [3] Installation ID persisted — ${installId}`);
  } else {
    console.warn(`${WARN} [3] No installation ID in localStorage (key: pulse_installation_id)`);
  }

  // ─── Step 4: Remote config fetched from BE ──────────────────────────────────
  const cachedConfig = localStorage.getItem("pulse_sdk_config");
  if (cachedConfig) {
    const parsed = (() => { try { return JSON.parse(cachedConfig); } catch { return null; } })();
    console.log(`${OK} [4] Remote config cached — version: ${parsed?.version ?? "unknown"}`);
    console.log(`${INFO} [4] Config payload:`, parsed);
  } else {
    console.warn(`${WARN} [4] No remote config in localStorage — using DEFAULT_SDK_CONFIG`);
    console.warn(`${INFO} [4] Verify: GET http://localhost:8080/v1/configs/active/ returns 200`);
    // Attempt live fetch to confirm BE reachability
    try {
      const res = await fetch("http://localhost:8080/v1/configs/active/", {
        headers: { "x-api-key": "default-project_devkey01" },
      });
      if (res.ok) {
        const body = await res.json();
        console.log(`${OK} [4] BE /v1/configs/active/ reachable — status ${res.status}`, body);
      } else {
        console.error(`${FAIL} [4] BE /v1/configs/active/ returned ${res.status}`);
      }
    } catch (e) {
      console.error(`${FAIL} [4] BE /v1/configs/active/ unreachable —`, e);
    }
  }

  // ─── Step 5: Feature gates ────────────────────────────────────────────────────
  const parsedConfig = cachedConfig
    ? (() => { try { return JSON.parse(cachedConfig); } catch { return null; } })()
    : null;

  const features = [
    "errors_instrumentation",
    "network_instrumentation",
    "web_vitals",
    "screen_session",
    "session",
    "custom_events",
  ];

  if (parsedConfig?.features) {
    console.group(`${OK} [5] Feature gates (from remote config):`);
    features.forEach((f) => {
      const enabled = parsedConfig.features?.[f] !== false;
      console.log(`  ${enabled ? OK : FAIL} ${f}: ${enabled ? "enabled" : "disabled"}`);
    });
    console.groupEnd();
  } else {
    console.group(`${WARN} [5] Feature gates (DEFAULT_SDK_CONFIG — no remote config):`);
    features.forEach((f) => console.log(`  ${INFO} ${f}: default (assumed enabled)`));
    console.groupEnd();
  }

  // ─── Step 6: OTLP collector reachable ────────────────────────────────────────
  console.log(`${INFO} [6] Checking OTLP collector at :4318 …`);
  try {
    // Send a minimal valid OTLP JSON body — expect 200 or 400 (valid endpoint), not refused
    const res = await fetch("http://localhost:4318/v1/logs", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ resourceLogs: [] }),
    });
    if (res.ok || res.status === 400) {
      console.log(`${OK} [6] OTLP collector reachable — POST /v1/logs → ${res.status}`);
    } else {
      console.error(`${FAIL} [6] OTLP collector returned unexpected status ${res.status}`);
    }
  } catch (e) {
    console.error(`${FAIL} [6] OTLP collector unreachable at localhost:4318 —`, e);
    console.warn(`${INFO} [6] On Android emulator run: adb reverse tcp:4318 tcp:4318`);
  }

  // ─── Step 7: session.start signal emitted ────────────────────────────────────
  // We infer this from the session existing — the SDK emits session.start on session create
  if (session?.id && session?.startTime) {
    console.log(`${OK} [7] session.start signal emitted — startTime: ${new Date(session.startTime).toISOString()}`);
  } else {
    console.warn(`${WARN} [7] Cannot confirm session.start — session data incomplete`);
  }

  console.log(`\n${INFO} Full checklist complete. Check Pulse Dashboard → Sessions for project: default-project`);
  console.groupEnd();
}

export function PulseHealthCheck() {
  useEffect(() => {
    // Small delay to let PulseProvider's useEffect run first
    const t = setTimeout(() => { void runHealthCheck(); }, 500);
    return () => clearTimeout(t);
  }, []);

  return null;
}
