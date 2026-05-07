"use client";

import React, { useState, useEffect, useRef, useCallback } from "react";

interface OtlpCall {
  endpoint: string;
  time: string;
  status: number;
  sizeBytes: number;
}

type PulseSdkWindow = {
  isInitialized: () => boolean;
};

export function PulseDebugPanel() {
  const [open, setOpen] = useState(false);
  const [otlpCalls, setOtlpCalls] = useState<OtlpCall[]>([]);
  const [sessionId, setSessionId] = useState<string>("—");
  const [installId, setInstallId] = useState<string>("—");
  const [idbCount, setIdbCount] = useState<number | null>(null);
  const origFetch = useRef<typeof fetch | null>(null);

  // Only render in dev
  if (process.env.NODE_ENV === "production") return null;

  // eslint-disable-next-line react-hooks/rules-of-hooks
  const refresh = useCallback(() => {
    setInstallId(localStorage.getItem("pulse_installation_id") ?? "—");
    const raw = localStorage.getItem("pulse_session");
    try {
      const parsed = raw ? (JSON.parse(raw) as { id?: string }) : null;
      setSessionId(parsed?.id ?? "—");
    } catch {
      setSessionId("—");
    }
  }, []);

  // eslint-disable-next-line react-hooks/rules-of-hooks
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.shiftKey && e.key === "P") setOpen((o) => !o);
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, []);

  // eslint-disable-next-line react-hooks/rules-of-hooks
  useEffect(() => {
    if (!open) return;
    refresh();

    origFetch.current = window.fetch;
    const patched: typeof fetch = async (input, init) => {
      const url = typeof input === "string" ? input : (input as Request).url;
      const res = await origFetch.current!(input, init);
      if (url.includes("/v1/")) {
        const endpoint = url.includes("traces")
          ? "traces"
          : url.includes("logs")
            ? "logs"
            : url.includes("metrics")
              ? "metrics"
              : url;
        const sizeBytes = Number(
          res.headers.get("content-length") ??
            init?.body?.toString().length ??
            0,
        );
        setOtlpCalls((prev) =>
          [
            {
              endpoint,
              time: new Date().toLocaleTimeString(),
              status: res.status,
              sizeBytes,
            },
            ...prev,
          ].slice(0, 50),
        );
      }
      return res;
    };
    window.fetch = patched;

    return () => {
      if (origFetch.current) window.fetch = origFetch.current;
    };
  }, [open, refresh]);

  const pulse =
    typeof window !== "undefined"
      ? ((window as unknown as Record<string, unknown>)["Pulse"] as
          | PulseSdkWindow
          | undefined)
      : undefined;

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        title="Open Pulse Debug Panel (Shift+P)"
        style={{
          position: "fixed",
          bottom: 80,
          right: 16,
          width: 32,
          height: 32,
          borderRadius: 16,
          background: "#6366f1",
          color: "#fff",
          fontWeight: 800,
          fontSize: 14,
          border: "none",
          cursor: "pointer",
          zIndex: 9999,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        P
      </button>
    );
  }

  return (
    <div
      style={{
        position: "fixed",
        bottom: 80,
        right: 16,
        width: 320,
        maxHeight: "65vh",
        overflowY: "auto",
        background: "#0f172a",
        color: "#e2e8f0",
        fontFamily: "monospace",
        fontSize: 11,
        borderRadius: 8,
        boxShadow: "0 8px 32px rgba(0,0,0,.5)",
        zIndex: 9999,
        padding: "12px 14px",
      }}
    >
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          marginBottom: 8,
        }}
      >
        <span style={{ fontWeight: 700, color: "#6366f1" }}>Pulse Debug</span>
        <button
          onClick={() => setOpen(false)}
          style={{
            background: "none",
            border: "none",
            color: "#94a3b8",
            cursor: "pointer",
            fontSize: 12,
          }}
        >
          ✕
        </button>
      </div>

      <div style={{ color: "#94a3b8", marginBottom: 8 }}>
        <div>
          SDK: {pulse?.isInitialized() ? "✅ initialized" : "⏳ not ready"}
        </div>
        <div>
          Session:{" "}
          <span style={{ color: "#fbbf24" }}>{sessionId.slice(0, 8)}…</span>
        </div>
        <div>
          Install:{" "}
          <span style={{ color: "#fbbf24" }}>{installId.slice(0, 8)}…</span>
        </div>
        {idbCount !== null && <div>IDB buffer: {idbCount} rows</div>}
      </div>

      <div
        style={{ borderTop: "1px solid #1e293b", paddingTop: 8, marginTop: 4 }}
      >
        <div
          style={{
            color: "#64748b",
            marginBottom: 4,
            fontSize: 10,
            textTransform: "uppercase",
          }}
        >
          OTLP calls ({otlpCalls.length})
        </div>
        {otlpCalls.length === 0 && (
          <div style={{ color: "#475569" }}>No OTLP traffic yet…</div>
        )}
        {otlpCalls.map((c, i) => (
          <div
            key={i}
            style={{
              color: c.status < 300 ? "#4ade80" : "#f87171",
              marginBottom: 2,
            }}
          >
            [{c.time}] {c.endpoint} {c.status}
            {c.sizeBytes > 0 ? ` (${c.sizeBytes}b)` : ""}
          </div>
        ))}
      </div>

      <div
        style={{ marginTop: 8, borderTop: "1px solid #1e293b", paddingTop: 8 }}
      >
        <button
          onClick={refresh}
          style={{
            background: "#1e293b",
            border: "none",
            color: "#94a3b8",
            borderRadius: 4,
            padding: "4px 8px",
            cursor: "pointer",
            fontSize: 10,
          }}
        >
          Refresh state
        </button>
      </div>
    </div>
  );
}
