import type { JourneyResponse } from "../../../hooks/useGetFunnelData";

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

/** Minimum pixel width per depth column (for chart width calculation). */
const COL_WIDTH_PX = 220;
/** Number of depth columns shown by default & per expansion click. */
const DEPTH_WINDOW = 5;
/** Pixels per node row for height calculation. */
const ROW_HEIGHT_PX = 45;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Strip the depth qualifier: "HomeLoaded::0" → "HomeLoaded" */
function stripDepth(name: string): string {
  const idx = name.lastIndexOf("::");
  return idx === -1 ? name : name.slice(0, idx);
}

/** Check if data uses depth-qualified node names ("Event::N" format). */
function isDepthQualified(data: JourneyResponse): boolean {
  return data.links.some(
    (l) => /::[-]?\d+$/.test(l.source) || /::[-]?\d+$/.test(l.target),
  );
}

/** Extract depth integer from a qualified name. Returns -999 for unqualified. */
function parseDepth(name: string): number {
  const m = name.match(/::(-?\d+)$/);
  return m ? parseInt(m[1], 10) : -999;
}

/** Truncate a string to `max` chars, appending "…" if trimmed. */
function truncate(str: string, max: number): string {
  return str.length > max ? str.slice(0, max - 1) + "…" : str;
}

// ---------------------------------------------------------------------------
// Sanitize — depth-qualified format
// ---------------------------------------------------------------------------

function sanitizeDepthQualified(data: JourneyResponse): JourneyResponse {
  const links = data.links.filter(
    (l) => l.source !== "ENTRY" && l.source !== l.target,
  );
  const usedNodes = new Set<string>();
  for (const link of links) {
    usedNodes.add(link.source);
    usedNodes.add(link.target);
  }
  const nodes = data.nodes.filter(
    (n) => n.name !== "ENTRY" && usedNodes.has(n.name),
  );
  return { nodes, links };
}

// ---------------------------------------------------------------------------
// Sanitize — legacy format (no depth qualifiers)
// ---------------------------------------------------------------------------

function sanitizeLegacy(data: JourneyResponse): JourneyResponse {
  let anchorTarget: string | null = null;
  const nonEntryLinks: typeof data.links = [];
  for (const link of data.links) {
    if (link.source === "ENTRY") {
      anchorTarget = link.target;
    } else {
      nonEntryLinks.push(link);
    }
  }
  const linkMap = new Map<string, number>();
  for (const link of nonEntryLinks) {
    if (link.source === link.target) continue;
    const key = `${link.source}\0${link.target}`;
    linkMap.set(key, (linkMap.get(key) || 0) + link.value);
  }
  const adj = new Map<string, string[]>();
  linkMap.forEach((_, key) => {
    const [source, target] = key.split("\0");
    if (!adj.has(source)) adj.set(source, []);
    adj.get(source)!.push(target);
  });
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
  const cleanLinks: JourneyResponse["links"] = [];
  linkMap.forEach((value, key) => {
    const [source, target] = key.split("\0");
    const sd = depth.get(source) ?? -1;
    const td = depth.get(target) ?? -1;
    if (td > sd) cleanLinks.push({ source, target, value });
  });
  const usedNodes = new Set<string>();
  for (const link of cleanLinks) {
    usedNodes.add(link.source);
    usedNodes.add(link.target);
  }
  return {
    nodes: data.nodes.filter(
      (n) => n.name !== "ENTRY" && usedNodes.has(n.name),
    ),
    links: cleanLinks,
  };
}

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface ExpansionState {
  /** Node names the user has individually expanded. */
  expandedNodes: Set<string>;
  /** When true, show every depth column. */
  globalExpanded: boolean;
}

export interface JourneyGraphResult {
  /** ECharts option object. */
  option: Record<string, unknown>;
  /** Recommended chart width (px). */
  graphWidth: number;
  /** Recommended chart height (px). */
  graphHeight: number;
  /** True when at least one visible node still has hidden children. */
  hasHiddenPaths: boolean;
}

// ---------------------------------------------------------------------------
// Main — build ECharts option (graph with dots + thin lines)
// ---------------------------------------------------------------------------

