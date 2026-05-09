import React from "react";
import { useNavigate } from "react-router-dom";

const btnBase: React.CSSProperties = {
  border: "none",
  borderRadius: 10,
  padding: "12px 20px",
  fontWeight: 600,
  fontSize: 14,
  cursor: "pointer",
};

/**
 * Shown when {@link PulseErrorBoundary} catches a render error. Pair with a remount key
 * on {@link ErrorDemo} so "retry" does not immediately re-throw from stale React state.
 */
export function EcommerceErrorFallback({
  error,
  reset,
  onRecover,
}: {
  error: Error;
  reset: () => void;
  onRecover: () => void;
}): React.ReactElement {
  const navigate = useNavigate();

  const recover = (): void => {
    onRecover();
    reset();
  };

  return (
    <div
      style={{
        minHeight: "70vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 24,
        background: "#f8fafc",
      }}
    >
      <div
        style={{
          maxWidth: 480,
          textAlign: "center",
          background: "#fff",
          borderRadius: 16,
          padding: "32px 28px",
          boxShadow: "0 4px 24px rgba(0,0,0,.08)",
        }}
      >
        <div style={{ fontSize: 40, marginBottom: 12 }}>⚠️</div>
        <h2 style={{ fontSize: 22, fontWeight: 800, margin: "0 0 8px" }}>
          Render error caught
        </h2>
        <p
          style={{
            color: "#64748b",
            fontSize: 14,
            marginBottom: 12,
            wordBreak: "break-word",
            fontFamily: "ui-monospace, monospace",
          }}
        >
          {error.message}
        </p>
        <p
          style={{
            color: "#94a3b8",
            fontSize: 13,
            lineHeight: 1.5,
            marginBottom: 24,
          }}
        >
          Pulse should have emitted a <code>device.crash</code> log with{" "}
          <code>react.component_stack</code>. Continue testing below.
        </p>
        <div
          style={{
            display: "flex",
            flexWrap: "wrap",
            gap: 10,
            justifyContent: "center",
          }}
        >
          <button
            type="button"
            style={{ ...btnBase, background: "#4f46e5", color: "#fff" }}
            onClick={recover}
          >
            Dismiss &amp; reload lab
          </button>
          <button
            type="button"
            style={{
              ...btnBase,
              background: "#fff",
              color: "#4f46e5",
              border: "2px solid #4f46e5",
            }}
            onClick={() => {
              recover();
              void navigate("/error-demo");
            }}
          >
            Error lab
          </button>
          <button
            type="button"
            style={{
              ...btnBase,
              background: "#fff",
              color: "#475569",
              border: "2px solid #e2e8f0",
            }}
            onClick={() => {
              recover();
              void navigate("/");
            }}
          >
            Home
          </button>
        </div>
      </div>
    </div>
  );
}
