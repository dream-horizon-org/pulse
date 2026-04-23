import { useEffect } from "react";
import {
  ReactFlow,
  Controls,
  Background,
  BackgroundVariant,
  useReactFlow,
  useNodesState,
  useEdgesState,
  type Node,
  type Edge,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";

import { JourneyEventNode } from "./JourneyEventNode";
import { JourneyFlowEdge } from "./JourneyFlowEdge";
import { applyDagreLayout } from "./journeyFlowLayout";
import type { JourneyNodeData } from "./journeyFlow.types";
import type { JourneyEdgeData } from "./journeyFlow.types";

// Defined outside the component so React Flow doesn't re-register on every render.
const nodeTypes = { journeyEvent: JourneyEventNode };
const edgeTypes = { journeyEdge: JourneyFlowEdge };

const defaultEdgeOptions = { animated: false };

// Custom arrow marker for edges
function ArrowMarker() {
  return (
    <svg style={{ position: "absolute", width: 0, height: 0 }}>
      <defs>
        <marker
          id="journey-arrow"
          viewBox="0 0 10 10"
          refX={8}
          refY={5}
          markerWidth={6}
          markerHeight={6}
          orient="auto-start-reverse"
        >
          <path d="M 0 0 L 10 5 L 0 10 z" fill="#b0bec5" />
        </marker>
      </defs>
    </svg>
  );
}

interface JourneyFlowGraphProps {
  nodes: Node<JourneyNodeData>[];
  edges: Edge<JourneyEdgeData>[];
}

function JourneyFlowGraphInner({
  nodes: inputNodes,
  edges: inputEdges,
}: JourneyFlowGraphProps) {
  const { fitView } = useReactFlow();
  const [nodes, setNodes, onNodesChange] = useNodesState<Node<JourneyNodeData>>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge<JourneyEdgeData>>([]);

  // Sync external props → internal React Flow state with dagre layout
  useEffect(() => {
    const layoutNodes = applyDagreLayout(inputNodes, inputEdges);
    setNodes(layoutNodes);
    setEdges(inputEdges);
    // Re-fit the viewport after layout changes
    const t = setTimeout(() => fitView({ padding: 0.15, duration: 200 }), 50);
    return () => clearTimeout(t);
  }, [inputNodes, inputEdges, setNodes, setEdges, fitView]);

  return (
    <>
      <ArrowMarker />
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        defaultEdgeOptions={defaultEdgeOptions}
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable={false}
        fitView
        minZoom={0.1}
        maxZoom={2}
        proOptions={{ hideAttribution: true }}
      >
        <Controls showInteractive={false} />
        <Background variant={BackgroundVariant.Dots} gap={16} size={0.8} color="#e2e8f0" />
      </ReactFlow>
    </>
  );
}

export { JourneyFlowGraphInner as JourneyFlowGraph };
