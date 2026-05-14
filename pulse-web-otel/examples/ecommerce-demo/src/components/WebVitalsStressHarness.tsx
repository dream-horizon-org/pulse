import React, { useEffect, useLayoutEffect, useMemo, useState } from "react";
import { useLocation } from "react-router-dom";
import {
  parseWebVitalsStressSearchParams,
  type WebVitalsStressParams,
} from "../webVitalsStressConfig";

/** Mulberry32 — deterministic PRNG for stress timing + arm roll. */
function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a += 0x6d2b79f5;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t ^= t + Math.imul(t ^ (t >>> 7), 61 | t);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function hashCombined(parts: string): number {
  let h = 2166136261;
  for (let i = 0; i < parts.length; i += 1) {
    h ^= parts.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

function stressRng(
  config: WebVitalsStressParams,
  navKey: string,
): () => number {
  const base =
    (typeof config.seed === "number" ? config.seed * 2654435761 : 0) ^
    hashCombined(navKey);
  return mulberry32(base === 0 ? 0x9e3779b9 : base);
}

export type WebVitalsStressPlan = {
  armed: boolean;
  wantCls: boolean;
  wantPaint: boolean;
  wantInp: boolean;
  paintMs: number;
  clsMs: number;
};

export function computeWebVitalsStressPlan(
  pathname: string,
  search: string,
  locationKey: string,
): WebVitalsStressPlan {
  const config = parseWebVitalsStressSearchParams(new URLSearchParams(search));
  const rng = stressRng(config, `${locationKey}:${pathname}`);
  const armed = config.mode !== "off" && rng() < config.probability;

  const wantCls = armed && (config.mode === "cls" || config.mode === "all");
  const wantPaint =
    armed &&
    (config.mode === "lcp" || config.mode === "fcp" || config.mode === "all");
  const wantInp = armed && (config.mode === "inp" || config.mode === "all");
  const mild = config.severity === "mild";

  let paintMs = 0;
  let clsMs = 0;
  if (wantPaint) {
    paintMs = mild ? 2000 + rng() * 800 : 3500 + rng() * 1500;
  }
  if (wantCls) {
    clsMs = mild ? 500 + rng() * 300 : 800 + rng() * 400;
  }

  return { armed, wantCls, wantPaint, wantInp, paintMs, clsMs };
}

type Props = {
  children: React.ReactNode;
};

/**
 * Optional cross-route stress: timer CLS strip, delayed route paint (LCP/FCP),
 * capture-phase INP spin on first click per navigation when armed.
 */
export function WebVitalsStressHarness({
  children,
}: Props): React.ReactElement {
  const location = useLocation();
  const [paintBlocked, setPaintBlocked] = useState(false);
  const [clsVisible, setClsVisible] = useState(false);

  const plan = useMemo(
    () =>
      computeWebVitalsStressPlan(
        location.pathname,
        location.search,
        location.key,
      ),
    [location.key, location.pathname, location.search],
  );

  useLayoutEffect(() => {
    const { wantCls, wantPaint } = plan;
    setPaintBlocked(wantPaint);
    setClsVisible(wantCls && !wantPaint);
  }, [plan]);

  useEffect(() => {
    const { wantCls, wantPaint, paintMs, clsMs } = plan;

    const tids: number[] = [];

    if (wantPaint && wantCls) {
      tids.push(
        window.setTimeout(() => {
          setPaintBlocked(false);
          setClsVisible(true);
          tids.push(
            window.setTimeout(() => {
              setClsVisible(false);
            }, clsMs),
          );
        }, paintMs),
      );
    } else if (wantPaint) {
      tids.push(
        window.setTimeout(() => {
          setPaintBlocked(false);
        }, paintMs),
      );
    } else if (wantCls) {
      tids.push(
        window.setTimeout(() => {
          setClsVisible(false);
        }, clsMs),
      );
    }

    return () => {
      for (const id of tids) {
        window.clearTimeout(id);
      }
    };
  }, [plan]);

  useEffect(() => {
    if (!plan.wantInp) return undefined;

    let spun = false;
    const onClick = (): void => {
      if (spun) return;
      spun = true;
      const end = Date.now() + 70;
      while (Date.now() < end) {
        /* INP stress — same ~70ms spin as manual triggers / web-vitals E2E */
      }
    };
    document.addEventListener("click", onClick, { capture: true });
    return () => {
      document.removeEventListener("click", onClick, { capture: true });
    };
  }, [plan]);

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "stretch",
        minHeight: 0,
        flex: 1,
      }}
    >
      {clsVisible ? (
        <div
          data-testid="wv-stress-cls-bar"
          style={{
            height: 48,
            flexShrink: 0,
            background: "linear-gradient(90deg,#c7d2fe,#a5b4fc)",
            borderBottom: "1px solid #6366f1",
            fontSize: 12,
            fontWeight: 700,
            color: "#312e81",
            display: "flex",
            alignItems: "center",
            paddingLeft: 16,
          }}
        >
          Web Vitals stress: CLS strip (timer removal)
        </div>
      ) : null}
      {paintBlocked ? (
        <div
          data-testid="wv-stress-paint-gate"
          style={{
            padding: 48,
            textAlign: "center",
            color: "#94a3b8",
            fontSize: 15,
          }}
        >
          Web Vitals stress: delaying route paint (LCP/FCP)…
        </div>
      ) : (
        children
      )}
    </div>
  );
}
