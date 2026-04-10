import type { Node, Edge } from "@xyflow/react";
import type { JourneyResponse } from "../../../../hooks/useGetFunnelData";
import type { JourneyNodeData, JourneyEdgeData } from "./journeyFlow.types";

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const DEPTH_WINDOW = 5;
const TOP_N_PER_DEPTH = 8;

// ---------------------------------------------------------------------------
// Helpers (ported from buildJourneySankeyOption.ts)
// ---------------------------------------------------------------------------

function stripDepth(name: string): string {
  const idx = name.lastIndexOf("::");
  return idx === -1 ? name : name.slice(0, idx);
}

function isDepthQualified(data: JourneyResponse): boolean {
  return data.links.some(
    (l) => /::[-]?\d+$/.test(l.source) || /::[-]?\d+$/.test(l.target),
  );
}

function parseDepth(name: string): number {
  const m = name.match(/::(-?\d+)$/);
  return m ? parseInt(m[1], 10) : -999;
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
// Sanitize — legacy format
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
  expandedNodes: Set<string>;
  globalExpanded: boolean;
}

export interface JourneyFlowResult {
  nodes: Node<JourneyNodeData>[];
  edges: Edge<JourneyEdgeData>[];
  hasHiddenPaths: boolean;
}

// ---------------------------------------------------------------------------
// Main transform
// ---------------------------------------------------------------------------

export function buildJourneyFlowData(
  rawData: JourneyResponse,
  expansion: ExpansionState,
  onToggleExpand: (rawName: string) => void,
): JourneyFlowResult {
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

  // ── Flow per node ─────────────────────────────────────────────────────
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

  // ── Forward adjacency ─────────────────────────────────────────────────
  const adj = new Map<string, string[]>();
  for (const link of data.links) {
    if (!adj.has(link.source)) adj.set(link.source, []);
    adj.get(link.source)!.push(link.target);
  }

  // ── Determine visible nodes ───────────────────────────────────────────
  const allDepths = Array.from(new Set(Array.from(nodeDepth.values()))).sort(
    (a, b) => a - b,
  );
  const minDepth = allDepths[0] || 0;
  const baseMaxDepth = minDepth + DEPTH_WINDOW - 1;

  const visibleNodes = new Set<string>();

  if (expansion.globalExpanded) {
    for (const node of data.nodes) visibleNodes.add(node.name);
  } else {
    for (const node of data.nodes) {
      const d = nodeDepth.get(node.name) ?? 0;
      if (d <= baseMaxDepth) visibleNodes.add(node.name);
    }
    if (expansion.expandedNodes.size) {
      const sortedExpanded = Array.from(expansion.expandedNodes)
        .filter((n) => nodeDepth.has(n))
        .sort((a, b) => (nodeDepth.get(a) ?? 0) - (nodeDepth.get(b) ?? 0));

      for (const expandedName of sortedExpanded) {
        if (!visibleNodes.has(expandedName)) continue;
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

  // ── Top-N per depth with "Other" aggregation ──────────────────────────
  const nodesByDepth = new Map<number, typeof filteredNodes>();
  for (const node of filteredNodes) {
    const d = nodeDepth.get(node.name) ?? 0;
    if (!nodesByDepth.has(d)) nodesByDepth.set(d, []);
    nodesByDepth.get(d)!.push(node);
  }

  const mergedNodeNames = new Set<string>();
  const mergedToOther = new Map<string, string>();
  const otherNodeEntries: Array<{ name: string }> = [];
  const otherMergedCount = new Map<string, number>();

  nodesByDepth.forEach((nodesAtDepth, d) => {
    if (nodesAtDepth.length <= TOP_N_PER_DEPTH) return;

    // Sort by inFlow descending, keep top N
    const sorted = [...nodesAtDepth].sort(
      (a, b) => (inFlow.get(b.name) || 0) - (inFlow.get(a.name) || 0),
    );
    const merged = sorted.slice(TOP_N_PER_DEPTH);
    const otherName = `Other::${d}`;
    let otherFlow = 0;

    for (const node of merged) {
      mergedNodeNames.add(node.name);
      mergedToOther.set(node.name, otherName);
      otherFlow += inFlow.get(node.name) || 0;
    }

    otherNodeEntries.push({ name: otherName });
    nodeDepth.set(otherName, d);
    inFlow.set(otherName, otherFlow);
    otherMergedCount.set(otherName, merged.length);
  });

  // Rebuild: remove merged nodes, add Other aggregates
  const aggregatedNodes = filteredNodes
    .filter((n) => !mergedNodeNames.has(n.name))
    .concat(otherNodeEntries);

  // Rebuild links: redirect merged→Other, deduplicate, drop self-loops
  const linkBucket = new Map<string, number>();
  for (const link of filteredLinks) {
    const src = mergedToOther.get(link.source) ?? link.source;
    const tgt = mergedToOther.get(link.target) ?? link.target;
    if (src === tgt) continue;
    const key = `${src}\0${tgt}`;
    linkBucket.set(key, (linkBucket.get(key) || 0) + link.value);
  }
  const aggregatedLinks: JourneyResponse["links"] = [];
  linkBucket.forEach((value, key) => {
    const [source, target] = key.split("\0");
    aggregatedLinks.push({ source, target, value });
  });

  // ── Boundary detection ────────────────────────────────────────────────
  const hasHiddenChildren = new Set<string>();
  for (const link of data.links) {
    if (visibleNodes.has(link.source) && !visibleNodes.has(link.target)) {
      hasHiddenChildren.add(link.source);
    }
  }

  // ── Build React Flow nodes ────────────────────────────────────────────
  const nodes: Node<JourneyNodeData>[] = aggregatedNodes.map((node) => {
    const mergedCount = otherMergedCount.get(node.name);
    const display =
      mergedCount != null
        ? `Other (${mergedCount} events)`
        : displayName(node.name);
    const isExpandable =
      hasHiddenChildren.has(node.name) &&
      !expansion.expandedNodes.has(node.name);
    const isExpanded = expansion.expandedNodes.has(node.name);
    const flow = inFlow.get(node.name) || 0;
    const pct = Math.round((flow / rootTotal) * 1000) / 10;

    return {
      id: node.name,
      type: "journeyEvent",
      position: { x: 0, y: 0 }, // dagre will fill these in
      data: {
        displayName: display,
        rawName: node.name,
        userCount: flow,
        percentage: pct,
        isExpandable,
        isExpanded,
        isExit: display === "Exit",
        isOther: mergedCount != null,
        onToggleExpand,
      },
    };
  });

  // ── Build React Flow edges ────────────────────────────────────────────
  const edges: Edge<JourneyEdgeData>[] = aggregatedLinks.map((link, i) => {
    const pct = Math.round((link.value / rootTotal) * 1000) / 10;
    return {
      id: `e-${link.source}-${link.target}-${i}`,
      source: link.source,
      target: link.target,
      type: "journeyEdge",
      data: {
        userCount: link.value,
        percentage: pct,
        sourceDisplayName: displayName(link.source),
        targetDisplayName: displayName(link.target),
      },
    };
  });

  return {
    nodes,
    edges,
    hasHiddenPaths: hasHiddenChildren.size > 0,
  };
}
