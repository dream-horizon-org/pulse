import { Box, Group, SegmentedControl, Text } from "@mantine/core";
import { useState } from "react";
import type { NetworkRequest } from "../../../services/sessionReplay/mockSessionDetail";
import { WaterfallChart } from "./network/WaterfallChart";
import { StatusChart } from "./network/StatusChart";
import { DurationChart } from "./network/DurationChart";
import {
  HEADERS,
  NETWORK_VIEW_MODES,
  NETWORK_VIEW_MODE_LABELS,
} from "../constants/strings";

interface NetworkVisualizationProps {
  networkRequests: NetworkRequest[];
  sessionStartTime: Date;
}

type ViewMode = "waterfall" | "status" | "duration";

export function NetworkVisualization({
  networkRequests,
  sessionStartTime,
}: NetworkVisualizationProps) {
  const [viewMode, setViewMode] = useState<ViewMode>(
    NETWORK_VIEW_MODES.WATERFALL,
  );

  return (
    <Box>
      <Group justify="space-between" mb="md">
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          {HEADERS.NETWORK_REQUESTS_VISUALIZATION}
        </Text>
        <SegmentedControl
          size="xs"
          value={viewMode}
          onChange={(value) => setViewMode(value as ViewMode)}
          data={[
            {
              label: NETWORK_VIEW_MODE_LABELS.WATERFALL,
              value: NETWORK_VIEW_MODES.WATERFALL,
            },
            {
              label: NETWORK_VIEW_MODE_LABELS.STATUS,
              value: NETWORK_VIEW_MODES.STATUS,
            },
            {
              label: NETWORK_VIEW_MODE_LABELS.DURATION,
              value: NETWORK_VIEW_MODES.DURATION,
            },
          ]}
        />
      </Group>

      {viewMode === NETWORK_VIEW_MODES.WATERFALL && (
        <WaterfallChart networkRequests={networkRequests} />
      )}

      {viewMode === NETWORK_VIEW_MODES.STATUS && (
        <StatusChart networkRequests={networkRequests} />
      )}

      {viewMode === NETWORK_VIEW_MODES.DURATION && (
        <DurationChart networkRequests={networkRequests} />
      )}
    </Box>
  );
}
