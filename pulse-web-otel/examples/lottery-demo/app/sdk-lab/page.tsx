"use client";

import { useState, useRef } from "react";
import { Pulse } from "@dreamhorizonorg/pulse-web";
import { api } from "../lib/api";

// ─── helpers ───────────────────────────────────────────────────────────────

function Section({
  title,
  emoji,
  children,
}: {
  title: string;
  emoji: string;
  children: React.ReactNode;
}) {
  return (
    <div className="bg-white rounded-2xl shadow-card overflow-hidden">
      <div className="px-4 py-3 bg-sapphire flex items-center gap-2">
        <span>{emoji}</span>
        <span className="text-white font-bold text-sm">{title}</span>
      </div>
      <div className="p-4 space-y-2">{children}</div>
    </div>
  );
}

function LabButton({
  label,
  description,
  color = "sapphire",
  onClick,
  testId,
}: {
  label: string;
  description: string;
  color?: "sapphire" | "red" | "orange" | "purple" | "blue" | "emerald";
  onClick: () => void;
  testId?: string;
}) {
  const colorMap = {
    sapphire: "bg-sapphire hover:bg-sapphire-light",
    red: "bg-red-500 hover:bg-red-600",
    orange: "bg-orange-500 hover:bg-orange-600",
    purple: "bg-purple-600 hover:bg-purple-700",
    blue: "bg-blue-500 hover:bg-blue-600",
    emerald: "bg-emerald-600 hover:bg-emerald-700",
  };
  return (
    <div className="flex items-center justify-between gap-3">
      <div className="flex-1 min-w-0">
        <div className="text-sm font-semibold text-gray-800">{label}</div>
        <div className="text-xs text-gray-400 truncate">{description}</div>
      </div>
      <button
        data-testid={testId}
        onClick={onClick}
        className={`shrink-0 px-3 py-1.5 ${colorMap[color]} text-white text-xs font-semibold rounded-lg transition-colors active:scale-95`}
      >
        Run
      </button>
    </div>
  );
}

// ─── Render bomb (for React render error) ──────────────────────────────────
function RenderBomb(): React.ReactNode {
  throw new Error("Intentional render error from SDK Lab");
}

