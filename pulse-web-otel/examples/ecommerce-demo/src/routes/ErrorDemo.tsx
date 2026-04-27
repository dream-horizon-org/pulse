import React, { useState } from "react";
import { PulseWeb } from "@dreamhorizon/pulse-web";
import { PulseErrorBoundary } from "@dreamhorizon/pulse-web/react";

function RenderBomb(): React.ReactNode {
  throw new Error("Intentional render error from ErrorDemo");
}

const btn = (color: string): React.CSSProperties => ({
  background: color,
  color: "#fff",
  border: "none",
  borderRadius: 10,
  padding: "12px 24px",
  fontWeight: 700,
  fontSize: 14,
  cursor: "pointer",
  width: "100%",
});

export default function ErrorDemo() {
  const [throwRender, setThrowRender] = useState(false);

  return (
    <div style={{ maxWidth: 560, margin: "0 auto" }}>
      <h2 style={{ fontSize: 28, fontWeight: 700, marginBottom: 8 }}>
        Error Demo
      </h2>
      <p style={{ color: "#64748b", marginBottom: 32 }}>
        Trigger different error types to see them appear in the Pulse dashboard.
      </p>

      <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        <div
          style={{
            background: "#fff",
            borderRadius: 12,
            padding: 20,
            boxShadow: "0 1px 3px rgba(0,0,0,.08)",
          }}
        >
          <p style={{ fontWeight: 600, marginBottom: 4 }}>Uncaught error</p>
          <p style={{ fontSize: 13, color: "#94a3b8", marginBottom: 12 }}>
            Fires window.onerror → <code>device.crash</code> log
          </p>
          <button
            data-testid="throw-uncaught"
            style={btn("#ef4444")}
            onClick={() => {
              PulseWeb.trackEvent("error_demo_throw_uncaught");
              setTimeout(() => {
                throw new Error("Demo uncaught error from ErrorDemo");
              }, 0);
            }}
          >
            Throw uncaught error
          </button>
        </div>

        <div
          style={{
            background: "#fff",
            borderRadius: 12,
            padding: 20,
            boxShadow: "0 1px 3px rgba(0,0,0,.08)",
          }}
        >
          <p style={{ fontWeight: 600, marginBottom: 4 }}>
            Unhandled Promise rejection
          </p>
          <p style={{ fontSize: 13, color: "#94a3b8", marginBottom: 12 }}>
            Fires unhandledrejection → <code>non_fatal</code> log
          </p>
          <button
            data-testid="throw-promise"
            style={btn("#f97316")}
            onClick={() => {
              PulseWeb.trackEvent("error_demo_throw_promise");
              Promise.reject(
                new Error("Demo unhandled rejection from ErrorDemo"),
              );
            }}
          >
            Reject unhandled promise
          </button>
        </div>

        <div
          style={{
            background: "#fff",
            borderRadius: 12,
            padding: 20,
            boxShadow: "0 1px 3px rgba(0,0,0,.08)",
          }}
        >
          <p style={{ fontWeight: 600, marginBottom: 4 }}>React render error</p>
          <p style={{ fontSize: 13, color: "#94a3b8", marginBottom: 12 }}>
            Caught by <code>PulseErrorBoundary</code> →{" "}
            <code>device.crash</code> log
          </p>
          <button
            data-testid="throw-render-error"
            style={btn("#8b5cf6")}
            onClick={() => {
              PulseWeb.trackEvent("error_demo_throw_render");
              setThrowRender(true);
            }}
          >
            Throw in render
          </button>
          {throwRender && (
            <div style={{ marginTop: 12 }}>
              <PulseErrorBoundary
                fallback={(error, reset) => (
                  <div
                    style={{
                      background: "#fef2f2",
                      border: "1px solid #fecaca",
                      borderRadius: 12,
                      padding: 24,
                    }}
                  >
                    <p
                      style={{
                        color: "#b91c1c",
                        fontWeight: 700,
                        marginBottom: 8,
                      }}
                    >
                      Render error caught by PulseErrorBoundary
                    </p>
                    <pre
                      style={{
                        fontSize: 12,
                        color: "#991b1b",
                        background: "#fff",
                        padding: 12,
                        borderRadius: 8,
                        overflow: "auto",
                      }}
                    >
                      {error.message}
                    </pre>
                    <button
                      type="button"
                      onClick={reset}
                      style={{
                        marginTop: 12,
                        background: "#ef4444",
                        color: "#fff",
                        border: "none",
                        borderRadius: 8,
                        padding: "8px 16px",
                        cursor: "pointer",
                        fontWeight: 600,
                      }}
                    >
                      Reset
                    </button>
                  </div>
                )}
              >
                <RenderBomb />
              </PulseErrorBoundary>
            </div>
          )}
        </div>

        <div
          style={{
            background: "#fff",
            borderRadius: 12,
            padding: 20,
            boxShadow: "0 1px 3px rgba(0,0,0,.08)",
          }}
        >
          <p style={{ fontWeight: 600, marginBottom: 4 }}>
            Manual reportException
          </p>
          <p style={{ fontSize: 13, color: "#94a3b8", marginBottom: 12 }}>
            Calls PulseWeb.reportException() → <code>non_fatal</code> log
          </p>
          <button
            style={btn("#0ea5e9")}
            onClick={() =>
              PulseWeb.reportException(new Error("Manually reported error"), {
                context: "error-demo",
              })
            }
          >
            Report exception
          </button>
        </div>
      </div>
    </div>
  );
}
