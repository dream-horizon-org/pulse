import { Card, Text, Group, Badge, Alert, Stack, Divider } from "@mantine/core";
import { IconBug } from "@tabler/icons-react";
import type { SupportContext } from "../../../../services/sessionReplay/mockSessionDetail";
import { HEADERS, LABELS } from "../../constants/strings";

interface KnownIssueProps {
  knownIssue: NonNullable<SupportContext["matchesKnownIssue"]>;
}

export function KnownIssue({ knownIssue }: KnownIssueProps) {
  return (
    <Card padding="md" withBorder>
      <Group justify="space-between" mb="md">
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          {HEADERS.KNOWN_ISSUE}
        </Text>
        <Badge color="blue" variant="light">
          Issue #{knownIssue.issueId.split("_")[2]}
        </Badge>
      </Group>

      <Alert color="blue" title={knownIssue.title} icon={<IconBug size={16} />}>
        <Stack gap="sm">
          <Text size="sm">
            {LABELS.AFFECTED_USERS}: <strong>{knownIssue.affectedUsers}</strong>
          </Text>
          <Text size="sm">
            {LABELS.STATUS}:{" "}
            <Badge
              size="sm"
              color={knownIssue.status === "resolved" ? "teal" : "orange"}
            >
              {knownIssue.status.replace("_", " ")}
            </Badge>
          </Text>
          {knownIssue.workaround && (
            <>
              <Divider />
              <Text size="sm" fw={600}>
                {LABELS.WORKAROUND_AVAILABLE}:
              </Text>
              <Text size="sm" c="dimmed">
                {knownIssue.workaround}
              </Text>
            </>
          )}
        </Stack>
      </Alert>
    </Card>
  );
}
