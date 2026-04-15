/**
 * PulseDebugPanel — floating overlay that shows real-time SDK state.
 *
 * Toggle:  Shift + P   (keyboard)  OR  click the "P" badge in the corner
 *
 * What it shows:
 *   SDK state      — initialized, session ID, installation ID
 *   localStorage   — pulse_installation_id, pulse_sdk_config version
 *   IndexedDB      — pulse_signal_buffer signal count
 *   OTLP traffic   — live list of /v1/traces|logs|metrics calls intercepted
 *                    (monkey-patches window.fetch on mount, restores on unmount)
 *
 * Only renders in non-production (import.meta.env.DEV).
 */
import React, { useState, useEffect, useRef, useCallback } from "react";

interface OtlpCall {
  endpoint: "traces" | "logs" | "metrics" | string;
  time: string;
  status: number;
  sizeBytes: number;
}

type PulseWebInstance = {
  isInitialized: () => boolean;
};

const PANEL_STYLE: React.CSSProperties = {
  position: "fixed",
  bottom: 16,
  right: 16,
  width: 360,
  maxHeight: "70vh",
  overflowY: "auto",
  background: "#0f172a",
  color: "#e2e8f0",
  fontFamily: "monospace",
  fontSize: 11,
  borderRadius: 8,
  boxShadow: "0 8px 32px rgba(0,0,0,0.5)",
  zIndex: 9999,
  padding: "12px 14px",
};

const BADGE_STYLE: React.CSSProperties = {
  position: "fixed",
  bottom: 16,
  right: 16,
  width: 28,
  height: 28,
  borderRadius: 14,
  background: "#6366f1",
  color: "#fff",
  fontWeight: 800,
  fontSize: 12,
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  cursor: "pointer",
  zIndex: 9999,
  boxShadow: "0 2px 8px rgba(0,0,0,0.4)",
  userSelect: "none",
};

const ROW: React.CSSProperties = {
  display: "flex",
  justifyContent: "space-between",
  padding: "3px 0",
  borderBottom: "1px solid #1e293b",
};

const LABEL: React.CSSProperties = { color: "#94a3b8" };
const VALUE: React.CSSProperties = {
  color: "#a5f3fc",
  maxWidth: 200,
  overflow: "hidden",
  textOverflow: "ellipsis",
  whiteSpace: "nowrap",
};

const ENDPOINT_COLORS: Record<string, string> = {
  traces: "#a78bfa",
  logs: "#34d399",
  metrics: "#fbbf24",
};

function tag(endpoint: string) {
  const color = ENDPOINT_COLORS[endpoint] ?? "#94a3b8";
  return <span style={{ color, fontWeight: 700 }}>/{endpoint}</span>;
}

async function countIdbSignals(): Promise<number> {
  if (typeof indexedDB === "undefined") return 0;
  return new Promise((resolve) => {
    const req = indexedDB.open("pulse_signal_buffer");
    req.onerror = () => resolve(0);
    req.onsuccess = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains("signals")) {
        resolve(0);
        return;
      }
      const tx = db.transaction("signals", "readonly");
      const count = tx.objectStore("signals").count();
      count.onsuccess = () => {
        db.close();
        resolve(count.result);
      };
      count.onerror = () => {
        db.close();
        resolve(0);
      };
    };
  });
}

function readStorage() {
  const iid = localStorage.getItem("pulse_installation_id") ?? "—";
  const raw = localStorage.getItem("pulse_sdk_config");
  let configVersion = "—";
  if (raw) {
    try {
      configVersion = `v${(JSON.parse(raw) as { version: number }).version}`;
    } catch {
      configVersion = "invalid JSON";
    }
  }
  return { iid, configVersion };
}

