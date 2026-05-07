"use client";

/**
 * /api-demo — demonstrates tracking client-side fetch success/failure.
 *
 * Q4 from design review: "if there's an API call on SSR and it has some event
 * on success, does it get processed?"
 *
 * Answer: NO for SSR — `Pulse` is not initialized server-side.
 * YES for client-side fetch — as shown here.
 *
 * Pattern:
 *   fetch('/api/data')
 *     .then(r => r.ok ? Pulse.trackEvent('api_success') : Pulse.reportException())
 *     .catch(err => Pulse.reportException(err))
 */
import React, { useState } from "react";
import { Pulse } from "@dreamhorizonorg/pulse-web";

interface ApiData {
  activeUsers: number;
  ordersToday: number;
  revenue: string;
  timestamp: string;
}

type FetchState =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "success"; data: ApiData }
  | { status: "error"; message: string };

export default function ApiDemoPage(): React.JSX.Element {
  const [state, setState] = useState<FetchState>({ status: "idle" });

  async function fetchData(): Promise<void> {
    setState({ status: "loading" });

    try {
      const res = await fetch("/api/data");
      if (!res.ok) {
        const body = (await res.json()) as { error?: string };
        const err = new Error(body.error ?? `HTTP ${res.status}`);

        // API call failed — report as non-fatal (operation-level error, not a crash)
        Pulse.reportException(err, {
          "api.endpoint": "/api/data",
          "api.status": res.status,
        });

        setState({ status: "error", message: err.message });
        return;
      }

      const data = (await res.json()) as ApiData;

      // API call succeeded — track business event
      Pulse.trackEvent("dashboard_data_loaded", {
        active_users: data.activeUsers,
        orders_today: data.ordersToday,
      });

      setState({ status: "success", data });
    } catch (err) {
      // Network error — report as exception
      Pulse.reportException(
        err instanceof Error ? err : new Error(String(err)),
        { "api.endpoint": "/api/data" },
      );
      setState({
        status: "error",
        message: err instanceof Error ? err.message : "Network error",
      });
    }
  }

  return (
    <div>
      <h1>API Call Tracking Demo</h1>
      <p style={{ color: "#555", marginBottom: "0.5rem" }}>
        Calls <code>GET /api/data</code> and tracks the result with Pulse.
      </p>
      <ul
        style={{
          fontSize: "0.85rem",
          color: "#6b7280",
          marginBottom: "1.5rem",
          lineHeight: "1.8",
        }}
      >
        <li>
          <strong>Success (70%):</strong>{" "}
          <code>
            Pulse.trackEvent("dashboard_data_loaded", &#123; ... &#125;)
          </code>
        </li>
        <li>
          <strong>Failure (30%):</strong>{" "}
          <code>Pulse.reportException(error, &#123; api.endpoint &#125;)</code>
        </li>
      </ul>

      <button
        data-testid="fetch-btn"
        onClick={() => void fetchData()}
        disabled={state.status === "loading"}
        style={{
          padding: "0.75rem 1.5rem",
          background: state.status === "loading" ? "#9ca3af" : "#1d4ed8",
          color: "#fff",
          border: "none",
          borderRadius: "6px",
          cursor: state.status === "loading" ? "not-allowed" : "pointer",
          fontSize: "1rem",
        }}
      >
        {state.status === "loading" ? "Fetching…" : "Fetch Dashboard Data"}
      </button>

      {state.status === "success" && (
        <div
          data-testid="fetch-success"
          style={{
            marginTop: "1rem",
            padding: "1rem",
            background: "#f0fdf4",
            border: "1px solid #bbf7d0",
            borderRadius: "6px",
          }}
        >
          <p
            style={{
              color: "#15803d",
              fontWeight: "bold",
              marginBottom: "0.5rem",
            }}
          >
            ✓ Success — trackEvent("dashboard_data_loaded") fired
          </p>
          <table style={{ fontSize: "0.9rem", borderCollapse: "collapse" }}>
            <tbody>
              <tr>
                <td style={{ padding: "2px 1rem 2px 0", color: "#6b7280" }}>
                  Active Users
                </td>
                <td>{state.data.activeUsers}</td>
              </tr>
              <tr>
                <td style={{ padding: "2px 1rem 2px 0", color: "#6b7280" }}>
                  Orders Today
                </td>
                <td>{state.data.ordersToday}</td>
              </tr>
              <tr>
                <td style={{ padding: "2px 1rem 2px 0", color: "#6b7280" }}>
                  Revenue
                </td>
                <td>${state.data.revenue}</td>
              </tr>
            </tbody>
          </table>
        </div>
      )}

      {state.status === "error" && (
        <div
          data-testid="fetch-error"
          style={{
            marginTop: "1rem",
            padding: "1rem",
            background: "#fef2f2",
            border: "1px solid #fecaca",
            borderRadius: "6px",
          }}
        >
          <p
            style={{
              color: "#dc2626",
              fontWeight: "bold",
              marginBottom: "0.25rem",
            }}
          >
            ✗ Error — reportException() fired
          </p>
          <code style={{ fontSize: "0.85rem" }}>{state.message}</code>
        </div>
      )}
    </div>
  );
}
