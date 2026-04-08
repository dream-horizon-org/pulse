import type { JourneyResponse } from "../../../hooks/useGetFunnelData";

/**
 * Sanitize journey graph data so it forms a DAG (required by ECharts Sankey).
 *
 * 1. Remove the synthetic ENTRY node — promote the anchor event as the root.
 * 2. Aggregate duplicate links (sum values for identical source→target pairs).
 * 3. Remove self-loops (source === target).
 * 4. BFS from the root to assign depth levels.
 * 5. Keep only forward edges (target depth > source depth) to break cycles.
 * 6. Drop orphaned nodes that no longer appear in any link.
 */
function sanitizeForSankey(data: JourneyResponse): JourneyResponse {
  const links = data.links;

  // 1. Collapse ENTRY node — find what ENTRY points to (the anchor event)
  //    and rewrite all ENTRY→X links so X becomes the root.
  let anchorTarget: string | null = null;
  const nonEntryLinks: typeof links = [];
  for (const link of links) {
    if (link.source === "ENTRY") {
      // The anchor event is whatever ENTRY points to
      anchorTarget = link.target;
    } else {
      nonEntryLinks.push(link);
    }
  }
  const workingLinks = nonEntryLinks;

  // 2. Aggregate duplicates & remove self-loops
  const linkMap = new Map<string, number>();
  for (const link of workingLinks) {
    if (link.source === link.target) continue;
    const key = `${link.source}\0${link.target}`;
    linkMap.set(key, (linkMap.get(key) || 0) + link.value);
  }

  // 3. Build adjacency list from aggregated links
  const adj = new Map<string, string[]>();
  linkMap.forEach((_, key) => {
    const [source, target] = key.split("\0");
    if (!adj.has(source)) adj.set(source, []);
    adj.get(source)!.push(target);
  });

  // 4. BFS to assign depth levels — root is the anchor (or first node)
  const root =
    anchorTarget ??
    data.nodes.find((n) => n.name !== "ENTRY")?.name ??
    data.nodes[0]?.name;
  const depth = new Map<string, number>();
  if (root) {
    const queue = [root];
    depth.set(root, 0);
    while (queue.length > 0) {
      const node = queue.shift()!;
      for (const neighbor of adj.get(node) || []) {
        if (!depth.has(neighbor)) {
          depth.set(neighbor, depth.get(node)! + 1);
          queue.push(neighbor);
        }
      }
    }
  }

  // 5. Keep only forward edges (target depth > source depth)
  const cleanLinks: JourneyResponse["links"] = [];
  linkMap.forEach((value, key) => {
    const [source, target] = key.split("\0");
    const sd = depth.get(source) ?? -1;
    const td = depth.get(target) ?? -1;
    if (td > sd) {
      cleanLinks.push({ source, target, value });
    }
  });

  // 6. Drop orphaned nodes (and always drop the synthetic ENTRY node)
  const usedNodes = new Set<string>();
  for (const link of cleanLinks) {
    usedNodes.add(link.source);
    usedNodes.add(link.target);
  }
  const cleanNodes = data.nodes.filter(
    (n) => n.name !== "ENTRY" && usedNodes.has(n.name),
  );

  return { nodes: cleanNodes, links: cleanLinks };
}

/** Truncate a string to `max` chars, appending "…" if trimmed. */
function truncate(str: string, max: number): string {
  return str.length > max ? str.slice(0, max - 1) + "…" : str;
}

export function buildJourneySankeyOption(rawData: JourneyResponse) {
  const data = sanitizeForSankey(rawData);

  // Compute root node total (sum of outgoing links from the first/anchor node).
  // This represents 100% of users entering the journey.
  const rootNode = data.nodes[0]?.name;
  let rootTotal = 0;
  for (const link of data.links) {
    if (link.source === rootNode) rootTotal += link.value;
  }
  if (rootTotal === 0) rootTotal = 1;

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
          const edgePct = ((params.data?.value ?? 0) / rootTotal * 100).toFixed(1);
          return `<strong>${params.data?.source}</strong> → <strong>${params.data?.target}</strong><br/>Users: <strong>${params.data?.value?.toLocaleString() ?? ""}</strong> (${edgePct}%)`;
        }
        const pct = (((params.value ?? 0) / rootTotal) * 100).toFixed(1);
        return `<strong>${params.name}</strong><br/>Users: <strong>${params.value?.toLocaleString() ?? "—"}</strong> (${pct}%)`;
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
        right: 200,
        top: 20,
        bottom: 20,
        nodeWidth: 18,
        nodeGap: 20,
        lineStyle: {
          color: "gradient" as const,
          curveness: 0.5,
          opacity: 0.25,
        },
        itemStyle: { borderWidth: 1, borderColor: "#fff" },
        label: {
          position: "right" as const,
          fontSize: 11,
          fontWeight: 500,
          color: "#334155",
          overflow: "truncate" as const,
          width: 180,
          formatter: (params: { name?: string; value?: number }) => {
            const name = truncate(params.name ?? "", 24);
            const count = params.value?.toLocaleString() ?? "";
            return `${name}  ${count}`;
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
