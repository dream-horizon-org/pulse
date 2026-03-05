import { Card, Text, Group, Badge, Alert } from "@mantine/core";
import { IconUsers } from "@tabler/icons-react";
import { HEADERS, LABELS, MESSAGES, FORMAT_STRINGS } from "../../constants/strings";

interface SimilarIssuesProps {
  similarErrorsToday: number;
}

export function SimilarIssues({ similarErrorsToday }: SimilarIssuesProps) {
  if (similarErrorsToday <= 0) {
    return null;
  }

  return (
    <Card padding="md" withBorder>
      <Group justify="space-between" mb="md">
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          {HEADERS.SIMILAR_ISSUES_TODAY}
        </Text>
        <Badge size="lg" color="orange" variant="filled">
          {FORMAT_STRINGS.USERS_AFFECTED.replace(
            "{count}",
            similarErrorsToday.toString(),
          )}
        </Badge>
      </Group>

      <Alert color="orange" icon={<IconUsers size={16} />}>
        <Text size="sm">
          {MESSAGES.PATTERN_DETECTION_MESSAGE.replace(
            "{count}",
            similarErrorsToday.toString(),
          )}
        </Text>
      </Alert>
    </Card>
  );
}
