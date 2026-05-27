import { Box, Text } from "@mantine/core";
import { ReactFlowProvider } from "@xyflow/react";
import { useMemo } from "react";
import type { JourneyTopPath } from "../../../services/funnels.service";
import {
  JourneyFlowGraph,
  buildTopPathFlowData,
} from "../../FunnelJourneyCreate/components/JourneyGraph";
import classes from "./JourneyTopPathPanel.module.css";

type JourneyTopPathPanelProps = {
  topPath: JourneyTopPath | undefined;
};

export function JourneyTopPathPanel({ topPath }: JourneyTopPathPanelProps) {
  const flowResult = useMemo(
    () => (topPath?.steps?.length ? buildTopPathFlowData(topPath) : null),
    [topPath],
  );

  if (!flowResult || flowResult.nodes.length === 0) {
    return (
      <Box className={classes.panel}>
        <Box className={classes.emptyState}>
          <Text size="sm" c="dimmed">
            {topPath?.incompletenessReason ??
              "No journey results yet. Run analysis first."}
          </Text>
        </Box>
      </Box>
    );
  }

  return (
    <Box className={classes.panel}>
      <ReactFlowProvider>
        <JourneyFlowGraph nodes={flowResult.nodes} edges={flowResult.edges} />
      </ReactFlowProvider>
    </Box>
  );
}
