import { ActionIcon, Badge, Box, Group, Text } from "@mantine/core";
import { IconArrowLeft } from "@tabler/icons-react";
import dayjs from "dayjs";
import funnelClasses from "../FunnelJourneyCreate/FunnelCreate.module.css";

type DetailChrome = {
  name: string;
  status: string;
  createdBy?: string;
  updatedAt?: string;
  /** Analysis grouping key — surfaced as a read-only badge when present. */
  mode?: string;
};

/** Human-readable label for a FunnelMode value. */
function modeLabel(mode: string | undefined): string | null {
  if (!mode) return null;
  if (mode === "SESSIONS") return "Unique Sessions";
  if (mode === "UNIQUE_USERS") return "Unique Users";
  return mode;
}

export function FunnelJourneyDetailChrome({
  detail,
  kind,
  onBack,
}: {
  detail: DetailChrome;
  kind: "FUNNEL" | "JOURNEY";
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
                    : detail.status === "IN_PROGRESS"
                      ? "blue"
                      : detail.status === "WARN"
                        ? "orange"
                        : detail.status === "COMPLETED"
                          ? "violet"
                          : detail.status === "FAILED"
                            ? "red"
                            : "gray"
                }
                variant="light"
                size="sm"
              >
                {detail.status === "ACTIVE"
                  ? "Active"
                  : detail.status === "IN_PROGRESS"
                    ? "In Progress"
                    : detail.status === "WARN"
                      ? "Warning"
                      : detail.status === "COMPLETED"
                        ? "Completed"
                        : detail.status === "FAILED"
                          ? "Failed"
                          : "Pending"}
              </Badge>
              <Text size="xs" c="dimmed">
                {kind === "FUNNEL" ? "Funnel" : "Journey"}
              </Text>
              {detail.createdBy && (
                <>
                  <Text size="xs" c="dimmed">·</Text>
                  <Text size="xs" c="dark.3" fw={500}>
                    Created by {detail.createdBy}
                  </Text>
                </>
              )}
              {detail.updatedAt && (
                <>
                  <Text size="xs" c="dimmed">·</Text>
                  <Text size="xs" c="dark.3" fw={500}>
                    Updated {dayjs(detail.updatedAt).format("MMM D, YYYY HH:mm")}
                  </Text>
                </>
              )}
              {modeLabel(detail.mode) && (
                <>
                  <Text size="xs" c="dimmed">·</Text>
                  <Text size="xs" c="dark.3" fw={500}>
                    Measured by {modeLabel(detail.mode)}
                  </Text>
                </>
              )}
            </Group>
          </Box>
        </Group>
      </Box>
    </Box>
  );
}
