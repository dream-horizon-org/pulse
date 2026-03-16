import { Paper, Group, Button, Text, Badge } from "@mantine/core";
import { IconArrowLeft, IconClock, IconUser } from "@tabler/icons-react";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import {
  LABELS,
  STATUS_LABELS_EXTENDED as STATUS_LABELS,
} from "../constants/strings";
import classes from "../SessionReplayDetail.module.css";

interface SessionHeaderProps {
  sessionData: SessionDetailData;
  onBack: () => void;
}

export function SessionHeader({ sessionData, onBack }: SessionHeaderProps) {
  return (
    <Paper className={classes.header} mb="md" withBorder>
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
          <Group gap="xs">
            <IconUser size={16} />
            <Text size="sm" fw={500}>
              {sessionData.userId}
            </Text>
            <Badge size="xs" color="blue" variant="light">
              {STATUS_LABELS.IDENTIFIED_UPPERCASE}
            </Badge>
          </Group>

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
        </Group>
      </Group>
    </Paper>
  );
}
