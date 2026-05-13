import React, { useCallback } from "react";

/**
 * Dev / manual QA helpers to surface CLS and INP reliably (same patterns as Playwright E2E).
 * TTFB is tied to the HTML navigation response — reload or DevTools throttling, not a DOM toggle.
 */
export function WebVitalsManualTriggers(): React.ReactElement {
  /**
   * CLS ignores layout shifts that count as “recent input” (~500ms window). A shift caused
   * directly by a click handler is excluded, so CLS stays 0. Delay the DOM change so the
   * shift is not attributed to the gesture (matches `e2e/web-vitals.spec.ts`).
   */
  const scheduleClsProbe = useCallback(() => {
    window.setTimeout(() => {
      const box = document.createElement("div");
      box.setAttribute("data-manual-cls-shift", "1");
      box.style.cssText =
        "width:80px;height:80px;background:#fecaca;border:1px solid #ef4444;border-radius:6px;position:fixed;top:120px;left:24px;z-index:99998;";
      document.body.appendChild(box);
      requestAnimationFrame(() => {
        box.style.height = "200px";
      });
    }, 600);
  }, []);

  const slowInpHandler = useCallback(() => {
    const end = Date.now() + 70;
    while (Date.now() < end) {
      /* spin main thread — PerformanceEventTiming duration > 40ms for INP */
    }
  }, []);

  return (
    <section
      style={{
        marginTop: 56,
        padding: 24,
        maxWidth: 560,
        marginLeft: "auto",
        marginRight: "auto",
        textAlign: "left",
        background: "#f8fafc",
        border: "1px solid #e2e8f0",
        borderRadius: 12,
      }}
    >
      <h2
        style={{
          fontSize: 16,
          fontWeight: 700,
          color: "#334155",
          marginBottom: 8,
        }}
      >
        Manual Web Vitals QA
      </h2>
      <p style={{ fontSize: 13, color: "#64748b", marginBottom: 16 }}>
        Buttons below help emit measurable <strong>CLS</strong> and{" "}
        <strong>INP</strong> during local debugging (see the demo{" "}
        <code style={{ fontSize: 12 }}>README.md</code> — section{" "}
        <strong>Web Vitals stress</strong>). After interactions, wait for the
        batch interval or switch tabs to flush logs.
      </p>

      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        <div>
          <div
            style={{
              fontSize: 13,
              fontWeight: 600,
              color: "#475569",
              marginBottom: 6,
            }}
          >
            CLS — layout shift
          </div>
          <button
            type="button"
            onClick={scheduleClsProbe}
            style={{
              padding: "8px 14px",
              borderRadius: 8,
              border: "1px solid #cbd5e1",
              background: "#fff",
              fontWeight: 600,
              fontSize: 13,
              cursor: "pointer",
            }}
          >
            Schedule CLS probe (~600ms delay)
          </button>
          <p style={{ fontSize: 12, color: "#94a3b8", marginTop: 6 }}>
            A box appears and grows after a short delay so the shift is not
            excluded as user-input–adjacent. Then hide the tab or wait for batch
            export — web-vitals reports CLS when the page becomes hidden
            (default).
          </p>
        </div>

        <div>
          <div
            style={{
              fontSize: 13,
              fontWeight: 600,
              color: "#475569",
              marginBottom: 6,
            }}
          >
            INP — slow input
          </div>
          <button
            type="button"
            onClick={slowInpHandler}
            style={{
              padding: "8px 14px",
              borderRadius: 8,
              border: "1px solid #cbd5e1",
              background: "#fff",
              fontWeight: 600,
              fontSize: 13,
              cursor: "pointer",
            }}
          >
            Slow click handler (~70ms) — INP candidate
          </button>
          <p style={{ fontSize: 12, color: "#94a3b8", marginTop: 6 }}>
            Keeps the main thread busy so{" "}
            <code style={{ fontSize: 11 }}>PerformanceEventTiming</code> records
            ≥40ms (same idea as E2E). Then tab-away or use INP finalization per
            web-vitals.
          </p>
        </div>

        <div>
          <div
            style={{
              fontSize: 13,
              fontWeight: 600,
              color: "#475569",
              marginBottom: 6,
            }}
          >
            TTFB — navigation timing
          </div>
          <p style={{ fontSize: 12, color: "#64748b", marginBottom: 8 }}>
            TTFB measures time to first byte of <strong>this document</strong>.
            It is not something you “click” after load—use{" "}
            <strong>Network throttling</strong> / slow 3G in DevTools, or reload
            to sample again.
          </p>
          <button
            type="button"
            onClick={() => {
              window.location.reload();
            }}
            style={{
              padding: "8px 14px",
              borderRadius: 8,
              border: "1px solid #cbd5e1",
              background: "#fff",
              fontWeight: 600,
              fontSize: 13,
              cursor: "pointer",
            }}
          >
            Hard reload (new navigation → new TTFB sample)
          </button>
        </div>
      </div>
    </section>
  );
}
