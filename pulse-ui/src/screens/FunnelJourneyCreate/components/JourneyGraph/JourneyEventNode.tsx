import { memo } from "react";
import { Handle, Position, type NodeProps } from "@xyflow/react";
import type { JourneyNodeData } from "./journeyFlow.types";
import classes from "./JourneyEventNode.module.css";

function accentColor(data: JourneyNodeData): string {
  if (data.isExit) return "#ef4444";
  if (data.isOther) return "#94a3b8";
  if (data.isExpandable) return "#3b82f6";
  return "#0ba09a";
}

function JourneyEventNodeInner({ data }: NodeProps & { data: JourneyNodeData }) {
  const color = accentColor(data);

  return (
    <>
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
      <div
        className={classes.card}
        style={{ "--accent-color": color } as React.CSSProperties}
      >
        <div className={classes.eventName}>{data.displayName}</div>
        <div className={classes.stats}>
          {data.userCount.toLocaleString()} users ({data.percentage}%)
        </div>
        {data.isExpandable && (
          <button
            className={`${classes.expandBtn} ${classes.expandMore}`}
            onClick={(e) => {
              e.stopPropagation();
              data.onToggleExpand(data.rawName);
            }}
          >
            ▸ more
          </button>
        )}
        {data.isExpanded && (
          <button
            className={`${classes.expandBtn} ${classes.expandLess}`}
            onClick={(e) => {
              e.stopPropagation();
              data.onToggleExpand(data.rawName);
            }}
          >
            ◂ less
          </button>
        )}
      </div>
      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
    </>
  );
}

export const JourneyEventNode = memo(JourneyEventNodeInner);