export function buildJourneySankeyOption(
  rawData: JourneyResponse,
  expansion?: ExpansionState,
): JourneyGraphResult {
  const depthMode = isDepthQualified(rawData);
  const data = depthMode
    ? sanitizeDepthQualified(rawData)
    : sanitizeLegacy(rawData);

  const displayName = depthMode ? stripDepth : (n: string) => n;

  // ── Compute depth for every node ──────────────────────────────────────
  const nodeDepth = new Map<string, number>();
  if (depthMode) {
    for (const node of data.nodes) {
      nodeDepth.set(node.name, Math.max(0, parseDepth(node.name)));
    }
  } else {
    const bfsAdj = new Map<string, string[]>();
    for (const link of data.links) {
      if (!bfsAdj.has(link.source)) bfsAdj.set(link.source, []);
      bfsAdj.get(link.source)!.push(link.target);
    }
    const root = data.nodes[0]?.name;
    if (root) {
      nodeDepth.set(root, 0);
      const queue = [root];
      while (queue.length > 0) {
        const cur = queue.shift()!;
        for (const nb of bfsAdj.get(cur) || []) {
          if (!nodeDepth.has(nb)) {
            nodeDepth.set(nb, nodeDepth.get(cur)! + 1);
            queue.push(nb);
          }
        }
      }
    }
  }

  // ── Flow per node (for sorting + labels/tooltips) ─────────────────────
  const inFlow = new Map<string, number>();
  for (const link of data.links) {
    inFlow.set(link.target, (inFlow.get(link.target) || 0) + link.value);
  }
  const rootNode = data.nodes[0]?.name;
  if (rootNode && !inFlow.has(rootNode)) {
    let rootOut = 0;
    for (const link of data.links) {
      if (link.source === rootNode) rootOut += link.value;
    }
    inFlow.set(rootNode, rootOut);
  }
  const rootTotal = inFlow.get(rootNode ?? "") || 1;

  // ── Forward adjacency (for expansion BFS) ─────────────────────────────
  const adj = new Map<string, string[]>();
  for (const link of data.links) {
    if (!adj.has(link.source)) adj.set(link.source, []);
    adj.get(link.source)!.push(link.target);
  }

  // ── Determine visible nodes ───────────────────────────────────────────
  const allDepths = Array.from(
    new Set(Array.from(nodeDepth.values())),
  ).sort((a, b) => a - b);
  const minDepth = allDepths[0] || 0;
  const baseMaxDepth = minDepth + DEPTH_WINDOW - 1;

  const visibleNodes = new Set<string>();

  if (expansion?.globalExpanded) {
    // Show everything
    for (const node of data.nodes) visibleNodes.add(node.name);
  } else {
    // Base: first DEPTH_WINDOW columns
    for (const node of data.nodes) {
      const d = nodeDepth.get(node.name) ?? 0;
      if (d <= baseMaxDepth) visibleNodes.add(node.name);
    }

    // Expand subtrees of individually expanded nodes (processed in depth
    // order so cascading expansions work correctly).
    if (expansion?.expandedNodes?.size) {
      const sortedExpanded = Array.from(expansion.expandedNodes)
        .filter((n) => nodeDepth.has(n))
        .sort((a, b) => (nodeDepth.get(a) ?? 0) - (nodeDepth.get(b) ?? 0));

      for (const expandedName of sortedExpanded) {
        if (!visibleNodes.has(expandedName)) continue; // parent collapsed
        const eDepth = nodeDepth.get(expandedName) ?? 0;
        const maxD = eDepth + DEPTH_WINDOW;
        const queue = [expandedName];
        const visited = new Set<string>([expandedName]);
        while (queue.length > 0) {
          const cur = queue.shift()!;
          const cd = nodeDepth.get(cur) ?? 0;
          if (cd >= maxD) continue;
          for (const neighbor of adj.get(cur) || []) {
            if (!visited.has(neighbor)) {
              visited.add(neighbor);
              visibleNodes.add(neighbor);
              queue.push(neighbor);
            }
          }
        }
      }
    }
  }

  // ── Filter to visible ─────────────────────────────────────────────────
  const filteredNodes = data.nodes.filter((n) => visibleNodes.has(n.name));
  const filteredLinks = data.links.filter(
    (l) => visibleNodes.has(l.source) && visibleNodes.has(l.target),
  );

  // ── Boundary detection — visible nodes with hidden children ───────────
  const hasHiddenChildren = new Set<string>();
  for (const link of data.links) {
    if (visibleNodes.has(link.source) && !visibleNodes.has(link.target)) {
      hasHiddenChildren.add(link.source);
    }
  }

  // ── Layout positions (normalised indices — ECharts auto-scales) ───────
  const visibleDepthGroups = new Map<number, string[]>();
  for (const node of filteredNodes) {
    const d = nodeDepth.get(node.name) ?? 0;
    if (!visibleDepthGroups.has(d)) visibleDepthGroups.set(d, []);
    visibleDepthGroups.get(d)!.push(node.name);
  }
  for (const [, nodes] of Array.from(visibleDepthGroups.entries())) {
    nodes.sort(
      (a: string, b: string) => (inFlow.get(b) || 0) - (inFlow.get(a) || 0),
    );
  }

  const visibleDepths = Array.from(visibleDepthGroups.keys()).sort(
    (a, b) => a - b,
  );
  const maxVisibleNodes = Math.max(
    1,
    ...Array.from(visibleDepthGroups.values()).map((g) => g.length),
  );

  const nodePositions = new Map<string, [number, number]>();
  for (const d of visibleDepths) {
    const nodes = visibleDepthGroups.get(d)!;
    const colX = d - (visibleDepths[0] || 0);
    const n = nodes.length;
    const startY = (maxVisibleNodes - n) / 2;
    for (let i = 0; i < n; i++) {
      nodePositions.set(nodes[i], [colX, startY + i]);
    }
  }

  // ── Graph dimensions ──────────────────────────────────────────────────
  const numVisibleCols = visibleDepths.length;
  const graphWidth = Math.max(900, numVisibleCols * COL_WIDTH_PX + 200);
  const graphHeight = Math.max(500, maxVisibleNodes * ROW_HEIGHT_PX + 60);

  // ── Helpers for node styling ──────────────────────────────────────────
  const isExpanded = (name: string) =>
    expansion?.expandedNodes?.has(name) ?? false;

  // ── Build ECharts option ──────────────────────────────────────────────
  const option: Record<string, unknown> = {
    tooltip: {
      trigger: "item" as const,
      formatter: (params: {
        dataType?: string;
        data?: {
          source?: string;
          target?: string;
          value?: number;
          realValue?: number;
        };
        name?: string;
        value?: number;
      }) => {
        if (params.dataType === "edge") {
          const real = params.data?.realValue ?? 0;
          const pct = ((real / rootTotal) * 100).toFixed(1);
          return `<strong>${displayName(params.data?.source ?? "")}</strong> → <strong>${displayName(params.data?.target ?? "")}</strong><br/>Users: <strong>${real.toLocaleString()}</strong> (${pct}%)`;
        }
        const nodeName = params.name ?? "";
        const flow = inFlow.get(nodeName) || 0;
        const pct = ((flow / rootTotal) * 100).toFixed(1);
        return `<strong>${displayName(nodeName)}</strong><br/>Users: <strong>${flow.toLocaleString()}</strong> (${pct}%)`;
      },
    },
    series: [
      {
        type: "graph" as const,
        layout: "none" as const,
        left: 80,
        top: 20,
        right: 200,
        bottom: 20,
        selectedMode: false,
        emphasis: { focus: "adjacency" as const },
        edgeSymbol: ["none", "arrow"],
        edgeSymbolSize: [0, 6],
        lineStyle: {
          color: "#b0bec5",
          width: 1.2,
          curveness: 0.01,
          opacity: 0.6,
        },
        data: filteredNodes.map((node) => {
          const pos = nodePositions.get(node.name) ?? [0, 0];
          const display = displayName(node.name);
          const isOther = display === "Other";
          const expandable =
            hasHiddenChildren.has(node.name) && !isExpanded(node.name);
          const expanded = isExpanded(node.name);

          const baseColor = isOther
            ? "#94a3b8"
            : display === "Exit"
              ? "#ef4444"
              : "#0ba09a";
          const color = expandable ? "#3b82f6" : baseColor;
          const flow = inFlow.get(node.name) || 0;

          // Build rich-text label
          let labelStr = `${truncate(display, 20)}  {count|${flow.toLocaleString()}}`;
          if (expandable) {
            labelStr += `  {expand|▸ more}`;
          } else if (expanded) {
            labelStr += `  {collapse|◂ less}`;
          }

          return {
            name: node.name,
            x: pos[0],
            y: pos[1],
            symbolSize: expandable || expanded ? 10 : 8,
            itemStyle: { color, borderColor: color, borderWidth: 1 },
            label: {
              show: true,
              position: "right" as const,
              fontSize: 10,
              fontWeight: 500,
              color: "#334155",
              formatter: labelStr,
              rich: {
                count: {
                  fontSize: 9,
                  color: "#94a3b8",
                  padding: [0, 0, 0, 4],
                },
                expand: {
                  fontSize: 9,
                  color: "#3b82f6",
                  fontWeight: 700,
                  padding: [0, 0, 0, 6],
                },
                collapse: {
                  fontSize: 9,
                  color: "#ef4444",
                  fontWeight: 700,
                  padding: [0, 0, 0, 6],
                },
              },
            },
            // Custom data for click handler in consuming component
            expandable,
            expanded,
          };
        }),
        links: filteredLinks.map((l) => ({
          source: l.source,
          target: l.target,
          realValue: l.value,
        })),
      },
    ],
  };

  return {
    option,
    graphWidth,
    graphHeight,
    hasHiddenPaths: hasHiddenChildren.size > 0,
  };
}
