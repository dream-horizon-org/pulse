import { Box, Button, Card, Group, Loader, Text } from "@mantine/core";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import { Link } from "react-router-dom";
import type { SessionDetailApiResponse } from "../../../../services/sessionReplay/types";
import { useGetSessionDetail } from "../../../../hooks/useGetSessionDetails/useGetSessionDetails";
import { buildSessionReplayEvidenceHref } from "./buildSessionReplayEvidenceHref";
import classes from "./RcaSessionReplayEvidenceCard.module.css";

dayjs.extend(relativeTime);

const SESSION_REPLAY_EVIDENCE_DESCRIPTION =
  "Watch taps, screens, and errors in this recorded session in the context of this segment.";

function sessionMetadataLine(session: SessionDetailApiResponse): string {
  const os = session.osVersion?.trim();
  const app = session.appVersion?.trim();
  const device = session.device?.trim();
  const parts: string[] = [];
  if (session.platform && os) {
    parts.push(`${session.platform} ${os}`);
  } else if (session.platform) {
    parts.push(session.platform);
  }
  if (app) parts.push(`App ${app}`);
  if (device) parts.push(device);
  return parts.join(" • ");
}

function relativeTimeFromStart(startTime: string | undefined): string | null {
  if (!startTime?.trim()) return null;
  const d = dayjs(startTime);
  return d.isValid() ? d.fromNow() : null;
}

export interface RcaSessionReplayEvidenceCardProps {
  sessionId: string;
  segmentTitle: string;
  projectId?: string | null;
}

export function RcaSessionReplayEvidenceCard({
  sessionId,
  segmentTitle,
  projectId,
}: RcaSessionReplayEvidenceCardProps) {
  const { data: session, isLoading, error } = useGetSessionDetail(sessionId);
  const href = buildSessionReplayEvidenceHref(projectId, sessionId);
  const timeLabel = session ? relativeTimeFromStart(session.startTime) : null;
  const metaLine = session ? sessionMetadataLine(session) : "";
  const contextLine =
    metaLine.trim() !== ""
      ? metaLine
      : segmentTitle.trim() !== ""
        ? segmentTitle.trim()
        : "—";

  return (
    <Card
      className={classes.sessionEvidenceCard}
      withBorder
      padding="md"
      radius="md"
    >
      <Group
        justify="space-between"
        align="flex-start"
        wrap="nowrap"
        gap="xs"
        mb="xs"
      >
        <Text className={classes.typeLabel} tt="uppercase" size="xs" fw={700}>
          Session replay
        </Text>
        {timeLabel ? (
          <Text size="xs" c="dimmed" ta="right" className={classes.timeLabel}>
            {timeLabel}
          </Text>
        ) : null}
      </Group>

      <Text
        className={classes.cardTitle}
        fw={700}
        size="sm"
        mb={6}
        lineClamp={2}
      >
        {sessionId}
      </Text>

      <Box mb="sm">
        {isLoading ? (
          <Group gap="xs" wrap="nowrap" py={2}>
            <Loader size="xs" />
            <Text size="xs" c="dimmed">
              Loading session…
            </Text>
          </Group>
        ) : (
          <Text size="xs" c="dimmed" lineClamp={3}>
            {error
              ? "Could not load session details — you can still open the replay."
              : contextLine}
          </Text>
        )}
      </Box>

      <Text
        size="xs"
        c="gray.6"
        lh={1.55}
        mb="md"
        className={classes.description}
      >
        {SESSION_REPLAY_EVIDENCE_DESCRIPTION}
      </Text>

      <Button
        component={Link}
        to={href}
        fullWidth
        size="sm"
        className={classes.viewDetailButton}
        variant="filled"
      >
        View Detail
      </Button>
    </Card>
  );
}
