/**
 * /pages-demo/error-demo — Pages Router error demo.
 * Tests manual error reporting in Pages Router context.
 */
import React, { useState } from "react";
import { PagesNavBar } from "../../components/pages-nav";
import { PulseErrorBoundary } from "@dreamhorizon/pulse-web/next";
import { Pulse } from "@dreamhorizon/pulse-web";

function ThrowingComponent(): React.JSX.Element {
  throw new Error("Boundary crash from pages-demo/error-demo");
}

export default function PagesDemoErrorDemo(): React.JSX.Element {
  const [shouldThrow, setShouldThrow] = useState(false);

  return (
    <>
      <PagesNavBar />
      <main style={{ padding: "1rem" }}>
        <h1>Error Demo (Pages Router)</h1>

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
            data-testid="pages-throw-btn"
            onClick={() => setShouldThrow(true)}
            style={{ marginTop: "0.5rem" }}
          >
            Throw in boundary
          </button>
        </section>

        <section style={{ marginBottom: "1rem" }}>
          <h2>Manual reportException</h2>
          <button
            data-testid="pages-manual-exception-btn"
            onClick={() => {
              Pulse.reportException(
                new Error("Pages Router — manual non_fatal"),
              );
            }}
          >
            Report non_fatal
          </button>
        </section>

        <section>
          <h2>Manual reportDeviceCrash</h2>
          <button
            data-testid="pages-manual-crash-btn"
            onClick={() => {
              Pulse.reportDeviceCrash(
                new Error("Pages Router — manual device.crash"),
              );
            }}
          >
            Report device.crash
          </button>
        </section>
      </main>
    </>
  );
}
