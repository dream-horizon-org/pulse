import { Group, Stack, Text, Title } from "@mantine/core";
import type { ReactNode } from "react";
import { TabPanelScrollArea } from "./TabPanelScrollArea";

export interface SessionDetailTabPanelProps {
  title: string;
  description?: string;
  toolbar?: ReactNode;
  children: ReactNode;
}

/**
 * Shared shell for session detail tabs: title row, optional toolbar, optional description, scroll body.
 */
export function SessionDetailTabPanel({
  title,
  description,
  toolbar,
  children,
}: SessionDetailTabPanelProps) {
  return (
    <Stack gap="sm" style={{ flex: 1, minHeight: 0 }}>
      <Group justify="space-between" align="flex-start" wrap="wrap" gap="sm">
        <Stack gap={4} style={{ flex: "1 1 220px", minWidth: 0 }}>
          <Title order={5} fz="md" fw={600}>
            {title}
          </Title>
          {description ? (
            <Text size="sm" c="dimmed" lh={1.55} maw={720}>
              {description}
            </Text>
          ) : null}
        </Stack>
        {toolbar ? (
          <Group justify="flex-end" wrap="nowrap" flex="0 0 auto">
            {toolbar}
          </Group>
        ) : null}
      </Group>
      <TabPanelScrollArea>{children}</TabPanelScrollArea>
    </Stack>
  );
}
