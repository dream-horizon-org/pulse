import React, { useState } from "react";
import { Pulse } from "@dreamhorizonorg/pulse-web";

function RenderBomb(): React.ReactNode {
  throw new Error("Intentional render error from ErrorDemo");
}

const btn = (color: string): React.CSSProperties => ({
  background: color,
  color: "#fff",
  border: "none",
  borderRadius: 10,
  padding: "10px 18px",
  fontWeight: 600,
  fontSize: 14,
  cursor: "pointer",
  width: "100%",
});

const card: React.CSSProperties = {
  background: "#fff",
  borderRadius: 12,
  padding: 20,
  boxShadow: "0 1px 3px rgba(0,0,0,.08)",
};

const sectionTitle: React.CSSProperties = {
  fontSize: 18,
  fontWeight: 800,
  margin: "0 0 4px",
  color: "#0f172a",
};

function LabCard({
  title,
  hint,
  children,
}: {
  title: string;
  hint: React.ReactNode;
  children: React.ReactNode;
}): React.ReactElement {
  return (
    <div style={card}>
      <p style={{ fontWeight: 700, marginBottom: 4 }}>{title}</p>
      <p
        style={{
          fontSize: 13,
          color: "#94a3b8",
          marginBottom: 14,
          lineHeight: 1.45,
        }}
      >
        {hint}
      </p>
      <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
        {children}
      </div>
    </div>
  );
}