// ─── Main page ──────────────────────────────────────────────────────────────
export default function SdkLabPage() {
  const [throwRender, setThrowRender] = useState(false);
  const [log, setLog] = useState<string[]>([]);
  const abortRef = useRef<AbortController | null>(null);

  function addLog(msg: string) {
    setLog((prev) =>
      [`[${new Date().toLocaleTimeString()}] ${msg}`, ...prev].slice(0, 30),
    );
  }

  // ── Errors ────────────────────────────────────────────────────────────────

  function throwUncaught() {
    Pulse.trackEvent("lab_throw_uncaught");
    setTimeout(() => {
      throw new Error("SDK Lab: uncaught error");
    }, 0);
    addLog("Threw uncaught error → device.crash via window.onerror");
  }

  function throwUnhandledRejection() {
    Pulse.trackEvent("lab_throw_rejection");
    // eslint-disable-next-line @typescript-eslint/prefer-promise-reject-errors
    Promise.reject(new Error("SDK Lab: unhandled promise rejection"));
    addLog("Promise.reject() → non_fatal via unhandledrejection");
  }

  function manualReportException() {
    Pulse.reportException(new Error("SDK Lab: manual reportException"), {
      context: "sdk_lab",
      severity: "warning",
    });
    addLog("Pulse.reportException() → non_fatal");
  }

  function manualReportCrash() {
    Pulse.reportDeviceCrash(new Error("SDK Lab: manual reportDeviceCrash"), {
      context: "sdk_lab",
    });
    addLog("Pulse.reportDeviceCrash() → device.crash");
  }

  function manualTrackNonFatal() {
    Pulse.trackNonFatal("lab_manual_non_fatal", {
      context: "sdk_lab",
      trigger: "button_click",
    });
    addLog("Pulse.trackNonFatal() → non_fatal");
  }

  function jsonParseError() {
    try {
      JSON.parse("not-valid-json {{{{");
    } catch (e) {
      Pulse.reportException(e as Error, { context: "json_parse_error" });
      addLog("JSON.parse error caught + reported → non_fatal");
    }
  }

  function localStorageQuota() {
    try {
      const big = "x".repeat(1024 * 1024); // 1 MB chunk
      let i = 0;
      while (i < 100) {
        localStorage.setItem(`lab_quota_${i}`, big);
        i++;
      }
    } catch (e) {
      Pulse.reportException(e as Error, { context: "localstorage_quota" });
      addLog("localStorage quota exceeded + reported → non_fatal");
    } finally {
      // Clean up quota fill
      for (let i = 0; i < 100; i++) localStorage.removeItem(`lab_quota_${i}`);
    }
  }

  // ── Network ───────────────────────────────────────────────────────────────

  async function chaosRequest(status: number) {
    try {
      await api.get(`/api/chaos?status=${status}`);
    } catch {
      // ApiError already reported inside api.get
    }
    addLog(`GET /api/chaos?status=${status} → http span status ${status}`);
  }

  async function slowRequest() {
    addLog("GET /api/slow (4s delay) started…");
    try {
      await api.get("/api/slow");
      addLog("GET /api/slow completed → slow http span");
    } catch {
      addLog("GET /api/slow failed");
    }
  }

  async function abortRequest() {
    abortRef.current = new AbortController();
    addLog("Starting fetch with AbortController…");
    try {
      await api.get("/api/slow", abortRef.current.signal);
    } catch {
      addLog("Fetch aborted → http span with abort");
    }
  }

  function abortNow() {
    abortRef.current?.abort();
    addLog("AbortController.abort() called");
  }

  async function timeoutRequest() {
    const ctrl = new AbortController();
    setTimeout(() => ctrl.abort(), 1000);
    addLog("Fetch with 1s timeout started…");
    try {
      await api.get("/api/slow", ctrl.signal);
    } catch {
      addLog("Request timed out after 1s → http span aborted");
    }
  }

  // ── Web Vitals ────────────────────────────────────────────────────────────

  function blockMainThread() {
    addLog("Blocking main thread 600ms → poor INP…");
    const start = Date.now();
    // Synchronous CPU burn — simulates heavy JS blocking INP measurement
    while (Date.now() - start < 600) {
      /* busy wait */
    }
    // Force a DOM update after the block so INP is measured
    document.title = document.title; // trigger reflow hint
    addLog("Main thread unblocked — INP spike emitted to web-vitals");
  }

  function injectCLS() {
    addLog("Injecting banner above content → CLS spike…");
    const el = document.createElement("div");
    el.style.cssText =
      "height:120px;background:linear-gradient(135deg,#f5a623,#e8932a);display:flex;align-items:center;justify-content:center;color:#1b2e4b;font-weight:800;font-size:18px;border-radius:12px;margin-bottom:8px";
    el.textContent = "🎉 FLASH SALE — 50% off all tickets today only!";
    el.id = "lab-cls-banner";
    const main = document.querySelector("main");
    if (main) {
      main.insertBefore(el, main.firstChild);
      setTimeout(
        () => document.getElementById("lab-cls-banner")?.remove(),
        4000,
      );
    }
    addLog("Banner injected — CLS recorded. Removes in 4s.");
  }

  async function triggerSlowLCP() {
    addLog("Fetching slow hero image → poor LCP / TTFB…");
    try {
      const res = await api.get<{ imageUrl: string }>("/api/slow");
      const img = document.createElement("img");
      img.src = res.imageUrl;
      img.style.cssText = "width:100%;border-radius:12px;margin-top:8px";
      img.id = "lab-lcp-img";
      const main = document.querySelector("main");
      main?.appendChild(img);
      setTimeout(() => document.getElementById("lab-lcp-img")?.remove(), 6000);
      addLog("Slow hero image loaded → LCP fired. Removes in 6s.");
    } catch {
      addLog("Slow LCP fetch failed");
    }
  }

  // ── Session ───────────────────────────────────────────────────────────────

  function rotateSession() {
    // Advance stored session start time back 31 minutes to trigger rotation
    const key = "pulse_session";
    try {
      const raw = localStorage.getItem(key);
      if (raw) {
        const session = JSON.parse(raw) as Record<string, unknown>;
        session.startedAt = Date.now() - 31 * 60 * 1000;
        localStorage.setItem(key, JSON.stringify(session));
      }
    } catch {
      // ignore
    }
    Pulse.trackEvent("session_rotate_manual");
    addLog("Session clock advanced 31min → will rotate on next signal");
  }

  function simulateNewInstall() {
    localStorage.removeItem("pulse_installation_id");
    addLog(
      "Installation ID cleared → reload to trigger installation.start + new session",
    );
    setTimeout(() => window.location.reload(), 1000);
  }

  async function forceFlush() {
    addLog("Force-flushing all providers…");
    await Pulse.shutdown();
    addLog("Shutdown complete. Reload to re-init.");
  }

  // ── Custom Events ─────────────────────────────────────────────────────────

  function fireCustomEvent() {
    Pulse.trackEvent("lab_custom_event", {
      source: "sdk_lab",
      timestamp: Date.now(),
      random: Math.random(),
    });
    addLog("Pulse.trackEvent('lab_custom_event') fired");
  }

  function batchStress() {
    for (let i = 0; i < 600; i++) {
      Pulse.trackEvent("lab_batch_stress", { seq: i });
    }
    addLog("600× trackEvent fired → batch overflow test");
  }

  // ─────────────────────────────────────────────────────────────────────────

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-extrabold text-sapphire">SDK Lab</h1>
        <p className="text-xs text-gray-400 mt-0.5">
          Explicit triggers for every Pulse Web SDK signal type.
        </p>
      </div>

      {/* Errors */}
      <Section title="Errors" emoji="🔴">
        <LabButton
          label="Throw uncaught error"
          description="window.onerror → device.crash"
          color="red"
          testId="lab-throw-uncaught"
          onClick={throwUncaught}
        />
        <LabButton
          label="Unhandled promise rejection"
          description="unhandledrejection → non_fatal"
          color="orange"
          testId="lab-throw-rejection"
          onClick={throwUnhandledRejection}
        />
        <LabButton
          label="React render error"
          description="PulseErrorBoundary → device.crash"
          color="purple"
          testId="lab-throw-render"
          onClick={() => {
            Pulse.trackEvent("lab_throw_render");
            setThrowRender(true);
          }}
        />
        {throwRender && <RenderBomb />}
        <LabButton
          label="reportDeviceCrash()"
          description="Manual fatal → device.crash"
          color="red"
          onClick={manualReportCrash}
        />
        <LabButton
          label="reportException()"
          description="Manual non-fatal → non_fatal"
          color="blue"
          onClick={manualReportException}
        />
        <LabButton
          label="trackNonFatal()"
          description="Named non-fatal → non_fatal"
          color="blue"
          onClick={manualTrackNonFatal}
        />
        <LabButton
          label="JSON.parse error"
          description="Caught + reported → non_fatal"
          color="orange"
          onClick={jsonParseError}
        />
        <LabButton
          label="localStorage quota exceeded"
          description="Storage error → non_fatal"
          color="orange"
          onClick={localStorageQuota}
        />
      </Section>

      {/* Network */}
      <Section title="Network" emoji="🌐">
        {([404, 429, 500, 503] as const).map((status) => (
          <LabButton
            key={status}
            label={`HTTP ${status}`}
            description={`GET /api/chaos?status=${status} → http span`}
            color={status >= 500 ? "red" : "orange"}
            onClick={() => chaosRequest(status)}
          />
        ))}
        <LabButton
          label="Slow request (4 s)"
          description="GET /api/slow → slow http span, poor TTFB"
          color="blue"
          onClick={slowRequest}
        />
        <LabButton
          label="Abortable fetch"
          description="Start fetch, then abort it below"
          color="sapphire"
          onClick={abortRequest}
        />
        <LabButton
          label="Abort now"
          description="AbortController.abort() → aborted span"
          color="orange"
          onClick={abortNow}
        />
        <LabButton
          label="Timeout after 1 s"
          description="Auto-abort → timed-out http span"
          color="orange"
          onClick={timeoutRequest}
        />
      </Section>

      {/* Web Vitals */}
      <Section title="Web Vitals" emoji="📊">
        <LabButton
          label="Block main thread (600 ms)"
          description="Sync CPU burn → poor INP > 500 ms"
          color="red"
          onClick={blockMainThread}
        />
        <LabButton
          label="Inject layout shift"
          description="Insert tall banner above content → CLS > 0.25"
          color="orange"
          onClick={injectCLS}
        />
        <LabButton
          label="Load slow hero image"
          description="Fetch 4s image → poor LCP + TTFB"
          color="orange"
          onClick={triggerSlowLCP}
        />
      </Section>

      {/* Session */}
      <Section title="Session" emoji="🔄">
        <LabButton
          label="Rotate session"
          description="Advance clock 31 min → session.end + session.start"
          color="sapphire"
          onClick={rotateSession}
        />
        <LabButton
          label="Simulate new install"
          description="Clear installation ID → reload → installation.start"
          color="purple"
          onClick={simulateNewInstall}
        />
        <LabButton
          label="Force flush (shutdown)"
          description="Pulse.shutdown() → drain all buffered signals"
          color="blue"
          onClick={forceFlush}
        />
      </Section>

      {/* Custom Events */}
      <Section title="Custom Events" emoji="⚡">
        <LabButton
          label="Fire custom event"
          description="trackEvent('lab_custom_event') with attrs"
          color="emerald"
          onClick={fireCustomEvent}
        />
        <LabButton
          label="Batch stress (600 events)"
          description="600× trackEvent → tests batch overflow"
          color="sapphire"
          onClick={batchStress}
        />
      </Section>

      {/* Log */}
      {log.length > 0 && (
        <div className="bg-gray-900 rounded-2xl p-4">
          <div className="text-xs text-gray-400 uppercase tracking-wider mb-2">
            Lab log
          </div>
          <div className="space-y-1 max-h-48 overflow-y-auto">
            {log.map((line, i) => (
              <div key={i} className="text-xs font-mono text-emerald-400">
                {line}
              </div>
            ))}
          </div>
          <button
            onClick={() => setLog([])}
            className="mt-2 text-xs text-gray-500 hover:text-gray-300 transition-colors"
          >
            Clear log
          </button>
        </div>
      )}
    </div>
  );
}
