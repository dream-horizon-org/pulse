import { Card, Text, Stack, Group, Progress } from "@mantine/core";
import type { BusinessContext } from "../../../../services/sessionReplay/mockSessionDetail";
import { formatDuration } from "../../utils/sessionUtils";
import { HEADERS } from "../../constants/strings";

interface FeatureEngagementProps {
  businessContext: BusinessContext;
  sessionDuration: number;
}

export function FeatureEngagement({
  businessContext,
  sessionDuration,
}: FeatureEngagementProps) {
  if (!businessContext.featuresUsed || businessContext.featuresUsed.length === 0) {
    return null;
  }

  return (
    <Card padding="md" withBorder>
      <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="md">
        {HEADERS.FEATURE_ENGAGEMENT}
      </Text>

      <Stack gap="sm">
        {businessContext.featuresUsed.map((feature, idx) => {
          const engagementTime =
            businessContext.featureEngagement?.[feature] || 0;
          const percentage = (engagementTime / sessionDuration) * 100;

          return (
            <div key={idx}>
              <Group justify="space-between" mb={4}>
                <Text size="sm">{feature}</Text>
                <Text size="xs" c="dimmed">
                  {formatDuration(engagementTime)}
                </Text>
              </Group>
              <Progress value={percentage} color="teal" size="sm" />
            </div>
          );
        })}
      </Stack>
    </Card>
  );
}
