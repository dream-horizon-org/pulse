/**
 * Mock POST /v1/ai/rca/report — format: exec summary → 3 segment metric tables → recommendations.
 * UI order: Insights (exec summary) → Markdown (tables + recs) → charts.
 * When `tenantContext` is provided (from interaction overview metrics), baseline columns match that interaction.
 */

import type { RcaReportTenantContext } from "../../hooks/useGetRcaReport/useGetRcaReport.interface";
import { isEcommerceMockThemeEnabled } from "../mockEcommerceTheme";

/** Tenant baselines when overview metrics are unavailable — aligned with JoinContestButtonClick RCA demo. */
const DEFAULT_NUM = {
  volLabel: "3,287",
  apdex: 0.85,
  errorRate: 14.2,
  poorUsers: 14.41,
  p50Ms: 230,
  p95Ms: 1161,
  crash: 1.22,
  anr: 0.49,
  frozen: 2.38,
} as const;

/** Same relative lifts as the original static mock (segment vs tenant error rate). */
const ERROR_BAR_RATIOS = [1, 15.13 / 9.31, 40 / 9.31, 11.89 / 9.31] as const;

function formatPct(n: number): string {
  return `${n.toFixed(2)}%`;
}

function formatMs(ms: number): string {
  return `${ms.toLocaleString("en-US", { maximumFractionDigits: 2 })} ms`;
}

function resolveBaseline(ctx: RcaReportTenantContext | null | undefined) {
  const err = ctx?.errorRatePercent ?? DEFAULT_NUM.errorRate;
  const poor = ctx?.poorUsersPercent ?? DEFAULT_NUM.poorUsers;
  const apdex = ctx?.apdex ?? DEFAULT_NUM.apdex;
  const p50 = ctx?.p50Ms ?? DEFAULT_NUM.p50Ms;
  const p95 = ctx?.p95Ms ?? DEFAULT_NUM.p95Ms;
  return {
    vol: DEFAULT_NUM.volLabel,
    apdex: apdex.toFixed(2),
    err: formatPct(err),
    poor: formatPct(poor),
    p50: formatMs(p50),
    p95: formatMs(p95),
    crash: formatPct(DEFAULT_NUM.crash),
    anr: formatPct(DEFAULT_NUM.anr),
    frozen: formatPct(DEFAULT_NUM.frozen),
    /** Raw tenant error % for charts (avoid 0 for bar scaling). */
    tenantErrorRate: Math.max(err, 0.01),
  };
}

