import {
  Card,
  Text,
  Group,
  Stack,
  Button,
  Code,
  CopyButton,
  ActionIcon,
  Tooltip,
} from "@mantine/core";
import { IconCode, IconBrandGithub, IconCheck, IconCopy } from "@tabler/icons-react";
import type { TechnicalContext } from "../../../../services/sessionReplay/mockSessionDetail";
import {
  HEADERS,
  LABELS,
  BUTTON_LABELS,
  MESSAGES_EXTENDED as MESSAGES,
} from "../../constants/strings";

interface CodeReferencesProps {
  codeReferences: NonNullable<TechnicalContext["codeReferences"]>;
}

export function CodeReferences({ codeReferences }: CodeReferencesProps) {
  return (
    <Card padding="md" withBorder>
      <Group justify="space-between" mb="md">
        <Group gap="xs">
          <IconCode size={18} />
          <Text size="xs" tt="uppercase" fw={600} c="dimmed">
            {HEADERS.CODE_REFERENCES}
          </Text>
        </Group>
      </Group>

      <Stack gap="md">
        {codeReferences.map((ref, idx) => (
          <Card key={idx} padding="sm" withBorder>
            <Group justify="space-between" mb="xs">
              <Text size="sm" fw={600} ff="monospace">
                {ref.file}:{ref.line}
              </Text>
              {ref.githubUrl && (
                <Button
                  size="xs"
                  variant="light"
                  leftSection={<IconBrandGithub size={14} />}
                  component="a"
                  href={ref.githubUrl}
                  target="_blank"
                >
                  {BUTTON_LABELS.VIEW_IN_GITHUB}
                </Button>
              )}
            </Group>

            <Text size="sm" c="dimmed" mb={4}>
              {LABELS.FUNCTION}: <Code>{ref.function}</Code>
            </Text>

            {ref.stackFrame && (
              <Group gap="xs">
                <Code style={{ flex: 1, fontSize: "11px" }}>
                  {ref.stackFrame}
                </Code>
                <CopyButton value={ref.stackFrame}>
                  {({ copied, copy }) => (
                    <Tooltip
                      label={copied ? MESSAGES.COPIED : MESSAGES.COPY}
                    >
                      <ActionIcon
                        color={copied ? "teal" : "gray"}
                        onClick={copy}
                        size="sm"
                      >
                        {copied ? (
                          <IconCheck size={14} />
                        ) : (
                          <IconCopy size={14} />
                        )}
                      </ActionIcon>
                    </Tooltip>
                  )}
                </CopyButton>
              </Group>
            )}
          </Card>
        ))}
      </Stack>
    </Card>
  );
}
