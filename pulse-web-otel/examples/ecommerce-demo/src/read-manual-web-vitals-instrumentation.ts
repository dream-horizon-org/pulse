/**
 * Manual Web Vitals QA: optional local disable via URL (`pulse_wv_enabled`) or
 * `VITE_PULSE_WEB_VITALS_ENABLED=false`. Merged into `PulseProvider` config from
 * `Root.tsx` (`useDemoUrlPulseOptions`).
 *
 * Remote `web_vitals` feature gate still comes from active-config (mock JSON / server).
 */
export type ManualWebVitalsInstrumentation = {
  webVitals: {
    enabled?: boolean;
  };
};

export function readManualWebVitalsInstrumentation(
  searchParams: URLSearchParams,
): ManualWebVitalsInstrumentation | undefined {
  const q = (key: string): string | null => searchParams.get(key);
  const truthy = (v: string | null): boolean =>
    v === "1" || v === "true" || v === "yes";
  const falsy = (v: string | null): boolean => v === "0" || v === "false";

  let enabled: boolean | undefined;
  let touchedEnabled = false;
  if (falsy(q("pulse_wv_enabled"))) {
    enabled = false;
    touchedEnabled = true;
  } else if (import.meta.env["VITE_PULSE_WEB_VITALS_ENABLED"] === "false") {
    enabled = false;
    touchedEnabled = true;
  } else if (truthy(q("pulse_wv_enabled"))) {
    enabled = true;
    touchedEnabled = true;
  }

  if (!touchedEnabled) {
    return undefined;
  }

  return { webVitals: { enabled } };
}
