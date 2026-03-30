import {
  Stack,
  Group,
  SegmentedControl,
  Card,
  Badge,
  Text,
} from "@mantine/core";
import { NetworkVisualization } from "./NetworkVisualization";
import { formatTimestamp } from "../utils/sessionUtils";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { SessionDetailTabPanel } from "./SessionDetailTabPanel";
import { TAB_PANEL_DESCRIPTION, TAB_PANEL_TITLE } from "../constants/strings";

interface NetworkTabProps {
  sessionData: SessionDetailData;
  viewMode: "text" | "graph";
  onViewModeChange: (mode: "text" | "graph") => void;
}

export function NetworkTab({
  sessionData,
  viewMode,
  onViewModeChange,
}: NetworkTabProps) {
  return (
    <SessionDetailTabPanel
      title={TAB_PANEL_TITLE.NETWORK}
      description={TAB_PANEL_DESCRIPTION.NETWORK}
      toolbar={
        <SegmentedControl
          size="xs"
          value={viewMode}
          onChange={(value) => onViewModeChange(value as "text" | "graph")}
          data={[
            { label: "Text", value: "text" },
            { label: "Graph", value: "graph" },
          ]}
        />
      }
    >
      {viewMode === "graph" ? (
        <NetworkVisualization
          networkRequests={sessionData.networkRequests}
          sessionStartTime={new Date(sessionData.startTime)}
        />
      ) : (
        <Stack gap="xs">
          {sessionData.networkRequests.map((req, idx) => (
            <Card key={idx} padding="sm" withBorder>
              <Group justify="space-between" mb={4}>
                <Group gap="xs">
                  <Badge size="xs" variant="light">
                    {req.method}
                  </Badge>
                  <Badge
                    size="xs"
                    color={
                      req.status >= 200 && req.status < 300 ? "teal" : "red"
                    }
                  >
                    {req.status}
                  </Badge>
                </Group>
                <Text size="xs" c="dimmed">
                  {req.duration}ms
                </Text>
              </Group>
              <Text size="sm" ff="monospace" truncate="end">
                {req.url}
              </Text>
              <Text size="xs" c="dimmed" mt={4}>
                {formatTimestamp(
                  req.timestamp,
                  new Date(sessionData.startTime),
                )}
              </Text>
            </Card>
          ))}
        </Stack>
      )}
    </SessionDetailTabPanel>
  );
}
