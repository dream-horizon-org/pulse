import type { JourneyResponse } from "../../../hooks/useGetFunnelData";

export function buildJourneySankeyOption(data: JourneyResponse) {
  const maxValue = Math.max(...data.links.map((l) => l.value));

  return {
    tooltip: {
      trigger: "item" as const,
      triggerOn: "mousemove" as const,
      formatter: (params: {
        dataType?: string;
        data?: { source?: string; target?: string; value?: number };
        name?: string;
        value?: number;
      }) => {
        if (params.dataType === "edge") {
          return `<strong>${params.data?.source}</strong> → <strong>${params.data?.target}</strong><br/>Users: <strong>${params.data?.value?.toLocaleString() ?? ""}</strong>`;
        }
        return `<strong>${params.name}</strong><br/>Users: <strong>${params.value?.toLocaleString() ?? "—"}</strong>`;
      },
    },
    series: [
      {
        type: "sankey" as const,
        emphasis: { focus: "adjacency" as const },
        nodeAlign: "justify" as const,
        layoutIterations: 32,
        draggable: true,
        left: 20,
        right: 160,
        top: 20,
        bottom: 20,
        nodeWidth: 20,
        nodeGap: 14,
        lineStyle: {
          color: "gradient" as const,
          curveness: 0.5,
          opacity: 0.3,
        },
        itemStyle: { borderWidth: 1, borderColor: "#fff" },
        label: {
          position: "right" as const,
          fontSize: 12,
          fontWeight: 500,
          color: "#334155",
          formatter: (params: { name?: string; value?: number }) => {
            const pct =
              maxValue > 0
                ? (((params.value ?? 0) / maxValue) * 100).toFixed(1)
                : "0";
            return `${params.name}\n${pct}% · ${params.value?.toLocaleString() ?? ""}`;
          },
        },
        data: data.nodes.map((node) => ({
          name: node.name,
          itemStyle: {
            color: node.name === "Exit" ? "#ef4444" : "#0ba09a",
            borderColor: node.name === "Exit" ? "#dc2626" : "#077672",
          },
        })),
        links: data.links,
      },
    ],
  };
}
