import type { MouseEvent } from "react";
import {
  Box,
  Text,
  Group,
  Badge,
  Card,
  Stack,
  Paper,
  Center,
  ThemeIcon,
  Title,
} from "@mantine/core";
import { IconHandClick } from "@tabler/icons-react";
import { useLocation } from "react-router-dom";
import type { CriticalInteraction } from "../../../../services/sessionReplay/mockSessionDetail";
import { ROUTES } from "../../../../constants";
import {
  STATUS_LABELS,
  FORMAT_STRINGS,
  MESSAGES,
} from "../../constants/strings";
import classes from "./CriticalInteractions.module.css";

interface CriticalInteractionsProps {
  criticalInteractions: CriticalInteraction[];
  onCriticalInteractionClick?: (t0: number, t1: number) => void;
  projectId?: string;
  /** When false, omit the top-right success summary (e.g. shown in panel toolbar instead). */
  hideSummaryBadge?: boolean;
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
  hideSummaryBadge = false,
}: CriticalInteractionsProps) {
  const { pathname } = useLocation();
  const projectIdFromUrl = pathname.match(/\/projects\/([^/]+)/)?.[1];
  const projectId = projectIdProp ?? projectIdFromUrl;

  const successCount = criticalInteractions.filter(
    (i) => i.status === "success",
  ).length;

  if (criticalInteractions.length === 0) {
    return (
      <Paper withBorder p="xl" radius="md" bg="gray.0">
        <Center>
          <Stack align="center" gap="md" maw={420}>
            <ThemeIcon size={56} radius="md" variant="light" color="teal">
              <IconHandClick size={28} stroke={1.5} />
            </ThemeIcon>
            <Stack gap={6} align="center">
              <Title order={5} ta="center" fw={600}>
                No critical interactions
              </Title>
              <Text size="sm" c="dimmed" ta="center" lh={1.6}>
                {MESSAGES.NO_CRITICAL_INTERACTIONS}
              </Text>
            </Stack>
          </Stack>
        </Center>
      </Paper>
    );
  }

  return (
    <Box>
      {!hideSummaryBadge ? (
        <Group justify="flex-end" mb="xs">
          <Badge size="xs" variant="light" color="blue">
            {FORMAT_STRINGS.SUCCESSFUL_COUNT.replace(
              "{success}",
              successCount.toString(),
            ).replace("{total}", criticalInteractions.length.toString())}
          </Badge>
        </Group>
      ) : null}
      <Card padding="sm" withBorder>
        <Stack gap={0} className={classes.list}>
          {criticalInteractions.map((interaction) => {
            const handleRowClick = () => {
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

            const canSeek =
              interaction.timestamp !== undefined &&
              interaction.latency !== undefined &&
              !!onCriticalInteractionClick;

            return (
              <div
                key={interaction.interactionId}
                className={`${classes.row} ${canSeek ? classes.rowSeekable : ""}`}
                onClick={canSeek ? handleRowClick : undefined}
                role={canSeek ? "button" : undefined}
                tabIndex={canSeek ? 0 : undefined}
                aria-label={
                  canSeek
                    ? `Seek replay to ${interaction.displayName}`
                    : undefined
                }
                onKeyDown={
                  canSeek
                    ? (e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault();
                          handleRowClick();
                        }
                      }
                    : undefined
                }
              >
                {/* Grid: name (flex) | status | Apdex — columns align across rows */}
                <div className={classes.mainLine}>
                  <Text
                    component={path ? "a" : "span"}
                    href={path ?? undefined}
                    target={path ? "_blank" : undefined}
                    rel={path ? "noopener noreferrer" : undefined}
                    size="sm"
                    fw={600}
                    className={path ? classes.nameLink : classes.nameCol}
                    onClick={
                    path
                      ? (e: MouseEvent<HTMLAnchorElement>) =>
                          e.stopPropagation()
                      : undefined
                  }
                  >
                    {interaction.displayName}
                  </Text>
                  <div className={classes.statusCol}>
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
                  </div>
                  <Text size="xs" c="dimmed" className={classes.metricCol}>
                    Apdex {apdexValue}
                  </Text>
                </div>
              </div>
            );
          })}
        </Stack>
      </Card>
    </Box>
  );
}
