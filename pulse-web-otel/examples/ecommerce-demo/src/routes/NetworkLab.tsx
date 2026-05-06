import React, { useEffect, useMemo, useState } from "react";

type ScenarioKind = "fetch" | "xhr";

type ScenarioStatus = "idle" | "running" | "ok" | "error";

type ScenarioResult = {
  status: ScenarioStatus;
  details?: string;
};

type Scenario = {
  id: string;
  label: string;
  kind: ScenarioKind;
  expected: string;
  /** Calls jsonplaceholder / httpstat / example.com — runnable only in Vite dev (Playwright uses dev). */
  thirdParty?: boolean;
  run: () => Promise<string>;
};

type XhrRequest = {
  method: "GET" | "POST" | "PUT" | "DELETE";
  url: string;
  body?: string;
  headers?: Record<string, string>;
  timeoutMs?: number;
  abortAfterMs?: number;
};

function runXhr(request: XhrRequest): Promise<string> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open(request.method, request.url, true);
    if (request.timeoutMs !== undefined) {
      xhr.timeout = request.timeoutMs;
    }
    if (request.headers) {
      for (const [key, value] of Object.entries(request.headers)) {
        xhr.setRequestHeader(key, value);
      }
    }

    xhr.onload = () => {
      resolve(`status=${xhr.status}`);
    };
    xhr.onerror = () => {
      reject(new Error("xhr.onerror"));
    };
    xhr.ontimeout = () => {
      reject(new Error("xhr.timeout"));
    };
    xhr.onabort = () => {
      reject(new Error("xhr.abort"));
    };

    if (request.abortAfterMs !== undefined) {
      window.setTimeout(() => xhr.abort(), request.abortAfterMs);
    }
    xhr.send(request.body);
  });
}

