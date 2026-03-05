import { Paper, Group, Button, Text, Badge, Box, RingProgress } from "@mantine/core";
import {
  IconArrowLeft,
  IconClock,
  IconDeviceMobile,
  IconUser,
} from "@tabler/icons-react";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { formatDuration } from "../utils/sessionUtils";
import {
  LABELS,
  STATUS_LABELS_EXTENDED as STATUS_LABELS,
} from "../constants/strings";

interface SessionHeaderProps {
  sessionData: SessionDetailData;
  onBack: () => void;
}

function getQualityColor(score: number) {
  if (score >= 8) return "teal";
  if (score >= 6) return "yellow";
  return "red";
}

export function SessionHeader({ sessionData, onBack }: SessionHeaderProps) {
  return (
    <Paper mb="md">
      <Group justify="space-between" align="center" wrap="nowrap">
        <Group gap="md">
          <Button
            variant="subtle"
            color="teal"
            leftSection={<IconArrowLeft size={16} />}
            onClick={onBack}
          >
            {LABELS.BACK}
          </Button>
          <Text size="sm" ff="monospace" c="dimmed">
            {sessionData.sessionId}
          </Text>
        </Group>

        <Group gap="lg">
          {/* Quality Score - Circular Badge */}
          <Group gap="xs">
            <RingProgress
              size={50}
              thickness={6}
              sections={[
                {
                  value: (sessionData.interactionQuality / 10) * 100,
                  color: getQualityColor(sessionData.interactionQuality),
                },
              ]}
              label={
                <Text size="sm" fw={700} ta="center">
                  {sessionData.interactionQuality.toFixed(1)}
                </Text>
              }
            />
            <Box>
              <Text size="xs" fw={600}>
                {LABELS.QUALITY_SCORE}
              </Text>
            </Box>
          </Group>

          {/* User Info */}
          <Group gap="xs">
            <IconUser size={16} />
            <Text size="sm" fw={500}>
              {sessionData.userId}
            </Text>
            <Badge size="xs" color="blue" variant="light">
              {STATUS_LABELS.IDENTIFIED_UPPERCASE}
            </Badge>
          </Group>

          {/* Session Time */}
          <Group gap="xs">
            <IconClock size={16} />
            <Text size="sm" c="dimmed">
              {new Date(sessionData.startTime).toLocaleString("en-US", {
                month: "short",
                day: "numeric",
                hour: "2-digit",
                minute: "2-digit",
              })}{" "}
              {LABELS.SESSION_TIME}
            </Text>
          </Group>

          {/* Device Info */}
          <Group gap="xs">
            <IconDeviceMobile size={16} />
            <Text size="sm">
              {sessionData.device} {sessionData.os}
            </Text>
          </Group>

          {/* Duration */}
          <Text size="sm" fw={500}>
            {formatDuration(sessionData.duration)} {LABELS.DURATION}
          </Text>
        </Group>
      </Group>
    </Paper>
  );
}
