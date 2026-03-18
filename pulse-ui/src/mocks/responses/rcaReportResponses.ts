/**
 * Mock response body for POST /v1/ai/rca/report (local mock server).
 */

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
      columns: Array<{ key: string; label: string }>;
      rows: Array<Record<string, string>>;
    }>;
  };
  rca_insights: string;
  cached: boolean;
} => ({
  report: {
    markdown: `## What changed\n\n**${interactionName}** is carrying most of the regression this period. Android **13+** is at **~2.4%** errors vs **~0.5%** tenant baseline; iOS and Web are essentially flat.\n\n## Likely driver\nUplift clusters on **Jio + app 6.2.1**, not on Wi‑Fi or older app builds. ANR rate did not move—so treat this as **downstream / network-timeout** behaviour on cold feed load, not a UI jank issue.\n\n## Suggested check\nSample **20 failing sessions** with carrier **Jio**, compare **6.2.1 vs 6.2.0** trace waterfall for the feed request before cutting a hotfix.`,
    charts: [
      {
        type: "chart",
        title: "Error rate by platform (selected window)",
        data: {
          type: "bar",
          labels: ["Android 13+", "Android 12", "iOS", "Web"],
          datasets: [{ label: "Error %", data: [2.4, 1.1, 0.35, 0.12] }],
        },
      },
    ],
    tables: [
      {
        type: "table",
        title: "Worst slices (volume-weighted)",
        columns: [
          { key: "slice", label: "Slice" },
          { key: "err", label: "Err %" },
          { key: "vol", label: "Sessions" },
        ],
        rows: [
          { slice: "Android 13+ · Jio · 6.2.1", err: "3.1", vol: "4.2k" },
          {
            slice: "Android 13+ · other carriers",
            err: "1.8",
            vol: "2.1k",
          },
          { slice: "Android 12 · all", err: "1.1", vol: "8.9k" },
        ],
      },
    ],
  },
  rca_insights: `**${interactionName}** — Android **13+** is **~5×** the tenant error baseline (**2.4% vs 0.5%**); **iOS/Web unchanged**.

- **Where it hurts:** almost all extra errors sit on **Jio + 6.2.1**; same build on **Airtel/Wi‑Fi** looks normal → points to **carrier / edge path**, not a blanket release bug.
- **Signal:** cold-open window (**first ~90s**) accounts for **~72%** of those failures; ANR flat → prioritize **feed API latency / timeout** over render work.
- **Next step:** pull **traces** for failing **${interactionName}** sessions (filter **carrier=Jio**, **version=6.2.1**) and diff p95 **TTFB** vs **6.2.0** before the next train.`,
  cached: false,
});