export function PulseDebugPanel() {
  if (!import.meta.env.DEV) return null;

  const [open, setOpen] = useState(false);
  const [calls, setCalls] = useState<OtlpCall[]>([]);
  const [idbCount, setIdbCount] = useState(0);
  const [storage, setStorage] = useState({ iid: "—", configVersion: "—" });
  const [sdkReady, setSdkReady] = useState(false);
  const origFetch = useRef<typeof window.fetch | null>(null);

  // Keyboard toggle
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.shiftKey && e.key === "P") setOpen((v) => !v);
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);

  // Intercept fetch to capture OTLP traffic
  useEffect(() => {
    origFetch.current = window.fetch.bind(window);
    window.fetch = async (...args: Parameters<typeof window.fetch>) => {
      const res = await origFetch.current!(...args);
      const url =
        typeof args[0] === "string" ? args[0] : (args[0] as Request).url;
      const match = url.match(/\/v1\/(traces|logs|metrics)/);
      if (match) {
        // Clone to read Content-Length without consuming the body
        const cloned = res.clone();
        const buf = await cloned.arrayBuffer().catch(() => new ArrayBuffer(0));
        const endpoint = match[1] ?? "otlp";
        setCalls((prev) => [
          {
            endpoint,
            time: new Date().toLocaleTimeString(),
            status: res.status,
            sizeBytes: buf.byteLength,
          },
          ...prev.slice(0, 29),
        ]);
      }
      return res;
    };
    return () => {
      if (origFetch.current) window.fetch = origFetch.current;
    };
  }, []);

  const refresh = useCallback(async () => {
    setStorage(readStorage());
    setIdbCount(await countIdbSignals());
    const pw = (window as unknown as Record<string, unknown>)["PulseWeb"] as
      | PulseWebInstance
      | undefined;
    setSdkReady(pw?.isInitialized?.() ?? false);
  }, []);

  // Refresh every second when panel is open
  useEffect(() => {
    if (!open) return;
    refresh();
    const id = setInterval(refresh, 1000);
    return () => clearInterval(id);
  }, [open, refresh]);

  // Shorten UUID for display
  const short = (uuid: string) => (uuid === "—" ? "—" : `…${uuid.slice(-12)}`);

  if (!open) {
    return (
      <div
        style={BADGE_STYLE}
        onClick={() => setOpen(true)}
        title="Pulse Debug (Shift+P)"
      >
        P
      </div>
    );
  }

  return (
    <div style={PANEL_STYLE}>
      {/* Header */}
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 10,
        }}
      >
        <span style={{ color: "#818cf8", fontWeight: 700, fontSize: 12 }}>
          ◉ Pulse SDK Debug
        </span>
        <button
          onClick={() => setOpen(false)}
          style={{
            background: "none",
            border: "none",
            color: "#94a3b8",
            cursor: "pointer",
            fontSize: 14,
            padding: 0,
          }}
        >
          ✕
        </button>
      </div>

      {/* SDK State */}
      <div style={{ marginBottom: 10 }}>
        <div
          style={{
            color: "#64748b",
            fontSize: 10,
            marginBottom: 4,
            textTransform: "uppercase",
            letterSpacing: 1,
          }}
        >
          SDK State
        </div>
        <div style={ROW}>
          <span style={LABEL}>initialized</span>
          <span style={{ color: sdkReady ? "#34d399" : "#f87171" }}>
            {sdkReady ? "✓ yes" : "✗ no"}
          </span>
        </div>
      </div>

      {/* LocalStorage */}
      <div style={{ marginBottom: 10 }}>
        <div
          style={{
            color: "#64748b",
            fontSize: 10,
            marginBottom: 4,
            textTransform: "uppercase",
            letterSpacing: 1,
          }}
        >
          LocalStorage
        </div>
        <div style={ROW}>
          <span style={LABEL}>pulse_installation_id</span>
          <span style={VALUE} title={storage.iid}>
            {short(storage.iid)}
          </span>
        </div>
        <div style={ROW}>
          <span style={LABEL}>pulse_sdk_config</span>
          <span
            style={{
              color: storage.configVersion === "—" ? "#94a3b8" : "#a5f3fc",
            }}
          >
            {storage.configVersion}
          </span>
        </div>
        <div style={ROW}>
          <span style={LABEL}>IDB signal buffer</span>
          <span style={{ color: idbCount > 0 ? "#fbbf24" : "#94a3b8" }}>
            {idbCount} pending
          </span>
        </div>
      </div>

      {/* OTLP Traffic */}
      <div>
        <div
          style={{
            color: "#64748b",
            fontSize: 10,
            marginBottom: 4,
            textTransform: "uppercase",
            letterSpacing: 1,
          }}
        >
          OTLP Calls ({calls.length})
          {calls.length > 0 && (
            <button
              onClick={() => setCalls([])}
              style={{
                background: "none",
                border: "none",
                color: "#475569",
                cursor: "pointer",
                fontSize: 9,
                marginLeft: 8,
              }}
            >
              clear
            </button>
          )}
        </div>
        {calls.length === 0 ? (
          <div style={{ color: "#475569", paddingTop: 4 }}>
            No calls yet — waiting for batch flush…
          </div>
        ) : (
          calls.map((c, i) => (
            <div key={i} style={{ ...ROW, alignItems: "center" }}>
              <span style={{ color: "#64748b", minWidth: 62 }}>{c.time}</span>
              {tag(c.endpoint)}
              <span
                style={{
                  color: c.status === 200 ? "#34d399" : "#f87171",
                  marginLeft: 4,
                }}
              >
                {c.status}
              </span>
              <span style={{ color: "#475569", marginLeft: "auto" }}>
                {(c.sizeBytes / 1024).toFixed(1)}KB
              </span>
            </div>
          ))
        )}
      </div>

      {/* Quick actions */}
      <div
        style={{
          marginTop: 12,
          paddingTop: 8,
          borderTop: "1px solid #1e293b",
          display: "flex",
          gap: 8,
        }}
      >
        <button
          onClick={() => {
            const p = (window as unknown as Record<string, unknown>)[
              "PulseWeb"
            ] as { trackEvent?: (n: string) => void } | undefined;
            p?.trackEvent?.("debug_ping");
          }}
          style={{
            background: "#1e293b",
            border: "none",
            color: "#94a3b8",
            borderRadius: 4,
            padding: "3px 8px",
            cursor: "pointer",
            fontSize: 10,
          }}
          title="Emits a debug_ping span to trigger a batch flush"
        >
          ping
        </button>
        <button
          onClick={refresh}
          style={{
            background: "#1e293b",
            border: "none",
            color: "#94a3b8",
            borderRadius: 4,
            padding: "3px 8px",
            cursor: "pointer",
            fontSize: 10,
          }}
        >
          refresh
        </button>
        <span
          style={{
            color: "#334155",
            fontSize: 9,
            marginLeft: "auto",
            alignSelf: "center",
          }}
        >
          Shift+P to toggle
        </span>
      </div>
    </div>
  );
}
