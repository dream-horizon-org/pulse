import { Card, Text, Group, Stack, Badge } from "@mantine/core";
import { IconGitBranch, IconBrandGithub } from "@tabler/icons-react";
import type { TechnicalContext } from "../../../../services/sessionReplay/mockSessionDetail";
import { isRecent } from "../utils/technicalUtils";
import {
  HEADERS,
  STATUS_LABELS_EXTENDED as STATUS_LABELS,
} from "../../constants/strings";

interface RelatedIssuesPRsProps {
  relatedPRs?: TechnicalContext["relatedPRs"];
  relatedJiraIssues?: TechnicalContext["relatedJiraIssues"];
}

export function RelatedIssuesPRs({
  relatedPRs,
  relatedJiraIssues,
}: RelatedIssuesPRsProps) {
  const hasPRs = relatedPRs && relatedPRs.length > 0;
  const hasIssues =
    relatedJiraIssues && relatedJiraIssues.length > 0;

  if (!hasPRs && !hasIssues) {
    return null;
  }

  return (
    <Card padding="md" withBorder>
      <Group mb="md">
        <IconGitBranch size={18} />
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          {HEADERS.RELATED_ISSUES_PRS}
        </Text>
      </Group>

      <Stack gap="md">
        {relatedPRs?.map((pr, idx) => (
          <Card key={idx} padding="sm" withBorder>
            <Group justify="space-between">
              <Group gap="xs">
                <IconBrandGithub size={16} />
                <Text size="sm" fw={500}>
                  {pr.id}
                </Text>
              </Group>
              <Badge
                color={
                  pr.status === STATUS_LABELS.MERGED
                    ? "teal"
                    : pr.status === STATUS_LABELS.OPEN
                      ? "blue"
                      : "gray"
                }
              >
                {pr.status}
              </Badge>
            </Group>
            <Text size="sm" c="dimmed" mt={4}>
              {pr.title}
            </Text>
            {pr.mergedAt && (
              <Text size="xs" c="dimmed" mt={4}>
                Merged {new Date(pr.mergedAt).toLocaleDateString()}
                {isRecent(pr.mergedAt) && (
                  <Badge size="xs" color="red" ml="xs">
                    {STATUS_LABELS.SUSPECT}
                  </Badge>
                )}
              </Text>
            )}
          </Card>
        ))}

        {relatedJiraIssues?.map((issue, idx) => (
          <Card key={idx} padding="sm" withBorder>
            <Group justify="space-between">
              <Text size="sm" fw={500}>
                {issue.key}
              </Text>
              <Badge color="blue" variant="light">
                {issue.status}
              </Badge>
            </Group>
            <Text size="sm" c="dimmed" mt={4}>
              {issue.title}
            </Text>
          </Card>
        ))}
      </Stack>
    </Card>
  );
}
