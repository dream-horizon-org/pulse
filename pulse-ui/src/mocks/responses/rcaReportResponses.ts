/**
 * Mock POST /v1/ai/rca/report — format: exec summary → 3 segment metric tables → recommendations.
 * UI order: Insights (exec summary) → Markdown (tables + recs) → charts.
 */

const BL = {
  vol: "3,287",
  apdex: "0.60",
  err: "9.31%",
  poor: "5.80%",
  p50: "2,111.25 ms",
  p95: "4,179.76 ms",
  crash: "1.22%",
  anr: "0.49%",
  frozen: "2.38%",
} as const;

export const buildMockRcaReportResponseBody = (
  interactionName: string,
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

  const executiveSummaryMarkdown = `The **'${name}'** interaction is experiencing **degraded performance**: **P95 duration** exceeds the poor threshold and **error** and **crash** rates are elevated versus tenant baseline. The primary contributors are **Android App Version 4.0.0** especially **OS 13** and **iOS App Version 4.2.0**.`;

  const markdown = `## Top contributing segments

### 1. Android + AppVersion 4.0.0

| Metric | Value | Baseline | Delta |
| :----- | :---- | :------- | :---- |
| Volume | 119 | ${BL.vol} | 3.6% of total |
| APDEX | 0.66 | ${BL.apdex} | +10% |
| Error rate | **15.13%** | ${BL.err} | +62% |
| Poor user % | 2.97% | ${BL.poor} | -49% |
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
| Error rate | **40.0%** | ${BL.err} | +330% |
| Poor user % | 6.67% | ${BL.poor} | +15% |
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
| Error rate | **11.89%** | ${BL.err} | +28% |
| Poor user % | 8.01% | ${BL.poor} | +38% |
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
`;

  return {
    report: {
      markdown,
      charts: [
        {
          type: "chart",
          title: "Error rate (%) — top 3 segments vs tenant baseline",
          data: {
            type: "bar",
            labels: ["Tenant", "Andr 4.0.0", "4.0.0+OS13", "iOS 4.2.0"],
            datasets: [{ label: "Error %", data: [9.31, 15.13, 40.0, 11.89] }],
          },
        },
      ],
      tables: [],
    },
    rca_insights: executiveSummaryMarkdown,
    cached: true,
  };
};
