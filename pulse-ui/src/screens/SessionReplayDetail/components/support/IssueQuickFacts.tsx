import { Card, Text, Stack, Alert, Group, Badge } from "@mantine/core";
import { IconAlertTriangle, IconCheck } from "@tabler/icons-react";
import type { DetectedIssue } from "../../../../services/sessionReplay/mockSessionDetail";
import { HEADERS, MESSAGES } from "../../constants/strings";

function formatTimestamp(ms: number): string {
  const seconds = Math.floor(ms / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const remainingSeconds = seconds % 60;
  return `${minutes}m ${remainingSeconds}s`;
}

interface IssueQuickFactsProps {
  criticalIssues: DetectedIssue[];
}

export function IssueQuickFacts({ criticalIssues }: IssueQuickFactsProps) {
  return (
    <Card padding="md" withBorder>
      <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">
        {HEADERS.ISSUE_QUICK_FACTS}
      </Text>

      {criticalIssues.length > 0 ? (
        <Stack gap="md">
          {criticalIssues.map((issue, idx) => (
            <Alert
              key={idx}
              color={issue.severity === "critical" ? "red" : "orange"}
              title={issue.title}
              icon={<IconAlertTriangle size={16} />}
            >
              <Text size="sm" mb="xs">
                {issue.userFacingImpact}
              </Text>
              <Group gap="xs">
                <Badge size="sm" variant="light">
                  {issue.affectedFeature || MESSAGES.UNKNOWN_FEATURE}
                </Badge>
                <Badge size="sm" variant="light" color="gray">
                  {formatTimestamp(issue.timestamp)}
                </Badge>
              </Group>
            </Alert>
          ))}
        </Stack>
      ) : (
        <Alert
          color="teal"
          title={MESSAGES.NO_CRITICAL_ISSUES}
          icon={<IconCheck size={16} />}
        >
          <Text size="sm">{MESSAGES.NO_CRITICAL_ISSUES_DESCRIPTION}</Text>
        </Alert>
      )}
    </Card>
  );
}
