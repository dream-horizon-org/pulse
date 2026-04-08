import {
  Stack,
  Group,
  SegmentedControl,
  Card,
  Badge,
  Text,
  Title,
} from "@mantine/core";
import { NetworkVisualization } from "./NetworkVisualization";
import { formatTimestamp } from "../utils/sessionUtils";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { HEADERS, MESSAGES } from "../constants/strings";
import { TabPanelScrollArea } from "./TabPanelScrollArea";
import classes from "../SessionReplayDetail.module.css";

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
    <Stack gap="md" style={{ flex: 1, minHeight: 0 }}>
      <Group
        justify="space-between"
        align="flex-start"
        wrap="nowrap"
        gap="md"
        flex="0 0 auto"
      >
        <Stack
          gap={0}
          maw="calc(100% - 140px)"
          style={{ flex: 1, minWidth: 0 }}
        >
          <Title
            order={4}
            size="h5"
            className={classes.sessionReplaySectionTitle}
          >
            {HEADERS.SESSION_REPLAY_NETWORK_TITLE}
          </Title>
          <Text size="sm" c="dimmed" mt={4}>
            {MESSAGES.SESSION_REPLAY_NETWORK_DESCRIPTION}
          </Text>
        </Stack>
        <SegmentedControl
          size="xs"
          value={viewMode}
          onChange={(value) => onViewModeChange(value as "text" | "graph")}
          data={[
            { label: "Text", value: "text" },
            { label: "Graph", value: "graph" },
          ]}
          style={{ flexShrink: 0 }}
        />
      </Group>
      <TabPanelScrollArea>
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
                  {formatTimestamp(req.timestamp, sessionData.startTime)}
                </Text>
              </Card>
            ))}
          </Stack>
        )}
      </TabPanelScrollArea>
    </Stack>
  );
}
