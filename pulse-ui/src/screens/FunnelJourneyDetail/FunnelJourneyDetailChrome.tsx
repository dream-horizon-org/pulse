import { ActionIcon, Badge, Box, Group, Text } from "@mantine/core";
import { IconArrowLeft } from "@tabler/icons-react";
import funnelClasses from "../FunnelJourneyCreate/FunnelCreate.module.css";

type DetailChrome = {
  name: string;
  status: string;
  kind: "FUNNEL" | "JOURNEY";
};

export function FunnelJourneyDetailChrome({
  detail,
  onBack,
}: {
  detail: DetailChrome;
  onBack: () => void;
}) {
  return (
    <Box className={funnelClasses.topBar}>
      <Box className={funnelClasses.topBarLeft}>
        <Group gap="sm" align="center">
          <ActionIcon variant="subtle" color="gray" onClick={onBack} size="lg">
            <IconArrowLeft size={20} />
          </ActionIcon>
          <Box>
            <Text className={funnelClasses.moduleTitle}>{detail.name}</Text>
            <Group gap="xs" mt={4}>
              <Badge
                color={
                  detail.status === "ACTIVE"
                    ? "teal"
                    : detail.status === "CREATING"
                      ? "blue"
                      : detail.status === "UPDATING"
                        ? "orange"
                        : detail.status === "COMPLETED"
                          ? "violet"
                          : "gray"
                }
                variant="light"
                size="sm"
              >
                {detail.status === "ACTIVE"
                  ? "Active"
                  : detail.status === "CREATING"
                    ? "Creating"
                    : detail.status === "UPDATING"
                      ? "Updating"
                      : detail.status === "COMPLETED"
                        ? "Completed"
                        : "Stopped"}
              </Badge>
              <Text size="xs" c="dimmed">
                {detail.kind === "FUNNEL" ? "Funnel" : "Journey"}
              </Text>
            </Group>
          </Box>
        </Group>
      </Box>
    </Box>
  );
}
