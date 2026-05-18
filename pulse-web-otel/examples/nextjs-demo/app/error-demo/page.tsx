"use client";

import React, { useState } from "react";
import { PulseErrorBoundary } from "@dreamhorizonorg/pulse-web/next";
import { Pulse } from "@dreamhorizonorg/pulse-web";

function ThrowingComponent(): React.JSX.Element {
  throw new Error("Boundary crash from error-demo");
}

export default function ErrorDemoPage(): React.JSX.Element {
  const [shouldThrow, setShouldThrow] = useState(false);

  return (
    <div>
      <h1>Error Demo</h1>

      <section style={{ marginBottom: "1rem" }}>
        <h2>PulseErrorBoundary crash</h2>
        <PulseErrorBoundary
          fallback={(err) => (
            <p style={{ color: "red" }}>Caught: {err.message}</p>
          )}
        >
          {shouldThrow ? <ThrowingComponent /> : <p>Component is healthy.</p>}
        </PulseErrorBoundary>
        <button
          data-testid="throw-btn"
          onClick={() => setShouldThrow(true)}
          style={{ marginTop: "0.5rem" }}
        >
          Throw in boundary
        </button>
      </section>

      <section style={{ marginBottom: "1rem" }}>
        <h2>Manual reportException</h2>
        <button
          data-testid="manual-exception-btn"
          onClick={() => {
            Pulse.reportException(new Error("Manual non_fatal error"));
          }}
        >
          Report non_fatal
        </button>
      </section>

      <section style={{ marginBottom: "1rem" }}>
        <h2>Manual reportDeviceCrash</h2>
        <button
          data-testid="manual-crash-btn"
          onClick={() => {
            Pulse.reportDeviceCrash(new Error("Manual device.crash error"));
          }}
        >
          Report device.crash
        </button>
      </section>

      <section style={{ marginBottom: "1rem" }}>
        <h2>Burst error (dedup test)</h2>
        <button
          data-testid="throw-burst"
          onClick={() => {
            for (let i = 0; i < 3; i++) {
              window.dispatchEvent(
                new ErrorEvent("error", {
                  message: "Burst dedup error",
                  filename: "error-demo.tsx",
                  lineno: 1,
                  colno: 1,
                  error: new Error("Burst dedup error"),
                }),
              );
            }
          }}
        >
          Throw burst (3×)
        </button>
      </section>

      <section>
        <h2>TypeError (class name test)</h2>
        <button
          data-testid="throw-type-error"
          onClick={() => {
            window.dispatchEvent(
              new ErrorEvent("error", {
                message: "type fail",
                filename: "error-demo.tsx",
                lineno: 1,
                colno: 1,
                error: new TypeError("type fail"),
              }),
            );
          }}
        >
          Throw TypeError
        </button>
      </section>
    </div>
  );
}
