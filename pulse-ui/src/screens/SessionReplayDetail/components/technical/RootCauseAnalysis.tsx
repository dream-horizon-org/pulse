import { Card, Text, Group, Badge, Alert, Timeline, Code } from "@mantine/core";
import { IconBug, IconArrowRight } from "@tabler/icons-react";
import type { TechnicalContext } from "../../../../services/sessionReplay/mockSessionDetail";
import { formatTimestamp } from "../utils/technicalUtils";
import { HEADERS, LABELS, MESSAGES } from "../../constants/strings";

interface RootCauseAnalysisProps {
  rootCause: NonNullable<TechnicalContext["rootCause"]>;
  technicalCause?: string;
}

export function RootCauseAnalysis({
  rootCause,
  technicalCause,
}: RootCauseAnalysisProps) {
  return (
    <Card padding="md" withBorder>
      <Group justify="space-between" mb="md">
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          {HEADERS.ROOT_CAUSE_ANALYSIS}
        </Text>
        <Badge color="orange" leftSection={<IconBug size={14} />}>
          {rootCause.type.replace("_", " ")}
        </Badge>
      </Group>

      <Alert
        color="orange"
        title={`${rootCause.component} - ${rootCause.type}`}
        mb="md"
      >
        <Text size="sm">{technicalCause || MESSAGES.ERROR_DETECTED}</Text>
      </Alert>

      {/* Error Chain */}
      {rootCause.errorChain && rootCause.errorChain.length > 0 && (
        <>
          <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="sm">
            {HEADERS.ERROR_PROPAGATION_CHAIN}
          </Text>
          <Timeline bulletSize={20} lineWidth={2}>
            {rootCause.errorChain.map((link, idx) => (
              <Timeline.Item
                key={idx}
                bullet={<IconArrowRight size={12} />}
                color="red"
              >
                <Text size="sm" fw={600}>
                  {link.component}
                </Text>
                <Code block mt={4}>
                  {link.error}
                </Code>
                <Text size="xs" c="dimmed" mt={2}>
                  {formatTimestamp(link.timestamp)}
                </Text>
              </Timeline.Item>
            ))}
          </Timeline>
        </>
      )}
    </Card>
  );
}
