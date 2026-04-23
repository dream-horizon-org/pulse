import { memo, useState } from "react";
import {
  getBezierPath,
  EdgeLabelRenderer,
  type EdgeProps,
} from "@xyflow/react";
import type { JourneyEdgeData } from "./journeyFlow.types";

function JourneyFlowEdgeInner(props: EdgeProps & { data?: JourneyEdgeData }) {
  const {
    id,
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
    data,
  } = props;

  const [hovered, setHovered] = useState(false);

  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });

  return (
    <>
      {/* Invisible wider path for easier hover targeting */}
      <path
        d={edgePath}
        fill="none"
        stroke="transparent"
        strokeWidth={16}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
      />
      {/* Visible edge */}
      <path
        id={id}
        d={edgePath}
        fill="none"
        stroke={hovered ? "#0ba09a" : "#b0bec5"}
        strokeWidth={hovered ? 2 : 1.2}
        strokeOpacity={hovered ? 1 : 0.6}
        markerEnd="url(#journey-arrow)"
        style={{ transition: "stroke 0.15s, stroke-width 0.15s" }}
      />
      {/* Tooltip on hover */}
      {hovered && data && (
        <EdgeLabelRenderer>
          <div
            style={{
              position: "absolute",
              transform: `translate(-50%, -50%) translate(${labelX}px,${labelY}px)`,
              pointerEvents: "none",
              background: "#1e293b",
              color: "#fff",
              padding: "6px 10px",
              borderRadius: 6,
              fontSize: 11,
              fontWeight: 500,
              whiteSpace: "nowrap",
              boxShadow: "0 2px 8px rgba(0,0,0,0.2)",
              zIndex: 10,
            }}
          >
            <strong>{data.sourceDisplayName}</strong> →{" "}
            <strong>{data.targetDisplayName}</strong>
            <br />
            Users: {data.userCount.toLocaleString()} ({data.percentage}%)
          </div>
        </EdgeLabelRenderer>
      )}
    </>
  );
}

export const JourneyFlowEdge = memo(JourneyFlowEdgeInner);
