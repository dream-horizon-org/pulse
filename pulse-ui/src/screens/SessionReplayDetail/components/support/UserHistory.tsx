import { Card, Text, Group, Timeline, Badge } from "@mantine/core";
import { IconHistory, IconCheck, IconX } from "@tabler/icons-react";
import type { SupportContext } from "../../../../services/sessionReplay/mockSessionDetail";
import { HEADERS, STATUS_LABELS } from "../../constants/strings";

interface UserHistoryProps {
  previousIssues: NonNullable<SupportContext["previousIssues"]>;
}

export function UserHistory({ previousIssues }: UserHistoryProps) {
  if (previousIssues.length === 0) {
    return null;
  }

  return (
    <Card padding="md" withBorder>
      <Group mb="md">
        <IconHistory size={18} />
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          {HEADERS.PREVIOUS_ISSUES}
        </Text>
      </Group>

      <Timeline bulletSize={16} lineWidth={2}>
        {previousIssues.map((issue, idx) => (
          <Timeline.Item
            key={idx}
            bullet={
              issue.resolved ? <IconCheck size={12} /> : <IconX size={12} />
            }
            color={issue.resolved ? "teal" : "red"}
          >
            <Text size="sm" fw={500}>
              {issue.issueType}
            </Text>
            <Text size="xs" c="dimmed">
              {new Date(issue.timestamp).toLocaleDateString()}
            </Text>
            <Badge size="xs" color={issue.resolved ? "teal" : "red"} mt={4}>
              {issue.resolved
                ? STATUS_LABELS.RESOLVED
                : STATUS_LABELS.UNRESOLVED}
            </Badge>
          </Timeline.Item>
        ))}
      </Timeline>
    </Card>
  );
}
