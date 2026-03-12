import { Card, Text, Group, Stack, Code, Badge, Divider } from "@mantine/core";
import { IconSettings } from "@tabler/icons-react";
import type { TechnicalContext } from "../../../../services/sessionReplay/mockSessionDetail";
import { HEADERS, LABELS, FORMAT_STRINGS } from "../../constants/strings";

interface EnvironmentInfoProps {
  environmentInfo: TechnicalContext["environmentInfo"];
}

export function EnvironmentInfoComponent({
  environmentInfo,
}: EnvironmentInfoProps) {
  return (
    <Card padding="md" withBorder>
      <Group mb="md">
        <IconSettings size={18} />
        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          {HEADERS.ENVIRONMENT_INFO}
        </Text>
      </Group>

      <Stack gap="sm">
        <Group justify="space-between">
          <Text size="sm" c="dimmed">
            {LABELS.APP_VERSION}
          </Text>
          <Code>{environmentInfo.appVersion}</Code>
        </Group>

        {environmentInfo.buildNumber && (
          <Group justify="space-between">
            <Text size="sm" c="dimmed">
              {LABELS.BUILD_NUMBER}
            </Text>
            <Code>{environmentInfo.buildNumber}</Code>
          </Group>
        )}

        {environmentInfo.deployedAt && (
          <Group justify="space-between">
            <Text size="sm" c="dimmed">
              {LABELS.DEPLOYED_AT}
            </Text>
            <Text size="sm">
              {new Date(environmentInfo.deployedAt).toLocaleString()}
            </Text>
          </Group>
        )}

        <Divider />

        <Text size="xs" tt="uppercase" fw={600} c="dimmed">
          {LABELS.FEATURE_FLAGS}
        </Text>
        <Stack gap="xs">
          {Object.entries(environmentInfo.featureFlags).map(
            ([flag, enabled]) => (
              <Group key={flag} justify="space-between">
                <Code style={{ fontSize: "11px" }}>{flag}</Code>
                <Badge size="sm" color={enabled ? "teal" : "gray"}>
                  {enabled
                    ? FORMAT_STRINGS.FEATURE_FLAG_ON
                    : FORMAT_STRINGS.FEATURE_FLAG_OFF}
                </Badge>
              </Group>
            ),
          )}
        </Stack>
      </Stack>
    </Card>
  );
}