export default function ErrorDemo(): React.ReactElement {
  const [throwRender, setThrowRender] = useState(false);

  return (
    <div style={{ maxWidth: 960, margin: "0 auto" }}>
      <h1 style={{ fontSize: 30, fontWeight: 800, marginBottom: 8 }}>
        Error instrumentation lab
      </h1>
      <p
        style={{
          color: "#64748b",
          marginBottom: 8,
          lineHeight: 1.55,
          maxWidth: 720,
        }}
      >
        Exercises the <code>@dreamhorizonorg/pulse-web</code> SDK error
        pipelines. Expected OTLP log <code>pulse.type</code> values are called
        out per action. Screen context should be <code>/error-demo</code>. Open
        DevTools → Network to inspect OTLP, or use the in-app debug panel.
      </p>
      <p style={{ fontSize: 13, color: "#94a3b8", marginBottom: 28 }}>
        Tip: after a <strong>React render crash</strong>, use &quot;Dismiss
        &amp; reload lab&quot; on the recovery screen so the route remounts
        cleanly.
      </p>

      <h2 style={sectionTitle}>
        A — Device crash (<code>device.crash</code>)
      </h2>
      <p style={{ fontSize: 14, color: "#64748b", marginBottom: 16 }}>
        Unhandled JS errors and manual fatals. Severity FATAL in OTLP.
      </p>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
          gap: 16,
          marginBottom: 28,
        }}
      >
        <LabCard
          title="Uncaught error (async throw)"
          hint={
            <>
              <code>window.onerror</code> path → <code>device.crash</code>. Uses{" "}
              <code>setTimeout(0)</code> so the click handler completes.
            </>
          }
        >
          <button
            data-testid="throw-uncaught"
            style={btn("#ef4444")}
            onClick={() => {
              Pulse.trackEvent("error_demo_throw_uncaught");
              setTimeout(() => {
                throw new Error("Demo uncaught error from ErrorDemo");
              }, 0);
            }}
          >
            Throw uncaught error
          </button>
        </LabCard>

        <LabCard
          title="React render error"
          hint={
            <>
              Caught by <code>PulseErrorBoundary</code> →{" "}
              <code>device.crash</code> + <code>react.component_stack</code>.
            </>
          }
        >
          <button
            data-testid="throw-render-error"
            style={btn("#8b5cf6")}
            onClick={() => {
              Pulse.trackEvent("error_demo_throw_render");
              setThrowRender(true);
            }}
          >
            Throw in render
          </button>
          {throwRender ? <RenderBomb /> : null}
        </LabCard>

        <LabCard
          title="Manual reportDeviceCrash"
          hint={
            <>
              Calls <code>Pulse.reportDeviceCrash()</code> — same{" "}
              <code>pulse.type</code> as other fatals; stack from the Error you
              pass.
            </>
          }
        >
          <button
            data-testid="report-device-crash-manual"
            style={btn("#b91c1c")}
            onClick={() =>
              Pulse.reportDeviceCrash(
                new Error("Manual reportDeviceCrash from ErrorDemo"),
                {
                  context: "error-demo-manual-fatal",
                },
              )
            }
          >
            reportDeviceCrash(…)
          </button>
        </LabCard>

        <LabCard
          title="Dedupe burst (same fingerprint)"
          hint={
            <>
              Four identical <code>ErrorEvent</code>s → expect{" "}
              <strong>one</strong> export within the dedupe window (M3 E2E).
            </>
          }
        >
          <button
            data-testid="throw-uncaught-burst"
            style={btn("#dc2626")}
            onClick={() => {
              Pulse.trackEvent("error_demo_throw_uncaught_burst");
              for (let i = 0; i < 4; i += 1) {
                window.dispatchEvent(
                  new ErrorEvent("error", {
                    message: "Demo dedupe burst error",
                    filename: "error-demo.tsx",
                    lineno: 201,
                    colno: 11,
                    error: new Error("Demo dedupe burst error"),
                  }),
                );
              }
            }}
          >
            Dispatch uncaught burst ×4
          </button>
        </LabCard>

        <LabCard
          title="Two distinct fingerprints"
          hint="Same filename, different line/column — two separate device.crash groups."
        >
          <button
            data-testid="throw-fingerprint-a"
            style={btn("#be123c")}
            onClick={() => {
              Pulse.trackEvent("error_demo_fingerprint_a");
              window.dispatchEvent(
                new ErrorEvent("error", {
                  message: "Distinct error A",
                  filename: "error-demo.tsx",
                  lineno: 301,
                  colno: 7,
                  error: new Error("Distinct error A"),
                }),
              );
            }}
          >
            Distinct crash A (301:7)
          </button>
          <button
            data-testid="throw-fingerprint-b"
            style={btn("#be123c")}
            onClick={() => {
              Pulse.trackEvent("error_demo_fingerprint_b");
              window.dispatchEvent(
                new ErrorEvent("error", {
                  message: "Distinct error B",
                  filename: "error-demo.tsx",
                  lineno: 302,
                  colno: 8,
                  error: new Error("Distinct error B"),
                }),
              );
            }}
          >
            Distinct crash B (302:8)
          </button>
        </LabCard>
      </div>

      <h2 style={sectionTitle}>
        B — Non-fatal (<code>non_fatal</code>)
      </h2>
      <p style={{ fontSize: 14, color: "#64748b", marginBottom: 16 }}>
        Unhandled rejections and manual APIs. Severity WARN.
      </p>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
          gap: 16,
          marginBottom: 28,
        }}
      >
        <LabCard
          title="Handled try/catch (no export)"
          hint={
            <>
              Swallowed error — expect <strong>zero</strong>{" "}
              <code>device.crash</code> (ERR-05).
            </>
          }
        >
          <button
            data-testid="throw-handled-catch"
            style={btn("#64748b")}
            onClick={() => {
              Pulse.trackEvent("error_demo_handled_catch");
              try {
                throw new Error("Handled error swallowed in ErrorDemo");
              } catch {
                /* intentional — must not reach Pulse */
              }
            }}
          >
            Throw in try/catch
          </button>
        </LabCard>

        <LabCard
          title="Unhandled Promise rejection"
          hint={
            <>
              <code>unhandledrejection</code> → <code>non_fatal</code>,{" "}
              <code>non_fatal.is_manual = false</code>.
            </>
          }
        >
          <button
            data-testid="throw-promise"
            style={btn("#f97316")}
            onClick={() => {
              Pulse.trackEvent("error_demo_throw_promise");
              Promise.reject(
                new TypeError("Demo TypeError rejection from ErrorDemo"),
              );
            }}
          >
            Reject unhandled promise (TypeError)
          </button>
        </LabCard>

        <LabCard
          title="Pulse.reportException"
          hint={
            <>
              <code>non_fatal</code> with{" "}
              <code>non_fatal.is_manual = true</code>.
            </>
          }
        >
          <button
            data-testid="report-exception"
            style={btn("#0ea5e9")}
            onClick={() =>
              Pulse.reportException(new Error("Manually reported error"), {
                context: "error-demo",
              })
            }
          >
            reportException(…)
          </button>
        </LabCard>

        <LabCard
          title="Pulse.trackNonFatal"
          hint="Named non-fatal bucket; body is the name you pass."
        >
          <button
            data-testid="track-non-fatal-manual"
            style={btn("#0284c7")}
            onClick={() => {
              Pulse.trackEvent("error_demo_track_non_fatal");
              Pulse.trackNonFatal("checkout_validation_failed", {
                step: "shipping",
              });
            }}
          >
            trackNonFatal(…)
          </button>
        </LabCard>

        <LabCard
          title="Reject with string / undefined"
          hint="SDK normalizes reason → Error with a stable message (M3 E2E)."
        >
          <button
            data-testid="throw-promise-string"
            style={btn("#f59e0b")}
            onClick={() => {
              Pulse.trackEvent("error_demo_throw_promise_string");
              Promise.reject("string");
            }}
          >
            Reject string
          </button>
          <button
            data-testid="throw-promise-undefined"
            style={btn("#f59e0b")}
            onClick={() => {
              Pulse.trackEvent("error_demo_throw_promise_undefined");
              Promise.reject(undefined);
            }}
          >
            Reject undefined
          </button>
        </LabCard>
      </div>

      <h2 style={sectionTitle}>C — Ignored (no Pulse error log)</h2>
      <p style={{ fontSize: 14, color: "#64748b", marginBottom: 16 }}>
        Browser “script error.” signatures are intentionally skipped to avoid
        noise.
      </p>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
          gap: 16,
          marginBottom: 32,
        }}
      >
        <LabCard
          title="Cross-origin script error"
          hint={
            <>
              Classic <code>Script error.</code> with no stack — expect{" "}
              <strong>no</strong> <code>device.crash</code> /{" "}
              <code>non_fatal</code> from Pulse.
            </>
          }
        >
          <button
            data-testid="trigger-cross-origin-script-error"
            style={btn("#64748b")}
            onClick={() => {
              Pulse.trackEvent("error_demo_cross_origin_script_error");
              window.dispatchEvent(
                new ErrorEvent("error", {
                  message: "Script error.",
                  error: null,
                }),
              );
            }}
          >
            Dispatch script error (ignored)
          </button>
        </LabCard>
      </div>
    </div>
  );
}
