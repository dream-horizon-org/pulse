import { useCallback } from "react";
import { Card, Group, Loader, Stack, Text, ThemeIcon } from "@mantine/core";
import {
  IconDeviceMobile,
  IconExternalLink,
  IconPlayerPlay,
} from "@tabler/icons-react";
import { useGetSessionDetail } from "../../hooks/useGetSessionDetails/useGetSessionDetails";
import classes from "./SessionCard.module.css";

interface SessionCardProps {
  sessionId: string;
  onNavigate?: (sessionId: string) => void;
}

const formatDuration = (ms: number): string => {
  if (!ms) return "—";
  const seconds = Math.floor(ms / 1000);
  const minutes = Math.floor(seconds / 60);
  return minutes > 0 ? `${minutes}m ${seconds % 60}s` : `${seconds}s`;
};

export const SessionCard = ({ sessionId, onNavigate }: SessionCardProps) => {
  const { data: session, isLoading, error } = useGetSessionDetail(sessionId);

  const handleClick = useCallback(() => {
    onNavigate
      ? onNavigate(sessionId)
      : window.open(`/session-replay/${sessionId}`, "_blank");
  }, [sessionId, onNavigate]);

  const errorCount =
    session?.interactions?.reduce((sum, i) => sum + (i.failureCount || 0), 0) ??
    0;

  return (
    <Card
      className={classes.sessionCard}
      withBorder
      p="sm"
      radius="md"
      onClick={handleClick}
      style={{ cursor: "pointer" }}
    >
      <Card.Section withBorder inheritPadding py="xs">
        <Group justify="space-between" align="center">
          <Stack gap={0} style={{ flex: 1, minWidth: 0 }}>
            <Text size="xs" fw={600} className={classes.sessionId} truncate>
              {sessionId}
            </Text>
            <Text size="10px" c="dimmed">
              Session ID
            </Text>
          </Stack>
          <ThemeIcon variant="light" size="sm" radius="md">
            <IconPlayerPlay size={14} />
          </ThemeIcon>
        </Group>
      </Card.Section>

      <Card.Section inheritPadding py="xs">
        {isLoading ? (
          <Group justify="center" py="sm">
            <Loader size="xs" />
          </Group>
        ) : error ? (
          <Stack gap="4px" align="center" py="sm">
            <Text size="10px" c="red" fw={500}>
              Failed to load session details
            </Text>
            <Text size="9px" c="dimmed">
              Please try again later
            </Text>
          </Stack>
        ) : session ? (
          <Stack gap="6px">
            <Group justify="space-between" wrap="nowrap">
              <Text size="10px" c="dimmed">
                Duration
              </Text>
              <Text size="10px" fw={500}>
                {formatDuration(session.duration)}
              </Text>
            </Group>

            <Group justify="space-between" wrap="nowrap">
              <Text size="10px" c="dimmed">
                Platform
              </Text>
              <Group gap="4px" wrap="nowrap">
                <IconDeviceMobile size={12} />
                <Text size="10px" fw={500}>
                  {session.platform}
                </Text>
              </Group>
            </Group>

            {session.osVersion && (
              <Group justify="space-between" wrap="nowrap">
                <Text size="10px" c="dimmed">
                  OS
                </Text>
                <Text size="10px" fw={500}>
                  {session.osVersion}
                </Text>
              </Group>
            )}

            {session.appVersion && (
              <Group justify="space-between" wrap="nowrap">
                <Text size="10px" c="dimmed">
                  App Ver
                </Text>
                <Text size="10px" fw={500} truncate>
                  {session.appVersion}
                </Text>
              </Group>
            )}

            {session.device && (
              <Group justify="space-between" wrap="nowrap">
                <Text size="10px" c="dimmed">
                  Device
                </Text>
                <Text size="10px" fw={500} truncate>
                  {session.device}
                </Text>
              </Group>
            )}

            {errorCount > 0 && (
              <Group justify="space-between" wrap="nowrap">
                <Text size="10px" c="dimmed">
                  Errors
                </Text>
                <Text size="10px" fw={500} c="red">
                  {errorCount}
                </Text>
              </Group>
            )}
          </Stack>
        ) : (
          <Stack gap="4px" align="center" py="sm">
            <Text size="10px" c="dimmed">
              No session data available
            </Text>
          </Stack>
        )}
      </Card.Section>

      <Card.Section withBorder inheritPadding py="6px" mt="xs">
        <Group justify="center" gap="4px">
          <Text size="10px" fw={500} c="blue">
            Session Replay
          </Text>
          <IconExternalLink size={10} />
        </Group>
      </Card.Section>
    </Card>
  );
};
