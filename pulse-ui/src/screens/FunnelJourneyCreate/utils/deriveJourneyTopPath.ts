export type JourneyTopPathStep = {
  position: number;
  stepName: string;
  traffic: number;
};

export type JourneyTopPath = {
  steps: JourneyTopPathStep[];
  complete: boolean;
  anchorTraffic: number;
  pathTraffic: number;
  incompletenessReason?: string;
};

type JourneyLink = { source: string; target: string; value: number };
type JourneyGraph = { nodes: { name: string }[]; links: JourneyLink[] };

type NodeKey = { pos: number; event: string };
type Edge = { from: NodeKey; to: NodeKey; traffic: number };

function parseDepth(name: string): number | null {
  const m = name.match(/::(-?\d+)$/);
  return m ? parseInt(m[1], 10) : null;
}

function stripDepth(name: string): string {
  const idx = name.lastIndexOf("::");
  return idx === -1 ? name : name.slice(0, idx);
}

function nodeKeyFromLabel(label: string): NodeKey | null {
  const pos = parseDepth(label);
  if (pos == null) return null;
  return { pos, event: stripDepth(label) };
}

function isEntryNode(node: NodeKey): boolean {
  return node.pos === -1 && node.event === "";
}

function parseEdges(links: JourneyLink[]): Edge[] {
  const edges: Edge[] = [];
  for (const link of links) {
    const from = nodeKeyFromLabel(link.source);
    const to = nodeKeyFromLabel(link.target);
    if (!from || !to || to.pos !== from.pos + 1) continue;
    if (!to.event) continue;
    edges.push({ from, to, traffic: link.value ?? 0 });
  }
  return edges;
}

function findEntryTraffic(links: JourneyLink[], anchor: string): number {
  for (const link of links) {
    if (link.source !== "ENTRY") continue;
    const to = nodeKeyFromLabel(link.target);
    if (to?.pos === 0 && to.event === anchor) {
      return link.value ?? 0;
    }
  }
  return 0;
}

function bestIncoming(edges: Edge[], target: NodeKey): Edge | null {
  const matches = edges.filter(
    (e) =>
      e.to.pos === target.pos &&
      e.to.event === target.event &&
      !isEntryNode(e.from),
  );
  if (!matches.length) return null;
  return matches.sort(
    (a, b) =>
      b.traffic - a.traffic ||
      a.from.event.localeCompare(b.from.event) ||
      a.to.event.localeCompare(b.to.event),
  )[0];
}

function bestOutgoing(edges: Edge[], source: NodeKey): Edge | null {
  const matches = edges.filter(
    (e) => e.from.pos === source.pos && e.from.event === source.event,
  );
  if (!matches.length) return null;
  return matches.sort(
    (a, b) =>
      b.traffic - a.traffic ||
      a.to.event.localeCompare(b.to.event) ||
      a.from.event.localeCompare(b.from.event),
  )[0];
}

function maxIncoming(edges: Edge[], target: NodeKey): number {
  return edges
    .filter(
      (e) =>
        e.to.pos === target.pos &&
        e.to.event === target.event &&
        !isEntryNode(e.from),
    )
    .reduce((max, e) => Math.max(max, e.traffic), 0);
}

function emptyTopPath(reason: string): JourneyTopPath {
  return {
    steps: [],
    complete: false,
    anchorTraffic: 0,
    pathTraffic: 0,
    incompletenessReason: reason,
  };
}

/** Mirrors backend {@code JourneyMostVisitedPathDeriver} for mock/local use. */
export function deriveJourneyTopPath(
  graph: JourneyGraph | undefined,
  anchorEvent: string,
  direction: string,
  depth: number,
): JourneyTopPath | undefined {
  if (!graph?.links?.length || !anchorEvent.trim()) {
    return undefined;
  }

  const depthQualified = graph.links.some(
    (l) => /::[-]?\d+$/.test(l.source) || /::[-]?\d+$/.test(l.target),
  );
  if (!depthQualified) {
    return undefined;
  }

  const anchor = anchorEvent.trim();
  const edges = parseEdges(graph.links);
  if (!edges.length) {
    return emptyTopPath("No transition edges available");
  }

  const anchorTraffic = findEntryTraffic(graph.links, anchor);
  const isEnd = (direction || "START").toUpperCase() === "END";
  const steps: JourneyTopPathStep[] = [];
  let pathTraffic = Number.MAX_SAFE_INTEGER;
  let gap = false;

  let current: NodeKey = { pos: 0, event: anchor };
  let anchorStepTraffic = maxIncoming(edges, current);
  if (anchorStepTraffic <= 0 && anchorTraffic > 0) {
    anchorStepTraffic = anchorTraffic;
  }
  steps.push({
    position: current.pos,
    stepName: current.event,
    traffic: anchorStepTraffic,
  });

  while (true) {
    const best = isEnd
      ? bestIncoming(edges, current)
      : bestOutgoing(edges, current);
    if (!best) {
      gap = steps.length === 1;
      break;
    }
    if (isEnd) {
      if (isEntryNode(best.from)) break;
      pathTraffic = Math.min(pathTraffic, best.traffic);
      current = best.from;
      steps.unshift({
        position: current.pos,
        stepName: current.event,
        traffic: best.traffic,
      });
      if (depth > 0 && current.pos <= -depth) break;
    } else {
      pathTraffic = Math.min(pathTraffic, best.traffic);
      current = best.to;
      steps.push({
        position: current.pos,
        stepName: current.event,
        traffic: best.traffic,
      });
      if (depth > 0 && current.pos >= depth) break;
    }
  }

  const complete = !gap && steps.length > 1;
  return {
    steps,
    complete,
    anchorTraffic,
    pathTraffic: pathTraffic === Number.MAX_SAFE_INTEGER ? 0 : pathTraffic,
    incompletenessReason: complete
      ? undefined
      : "Not enough path data to derive a full top path",
  };
}
