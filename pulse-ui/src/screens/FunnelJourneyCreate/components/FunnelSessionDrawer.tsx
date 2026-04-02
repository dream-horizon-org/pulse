import {
  Drawer,
  Table,
  Text,
  Badge,
  Group,
  Loader,
  Box,
  Anchor,
  ScrollArea,
  SegmentedControl,
  Stack,
} from "@mantine/core";
import {
  IconBug,
  IconAlertTriangle,
  IconExclamationCircle,
  IconExternalLink,
} from "@tabler/icons-react";
import { useState } from "react";
import { useGetFunnelSessions, FunnelStep } from "../../../hooks/useGetFunnelData";
import { TimeRange } from "../../../hooks/useGetDataQuery/useGetDataQuery.interface";

interface FunnelSessionDrawerProps {
  opened: boolean;
  onClose: () => void;
  stepLevel: number;
  issueType: string;
  steps: FunnelStep[];
  timeRange: TimeRange;
  mode: "UNIQUE_USERS" | "SESSIONS";
  windowSeconds: number;
}

const ISSUE_TYPE_OPTIONS = [
  { value: "ALL", label: "All Issues" },
  { value: "CRASH", label: "Crashes" },
  { value: "ANR", label: "ANRs" },
  { value: "NON_FATAL", label: "Non-Fatal" },
];

export function FunnelSessionDrawer({
  opened,
  onClose,
  stepLevel,
  issueType: initialIssueType,
  steps,
  timeRange,
  mode,
  windowSeconds,
}: FunnelSessionDrawerProps) {
  const [issueType, setIssueType] = useState(initialIssueType);

  const { data, isLoading, isError } = useGetFunnelSessions({
    requestBody: {
      steps,
      timeRange,
      mode,
      windowSeconds,
      stepLevel,
      issueType: issueType as "ALL" | "CRASH" | "ANR" | "NON_FATAL",
      limit: 100,
    },
    enabled: opened && stepLevel >= 1,
  });

  const sessionsResult = data?.data;
  const stepName =
    stepLevel <= steps.length ? steps[stepLevel - 1].eventName : `Step ${stepLevel}`;

  const getEventBadge = (eventName: string) => {
    switch (eventName) {
      case "device.crash":
        return (
          <Badge
            size="xs"
            variant="filled"
            color="red"
            leftSection={<IconBug size={10} />}
          >
            Crash
          </Badge>
        );
      case "device.anr":
        return (
          <Badge
            size="xs"
            variant="filled"
            color="orange"
            leftSection={<IconAlertTriangle size={10} />}
          >
            ANR
          </Badge>
        );
      case "non_fatal":
        return (
          <Badge
            size="xs"
            variant="filled"
            color="yellow"
            leftSection={<IconExclamationCircle size={10} />}
          >
            Non-Fatal
          </Badge>
        );
      default:
        return (
          <Badge size="xs" variant="light" color="gray">
            {eventName}
          </Badge>
        );
    }
  };

  return (
    <Drawer
      opened={opened}
      onClose={onClose}
      title={
        <Group gap="sm">
          <Text fw={700} size="md">
            Sessions at Step {stepLevel}
          </Text>
          <Badge variant="light" color="teal" size="sm">
            {stepName}
          </Badge>
        </Group>
      }
      position="right"
      size="xl"
      padding="md"
    >
      <Stack gap="md">
        <SegmentedControl
          value={issueType}
          onChange={setIssueType}
          data={ISSUE_TYPE_OPTIONS}
          size="xs"
          color="teal"
        />

        {isLoading && (
          <Box
            style={{
              display: "flex",
              justifyContent: "center",
              padding: 40,
            }}
          >
            <Loader color="teal" size="md" />
          </Box>
        )}

        {isError && (
          <Text size="sm" c="red" ta="center" py="xl">
            Failed to load session data
          </Text>
        )}

        {!isLoading && !isError && sessionsResult && (
          <>
            <Group gap="md">
              <Text size="sm" c="dimmed">
                Showing{" "}
                <Text span fw={600} c="dark">
                  {sessionsResult.sessions.length}
                </Text>{" "}
                affected sessions
              </Text>
            </Group>

            <ScrollArea h="calc(100vh - 200px)">
              <Table
                striped
                highlightOnHover
                withTableBorder
                withColumnBorders
                fz="xs"
              >
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Type</Table.Th>
                    <Table.Th>Exception</Table.Th>
                    <Table.Th>Screen</Table.Th>
                    <Table.Th>Device</Table.Th>
                    <Table.Th>Time</Table.Th>
                    <Table.Th style={{ width: 50 }}>View</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {sessionsResult.sessions.map((session, idx) => (
                    <Table.Tr key={`${session.sessionId}-${idx}`}>
                      <Table.Td>{getEventBadge(session.eventName)}</Table.Td>
                      <Table.Td style={{ maxWidth: 200 }}>
                        <Text size="xs" fw={500} truncate>
                          {session.exceptionType || "—"}
                        </Text>
                        <Text size="xs" c="dimmed" truncate>
                          {session.exceptionMessage || session.title || "—"}
                        </Text>
                      </Table.Td>
                      <Table.Td>
                        <Text size="xs">{session.screenName || "—"}</Text>
                      </Table.Td>
                      <Table.Td>
                        <Text size="xs">{session.deviceModel || "—"}</Text>
                        <Text size="xs" c="dimmed">
                          {[session.platform, session.appVersion]
                            .filter(Boolean)
                            .join(" · ")}
                        </Text>
                      </Table.Td>
                      <Table.Td>
                        <Text size="xs">
                          {session.timestamp
                            ? new Date(session.timestamp).toLocaleString()
                            : "—"}
                        </Text>
                      </Table.Td>
                      <Table.Td>
                        {session.sessionId && (
                          <Anchor
                            href={`/session/${session.sessionId}`}
                            target="_blank"
                            size="xs"
                          >
                            <IconExternalLink size={14} />
                          </Anchor>
                        )}
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>

              {sessionsResult.sessions.length === 0 && (
                <Text size="sm" c="dimmed" ta="center" py="xl">
                  No sessions with issues found at this step
                </Text>
              )}
            </ScrollArea>
          </>
        )}
      </Stack>
    </Drawer>
  );
}