export default function NetworkLab() {
  const [results, setResults] = useState<Record<string, ScenarioResult>>({});
  const thirdPartyDemoAllowed = import.meta.env.DEV;

  useEffect(() => {
    if (!thirdPartyDemoAllowed) {
      console.info(
        "[NetworkLab] Third-party demo scenarios are hidden in production builds.",
      );
    }
  }, [thirdPartyDemoAllowed]);

  const scenarios = useMemo<Scenario[]>(
    () => [
      {
        id: "fetch-get-local",
        label: "Fetch GET local JSON",
        kind: "fetch",
        expected: "network.200",
        run: async () => {
          const response = await fetch("/api/products.json");
          return `status=${response.status}`;
        },
      },
      {
        id: "fetch-get-query",
        label: "Fetch GET local with query params",
        kind: "fetch",
        expected: "network.200 (url.full query stripped by default)",
        run: async () => {
          const response = await fetch(
            "/api/product-detail.json?id=1&debug=true",
          );
          return `status=${response.status}`;
        },
      },
      {
        id: "fetch-post-json",
        label: "Fetch POST JSON body",
        kind: "fetch",
        thirdParty: true,
        expected: "network.201 or network.200",
        run: async () => {
          const response = await fetch(
            "https://jsonplaceholder.typicode.com/posts",
            {
              method: "POST",
              headers: {
                "Content-Type": "application/json",
                "X-Pulse-Demo": "fetch-post-json",
              },
              body: JSON.stringify({
                title: "pulse-demo",
                body: "fetch post scenario",
                userId: 1,
              }),
            },
          );
          return `status=${response.status}`;
        },
      },
      {
        id: "fetch-put-json",
        label: "Fetch PUT JSON body",
        kind: "fetch",
        thirdParty: true,
        expected: "network.200",
        run: async () => {
          const response = await fetch(
            "https://jsonplaceholder.typicode.com/posts/1",
            {
              method: "PUT",
              headers: {
                "Content-Type": "application/json",
              },
              body: JSON.stringify({
                id: 1,
                title: "updated",
                body: "put scenario",
                userId: 1,
              }),
            },
          );
          return `status=${response.status}`;
        },
      },
      {
        id: "fetch-delete",
        label: "Fetch DELETE request",
        kind: "fetch",
        thirdParty: true,
        expected: "network.200",
        run: async () => {
          const response = await fetch(
            "https://jsonplaceholder.typicode.com/posts/1",
            {
              method: "DELETE",
            },
          );
          return `status=${response.status}`;
        },
      },
      {
        id: "fetch-404",
        label: "Fetch 404 local missing file",
        kind: "fetch",
        expected: "network.404 + error.type=4xx",
        run: async () => {
          const response = await fetch("/api/does-not-exist.json");
          return `status=${response.status}`;
        },
      },
      {
        id: "fetch-500",
        label: "Fetch 500 remote endpoint",
        kind: "fetch",
        thirdParty: true,
        expected: "network.500 (or network.0 if CORS blocks)",
        run: async () => {
          const response = await fetch("https://httpstat.us/500");
          return `status=${response.status}`;
        },
      },
      {
        id: "fetch-abort",
        label: "Fetch AbortController immediate abort",
        kind: "fetch",
        thirdParty: true,
        expected: "network.0 + error.type=network_error",
        run: async () => {
          const controller = new AbortController();
          const req = fetch("https://httpstat.us/200?sleep=4000", {
            signal: controller.signal,
          }).catch(() => undefined);
          controller.abort();
          await req;
          return "aborted";
        },
      },
      {
        id: "fetch-timeout",
        label: "Fetch timeout-style abort after 800ms",
        kind: "fetch",
        thirdParty: true,
        expected: "network.0 + error.type=network_error",
        run: async () => {
          const controller = new AbortController();
          const timer = window.setTimeout(() => controller.abort(), 800);
          try {
            await fetch("https://httpstat.us/200?sleep=5000", {
              signal: controller.signal,
            });
            return "unexpected-success";
          } finally {
            window.clearTimeout(timer);
          }
        },
      },
      {
        id: "fetch-no-cors",
        label: "Fetch no-cors opaque request",
        kind: "fetch",
        thirdParty: true,
        expected: "network.0 (opaque/cors path)",
        run: async () => {
          await fetch("https://example.com", {
            mode: "no-cors",
            credentials: "omit",
          });
          return "opaque-response";
        },
      },
      {
        id: "xhr-get-local",
        label: "XHR GET local JSON",
        kind: "xhr",
        expected: "network.200",
        run: async () => {
          return runXhr({
            method: "GET",
            url: "/api/products.json",
          });
        },
      },
      {
        id: "xhr-post-json",
        label: "XHR POST JSON",
        kind: "xhr",
        thirdParty: true,
        expected: "network.201 or network.200",
        run: async () => {
          return runXhr({
            method: "POST",
            url: "https://jsonplaceholder.typicode.com/posts",
            headers: {
              "Content-Type": "application/json",
              "X-Pulse-Demo": "xhr-post-json",
            },
            body: JSON.stringify({
              title: "xhr-post",
              body: "manual demo",
              userId: 2,
            }),
          });
        },
      },
      {
        id: "xhr-404",
        label: "XHR 404 local missing file",
        kind: "xhr",
        expected: "network.404 + error.type=4xx",
        run: async () => {
          return runXhr({
            method: "GET",
            url: "/api/missing-xhr.json",
          });
        },
      },
      {
        id: "xhr-timeout",
        label: "XHR timeout 700ms",
        kind: "xhr",
        thirdParty: true,
        expected: "network.0 + error.type=network_error",
        run: async () => {
          return runXhr({
            method: "GET",
            url: "https://httpstat.us/200?sleep=5000",
            timeoutMs: 700,
          });
        },
      },
      {
        id: "xhr-abort",
        label: "XHR abort after 200ms",
        kind: "xhr",
        thirdParty: true,
        expected: "network.0 + error.type=network_error",
        run: async () => {
          return runXhr({
            method: "GET",
            url: "https://httpstat.us/200?sleep=5000",
            abortAfterMs: 200,
          });
        },
      },
    ],
    [],
  );

  async function runScenario(scenario: Scenario): Promise<void> {
    setResults((prev) => ({
      ...prev,
      [scenario.id]: { status: "running" },
    }));
    try {
      const details = await scenario.run();
      setResults((prev) => ({
        ...prev,
        [scenario.id]: { status: "ok", details },
      }));
    } catch (error: unknown) {
      const details =
        error instanceof Error ? error.message : "unknown scenario failure";
      setResults((prev) => ({
        ...prev,
        [scenario.id]: { status: "error", details },
      }));
    }
  }

  const runnableScenarioCount = scenarios.filter(
    (s) => !s.thirdParty || thirdPartyDemoAllowed,
  ).length;

  async function runAllScenarios(): Promise<void> {
    for (const scenario of scenarios) {
      if (scenario.thirdParty && !thirdPartyDemoAllowed) {
        continue;
      }
      await runScenario(scenario);
      await new Promise((resolve) => window.setTimeout(resolve, 200));
    }
  }

  const statusColor: Record<ScenarioStatus, string> = {
    idle: "#64748b",
    running: "#0369a1",
    ok: "#15803d",
    error: "#b91c1c",
  };

  return (
    <div style={{ maxWidth: 980, margin: "0 auto" }}>
      <h1 style={{ fontSize: 28, marginBottom: 8 }}>
        Network Instrumentation Lab
      </h1>
      <p style={{ color: "#475569", marginTop: 0, marginBottom: 20 }}>
        Trigger {runnableScenarioCount} runnable API-call pattern
        {runnableScenarioCount === 1 ? "" : "s"} manually (third-party demos only
        in dev). Open Pulse Debug Panel (Shift+P) to inspect captured spans and
        verify <code>pulse.type</code>, status, and attrs.
      </p>
      <div
        style={{
          background: "#eff6ff",
          border: "1px solid #bfdbfe",
          borderRadius: 10,
          padding: 14,
          marginBottom: 20,
          color: "#1e3a8a",
          fontSize: 14,
          lineHeight: 1.5,
        }}
      >
        Tip: remote endpoints may behave differently by browser/CORS policy.
        Even then, these flows are useful because network instrumentation should
        still emit error paths like <code>network.0</code>.
      </div>
      <div style={{ marginBottom: 16, display: "flex", gap: 12 }}>
        <button
          type="button"
          data-testid="network-lab-run-all"
          onClick={() => {
            void runAllScenarios();
          }}
          style={{
            background: "#4f46e5",
            color: "#fff",
            border: "none",
            borderRadius: 8,
            padding: "10px 16px",
            fontWeight: 700,
            cursor: "pointer",
          }}
        >
          Run All ({runnableScenarioCount})
        </button>
      </div>
      <div style={{ display: "grid", gap: 10 }}>
        {scenarios.map((scenario) => {
          const result = results[scenario.id] ?? { status: "idle" as const };
          const gated = Boolean(scenario.thirdParty && !thirdPartyDemoAllowed);
          return (
            <div
              key={scenario.id}
              style={{
                background: "#fff",
                border: "1px solid #e2e8f0",
                borderRadius: 10,
                padding: 14,
                display: "grid",
                gridTemplateColumns: "minmax(220px, 1fr) auto",
                gap: 12,
                alignItems: "center",
              }}
            >
              <div>
                <div style={{ fontWeight: 700, marginBottom: 4 }}>
                  {scenario.label}
                </div>
                <div style={{ fontSize: 13, color: "#475569" }}>
                  kind={scenario.kind} | expected {scenario.expected}
                </div>
                <div
                  style={{
                    marginTop: 4,
                    fontSize: 12,
                    color: statusColor[result.status],
                    fontWeight: 600,
                  }}
                >
                  {result.status}
                  {result.details ? ` — ${result.details}` : ""}
                </div>
              </div>
              {gated ? (
                <div
                  style={{
                    fontSize: 13,
                    color: "#64748b",
                    textAlign: "right",
                    maxWidth: 220,
                    justifySelf: "end",
                  }}
                >
                  Production build — third-party requests disabled for artifact
                  safety.
                </div>
              ) : (
                <button
                  type="button"
                  data-testid={`network-lab-${scenario.id}`}
                  onClick={() => {
                    void runScenario(scenario);
                  }}
                  disabled={result.status === "running"}
                  style={{
                    background:
                      result.status === "running" ? "#94a3b8" : "#0f172a",
                    color: "#fff",
                    border: "none",
                    borderRadius: 8,
                    padding: "9px 14px",
                    fontWeight: 700,
                    cursor:
                      result.status === "running" ? "not-allowed" : "pointer",
                  }}
                >
                  {result.status === "running" ? "Running..." : "Run"}
                </button>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
