import { Box, Text, Group, Badge, Card, Stack } from "@mantine/core";
import { IconCheck, IconX, IconExternalLink } from "@tabler/icons-react";
import { useLocation } from "react-router-dom";
import type { CriticalInteraction } from "../../../../services/sessionReplay/mockSessionDetail";
import { ROUTES } from "../../../../constants";
import {
  STATUS_LABELS,
  FORMAT_STRINGS,
  MESSAGES,
} from "../../constants/strings";

interface CriticalInteractionsProps {
  criticalInteractions: CriticalInteraction[];
  onCriticalInteractionClick?: (t0: number, t1: number) => void;
  projectId?: string;
}

function getStatusIcon(status: "success" | "failed" | "not_attempted") {
  if (status === "success")
    return <IconCheck size={14} color="var(--mantine-color-teal-6)" />;
  if (status === "failed")
    return <IconX size={14} color="var(--mantine-color-red-6)" />;
  return null;
}

function getStatusColor(status: "success" | "failed" | "not_attempted") {
  if (status === "success") return "teal";
  if (status === "failed") return "red";
  return "gray";
}

export function CriticalInteractions({
  criticalInteractions,
  onCriticalInteractionClick,
  projectId: projectIdProp,
}: CriticalInteractionsProps) {
  const { pathname } = useLocation();
  const projectIdFromUrl = pathname.match(/\/projects\/([^/]+)/)?.[1];
  const projectId = projectIdProp ?? projectIdFromUrl;

  const successCount = criticalInteractions.filter(
    (i) => i.status === "success",
  ).length;

  if (criticalInteractions.length === 0) {
    return (
      <Box py="xl" px="md" mih={200}>
        <Group align="flex-start" gap="sm" wrap="nowrap">
          <Text size="sm" c="dimmed" ta="left" lh={1.5} maw="100%">
            {MESSAGES.NO_CRITICAL_INTERACTIONS}
          </Text>
        </Group>
      </Box>
    );
  }

  return (
    <Box>
      <Group justify="flex-end" mb="xs">
        <Badge size="xs" variant="light" color="blue">
          {FORMAT_STRINGS.SUCCESSFUL_COUNT.replace(
            "{success}",
            successCount.toString(),
          ).replace("{total}", criticalInteractions.length.toString())}
        </Badge>
      </Group>
      <Card padding="sm" withBorder>
        <Stack gap="sm">
          {criticalInteractions.map((interaction) => {
            const handleClick = () => {
              if (
                interaction.timestamp !== undefined &&
                interaction.latency !== undefined &&
                onCriticalInteractionClick
              ) {
                const t0 = interaction.timestamp;
                const t1 = interaction.timestamp + interaction.latency;
                onCriticalInteractionClick(t0, t1);
              }
            };

            const apdexValue =
              interaction.apdexScore !== undefined
                ? interaction.apdexScore.toFixed(2)
                : "—";

            const nameForUrl =
              interaction.interactionName || interaction.displayName || "";
            const path =
              projectId && nameForUrl
                ? `${ROUTES.PROJECT_INTERACTION_DETAILS.basePath.replace(":projectId", projectId)}/${nameForUrl.replace(/\s+/g, "")}`
                : null;

            const openInteractionDetail = (e: React.MouseEvent) => {
              e.stopPropagation();
              e.preventDefault();
              if (!path) return;
              window.open(path, "_blank");
            };

            return (
              <Group
                key={interaction.interactionId}
                justify="space-between"
                wrap="nowrap"
                style={{
                  cursor:
                    interaction.timestamp !== undefined &&
                    interaction.latency !== undefined
                      ? "pointer"
                      : "default",
                }}
                onClick={handleClick}
              >
                <Group gap="xs" wrap="nowrap">
                  {getStatusIcon(interaction.status)}
                  {path ? (
                    <Box
                      component="button"
                      type="button"
                      onClick={openInteractionDetail}
                      style={{
                        flex: 1,
                        display: "inline-flex",
                        alignItems: "center",
                        gap: 6,
                        padding: 0,
                        border: "none",
                        background: "none",
                        cursor: "pointer",
                        color: "var(--mantine-color-blue-6)",
                        textDecoration: "none",
                        fontWeight: 500,
                        fontSize: "var(--mantine-font-size-sm)",
                        textAlign: "left",
                        font: "inherit",
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.textDecoration = "underline";
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.textDecoration = "none";
                      }}
                    >
                      {interaction.displayName}
                      <IconExternalLink size={14} style={{ flexShrink: 0 }} />
                    </Box>
                  ) : (
                    <Text size="sm" fw={500} style={{ flex: 1 }}>
                      {interaction.displayName}
                    </Text>
                  )}
                </Group>
                <Group gap="xs" wrap="nowrap">
                  <Text size="xs" c="dimmed">
                    Apdex {apdexValue}
                  </Text>
                  <Badge
                    size="sm"
                    color={getStatusColor(interaction.status)}
                    variant="light"
                  >
                    {interaction.status === "success"
                      ? STATUS_LABELS.SUCCESS
                      : interaction.status === "failed"
                        ? STATUS_LABELS.FAILED
                        : STATUS_LABELS.NOT_ATTEMPTED}
                  </Badge>
                </Group>
              </Group>
            );
          })}
        </Stack>
      </Card>
    </Box>
  );
}
