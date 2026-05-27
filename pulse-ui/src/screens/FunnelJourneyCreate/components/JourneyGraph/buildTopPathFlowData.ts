import type { Edge, Node } from "@xyflow/react";
import type { JourneyTopPath, JourneyTopPathStep } from "../../../../services/funnels.service";
import { JOURNEY_GRAPH_DEPTH_WINDOW } from "./journeyGraph.constants";
import type { JourneyEdgeData, JourneyNodeData } from "./journeyFlow.types";

const noopToggle = () => undefined;

function pct(value: number, total: number): number {
  if (total <= 0) return 0;
  return Math.round((value / total) * 1000) / 10;
}

function nodeId(stepName: string, position: number): string {
  return `${stepName}::${position}`;
}

export type TopPathFlowResult = {
  nodes: Node<JourneyNodeData>[];
  edges: Edge<JourneyEdgeData>[];
  displaySteps: JourneyTopPathStep[];
  displayAnchorTraffic: number;
  displayPathTraffic: number;
};

/**
 * Keep only steps visible in the default full-journey graph (same depth window).
 * Positions 0..depthWindow-1 for START, 0..-(depthWindow-1) for END.
 */
export function filterTopPathStepsForGraphView(
  steps: JourneyTopPathStep[],
  depthWindow = JOURNEY_GRAPH_DEPTH_WINDOW,
): JourneyTopPathStep[] {
  return steps.filter((step) => Math.abs(step.position) < depthWindow);
}

function pathBottleneck(steps: JourneyTopPathStep[]): number {
  if (steps.length <= 1) return steps[0]?.traffic ?? 0;
  return Math.min(...steps.slice(1).map((s) => s.traffic));
}

/** Maps greedy top-path steps into the same React Flow shape as the full journey graph. */
export function buildTopPathFlowData(topPath: JourneyTopPath): TopPathFlowResult {
  const steps = filterTopPathStepsForGraphView(topPath.steps ?? []);
  const anchorTotal = steps[0]?.traffic ?? topPath.anchorTraffic ?? 0;
  const displayPathTraffic = pathBottleneck(steps);

  const nodes: Node<JourneyNodeData>[] = steps.map((step) => {
    const rawName = nodeId(step.stepName, step.position);
    return {
      id: rawName,
      type: "journeyEvent",
      position: { x: 0, y: 0 },
      data: {
        displayName: step.stepName,
        rawName,
        userCount: step.traffic,
        percentage: pct(step.traffic, anchorTotal),
        isExpandable: false,
        isExpanded: false,
        isExit: false,
        isOther: false,
        onToggleExpand: noopToggle,
      },
    };
  });

  const edges: Edge<JourneyEdgeData>[] = [];
  for (let i = 0; i < steps.length - 1; i++) {
    const source = steps[i];
    const target = steps[i + 1];
    const sourceId = nodeId(source.stepName, source.position);
    const targetId = nodeId(target.stepName, target.position);
    const edgeTraffic = target.traffic;

    edges.push({
      id: `top-path-${sourceId}-${targetId}`,
      source: sourceId,
      target: targetId,
      type: "journeyEdge",
      data: {
        userCount: edgeTraffic,
        percentage: pct(edgeTraffic, anchorTotal),
        sourceDisplayName: source.stepName,
        targetDisplayName: target.stepName,
      },
    });
  }

  return { nodes, edges, displaySteps: steps, displayAnchorTraffic: anchorTotal, displayPathTraffic };
}