export const buildMockRcaReportResponseBody = (
  interactionName: string,
  tenantContext?: RcaReportTenantContext | null,
): {
  report: {
    markdown: string;
    charts: Array<{
      type: string;
      title: string;
      data: {
        type: string;
        labels: string[];
        datasets: Array<{ label: string; data: number[] }>;
      };
    }>;
    tables: Array<{
      type: string;
      title: string;
      description?: string;
      columns: Array<{ key: string; label: string }>;
      rows: Array<Record<string, string>>;
    }>;
  };
  rca_insights: string;
  cached: boolean;
} => {
  const name =
    interactionName.trim() !== ""
      ? interactionName.trim()
      : "payment_processing";

  const BL = resolveBaseline(tenantContext ?? null);

  const seg1Err = Math.min(99.99, BL.tenantErrorRate * (15.13 / 9.31));
  const seg2Err = Math.min(99.99, BL.tenantErrorRate * (40 / 9.31));
  const seg3Err = Math.min(99.99, BL.tenantErrorRate * (11.89 / 9.31));

  const seg1Poor = Math.min(99.99, parseFloat(BL.poor) * 0.51);
  const seg2Poor = Math.min(99.99, parseFloat(BL.poor) * 1.15);
  const seg3Poor = Math.min(99.99, parseFloat(BL.poor) * 1.38);

  const eco = isEcommerceMockThemeEnabled();

  const executiveSummaryMarkdown = tenantContext
    ? eco
      ? `The **'${name}'** interaction in the selected window shows **${BL.err}** interaction error rate and **${BL.poor}** of users in the **poor** experience bucket. Segment drilldowns compare slices to these baselines—use **screen heatmaps** (PLP, PDP, cart, checkout), **session replays** on this tab, and **linked funnels** to confirm where shoppers stall.`
      : `The **'${name}'** interaction in the selected window shows **${BL.err}** interaction error rate and **${BL.poor}** of users in the **poor** experience bucket. Segment drilldowns below compare localized slices against these baselines.`
    : eco
      ? `The **'${name}'** interaction shows **degraded performance** versus healthy **browse-to-buy** and **checkout** funnels. Contributors align with **Android 4.0.0** (**OS 13**) and **iOS 4.2.0**—validate with **heatmaps** on list/detail/checkout screens and **session replays** for cart and payment.`
      : `The **'${name}'** interaction is experiencing **degraded performance**: **P95 duration** exceeds the poor threshold and **error** and **crash** rates are elevated versus tenant baseline. The primary contributors are **Android App Version 4.0.0** especially **OS 13** and **iOS App Version 4.2.0**.`;

  const ecommerceRecommendationsSuffix = eco
    ? `

→ <span style="color:#0ca678">**Heatmaps:**</span> For **Android 4.0.0 / OS 13** and **iOS 4.2.0**, open **Checkout**, **Cart**, and **Product detail** heatmaps to find rage taps, retries, and dead zones near **${name}**.

→ <span style="color:#0ca678">**Session replays:**</span> From related replays, filter paths that fail **${name}** and verify inventory, promo, tax, and gateway responses in sequence.

→ <span style="color:#0ca678">**Funnels:**</span> Slice **Cart → checkout → payment** and **PLP → PDP** funnels by app version and OS—conversion cliffs should line up with the worst segments above.
`
    : "";

  const markdown = `## Top contributing segments

### 1. Android + AppVersion 4.0.0

| Metric | Value | Baseline | Delta |
| :----- | :---- | :------- | :---- |
| Volume | 119 | ${BL.vol} | 3.6% of total |
| APDEX | 0.66 | ${BL.apdex} | +10% |
| Error rate | **${seg1Err.toFixed(2)}%** | ${BL.err} | +62% |
| Poor user % | ${seg1Poor.toFixed(2)}% | ${BL.poor} | -49% |
| Duration P50 | 2,001.24 ms | ${BL.p50} | -5% |
| Duration P95 | 3,507.83 ms | ${BL.p95} | -16% |
| Crash rate | **2.52%** | ${BL.crash} | +107% |
| ANR rate | **1.68%** | ${BL.anr} | +243% |
| Frozen frame rate | — | ${BL.frozen} | — |

*Impact: This segment represents **3.6%** of total volume but accounts for a **disproportionately high** share of errors, crashes, and ANRs.*

---

### 2. Android + AppVersion 4.0.0 + OsVersion 13

| Metric | Value | Baseline | Delta |
| :----- | :---- | :------- | :---- |
| Volume | 25 | ${BL.vol} | 0.76% of total |
| APDEX | 0.73 | ${BL.apdex} | +22% |
| Error rate | **${seg2Err.toFixed(2)}%** | ${BL.err} | +330% |
| Poor user % | ${seg2Poor.toFixed(2)}% | ${BL.poor} | +15% |
| Duration P50 | 1,786.29 ms | ${BL.p50} | -15% |
| Duration P95 | **6,872.52 ms** | ${BL.p95} | +64% |
| Crash rate | **8.0%** | ${BL.crash} | +556% |
| ANR rate | **8.0%** | ${BL.anr} | +1,533% |
| Frozen frame rate | null | ${BL.frozen} | - |

*Impact: Smaller by volume, but **extremely high** error, crash, and ANR rates and a **markedly elevated P95**—severe performance risk for these users.*

---

### 3. iOS + AppVersion 4.2.0

| Metric | Value | Baseline | Delta |
| :----- | :---- | :------- | :---- |
| Volume | 412 | ${BL.vol} | 12.5% of total |
| APDEX | 0.54 | ${BL.apdex} | -10% |
| Error rate | **${seg3Err.toFixed(2)}%** | ${BL.err} | +28% |
| Poor user % | ${seg3Poor.toFixed(2)}% | ${BL.poor} | +38% |
| Duration P50 | 2,340.00 ms | ${BL.p50} | +11% |
| Duration P95 | **5,100.00 ms** | ${BL.p95} | +22% |
| Crash rate | 0.97% | ${BL.crash} | -21% |
| ANR rate | — | ${BL.anr} | — |
| Frozen frame rate | 1.95% | ${BL.frozen} | -18% |

*Impact: Large iOS slice with **rising errors and tail latency** while **crash rate stays flat**—favours **logic / network / backend** investigation over a native crash regression.*

---

## Recommendations

→ <span style="color:#0ca678">**Investigate Android App Version 4.0.0 (especially OS 13):**</span> Prioritize a deep dive into code changes and dependencies in <span style="color:#e03131">**4.0.0**</span> for Android, focusing on <span style="color:#e03131">**${name}**</span> flows. Pay particular attention to <span style="color:#e03131">**OS 13**</span>, where error, crash, and ANR rates are extreme.

→ <span style="color:#0ca678">**Analyze iOS App Version 4.2.0:**</span> Review <span style="color:#e03131">**4.2.0**</span> release diff to explain <span style="color:#e03131">**higher error rate and P95**</span>; correlate with <span style="color:#e03131">**API p95**</span> and client error codes for the same interaction.

→ <span style="color:#0ca678">**Error log analysis:**</span> For the high–error-rate segments above, query \`error_logs\` for specific messages and stack traces to pinpoint failure points.

→ <span style="color:#0ca678">**Crash / ANR event analysis:**</span> For high crash/ANR segments (notably <span style="color:#e03131">**Android 4.0.0 / OS 13**</span>), query \`rum_events\` for crash/ANR payloads, stack traces, and user context.
${ecommerceRecommendationsSuffix}
`;

  const chartErrorData = ERROR_BAR_RATIOS.map(
    (r) => Math.round(BL.tenantErrorRate * r * 100) / 100,
  );

  return {
    report: {
      markdown,
      charts: [
        {
          type: "chart",
          title: eco
            ? "Error rate (%) — top segments vs baseline (cross-check funnel + heatmap cohorts)"
            : "Error rate (%) — top 3 segments vs tenant baseline",
          data: {
            type: "bar",
            labels: ["Tenant", "Andr 4.0.0", "4.0.0+OS13", "iOS 4.2.0"],
            datasets: [{ label: "Error %", data: chartErrorData }],
          },
        },
      ],
      tables: [],
    },
    rca_insights: executiveSummaryMarkdown,
    cached: true,
  };
};
