import { Box, Text, Group, Badge, Card, Stack, Title } from "@mantine/core";
import { useLocation } from "react-router-dom";
import type { CriticalInteraction } from "../../../../services/sessionReplay/mockSessionDetail";
import { ROUTES } from "../../../../constants";
import {
  STATUS_LABELS,
  FORMAT_STRINGS,
  MESSAGES,
  HEADERS,
} from "../../constants/strings";
import detailClasses from "../../SessionReplayDetail.module.css";
import classes from "./CriticalInteractions.module.css";

interface CriticalInteractionsProps {
  criticalInteractions: CriticalInteraction[];
  onCriticalInteractionClick?: (t0: number, t1: number) => void;
  projectId?: string;
}

function toAbsoluteAppUrl(path: string): string {
  const rawBase = process.env.PUBLIC_URL ?? "";
  const base = rawBase === "/" ? "" : rawBase.replace(/\/$/, "");
  const normalized = path.startsWith("/") ? path : `/${path}`;
  return `${window.location.origin}${base}${normalized}`;
}

function openInteractionDetailInNewTab(path: string) {
  window.open(toAbsoluteAppUrl(path), "_blank", "noopener,noreferrer");
}

function getStatusColor(status: "success" | "failed" | "not_attempted") {
  if (status === "success") return "teal";
  if (status === "failed") return "red";
  return "gray";
}

function CriticalInteractionsHeader({
  successCount,
  total,
}: {
  successCount: number;
  total: number;
}) {
  return (
    <Group
      justify={total > 0 ? "space-between" : "flex-start"}
      align="flex-start"
      wrap="nowrap"
      gap="md"
      mb="md"
    >
      <Stack gap={0} maw={total > 0 ? "calc(100% - 120px)" : "100%"}>
        <Title
          order={4}
          size="h5"
          className={detailClasses.sessionReplaySectionTitle}
        >
          {HEADERS.CRITICAL_INTERACTIONS}
        </Title>
        <Text size="sm" c="dimmed" mt={4}>
          {MESSAGES.CRITICAL_INTERACTIONS_DESCRIPTION}
        </Text>
      </Stack>
      {total > 0 && (
        <Badge
          size="sm"
          variant="light"
          color="blue"
          style={{ flexShrink: 0 }}
          styles={{
            label: {
              fontWeight: 700,
              fontSize: 11,
              letterSpacing: "0.04em",
            },
          }}
        >
          {FORMAT_STRINGS.SUCCESSFUL_COUNT_CAPS.replace(
            "{success}",
            successCount.toString(),
          ).replace("{total}", total.toString())}
        </Badge>
      )}
    </Group>
  );
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
      <Box className={classes.section}>
        <CriticalInteractionsHeader successCount={0} total={0} />
        <Card padding="lg" withBorder radius="md">
          <Text size="sm" c="dimmed">
            {MESSAGES.NO_CRITICAL_INTERACTIONS}
          </Text>
        </Card>
      </Box>
    );
  }

  return (
    <Box className={classes.section}>
      <CriticalInteractionsHeader
        successCount={successCount}
        total={criticalInteractions.length}
      />

      <Card padding={0} withBorder radius="md" style={{ overflow: "hidden" }}>
        <Stack gap={0}>
          {criticalInteractions.map((interaction) => {
            const handleSeek = () => {
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

            const onInteractionNameClick = (e: {
              stopPropagation: () => void;
            }) => {
              if (!path) return;
              e.stopPropagation();
              openInteractionDetailInNewTab(path);
            };

            return (
              <Group
                key={interaction.interactionId}
                justify="space-between"
                align="center"
                wrap="nowrap"
                gap="md"
                className={classes.interactionRow}
                onClick={handleSeek}
                style={{
                  cursor: canSeek ? "pointer" : "default",
                }}
              >
                <Text
                  component="span"
                  lineClamp={2}
                  className={`${classes.interactionName} ${classes.interactionNameText} ${path ? classes.interactionNameClickable : ""}`}
                  onClick={path ? onInteractionNameClick : undefined}
                  role={path ? "link" : undefined}
                  tabIndex={path ? 0 : undefined}
                  onKeyDown={
                    path
                      ? (e) => {
                          if (e.key === "Enter" || e.key === " ") {
                            e.preventDefault();
                            e.stopPropagation();
                            openInteractionDetailInNewTab(path);
                          }
                        }
                      : undefined
                  }
                >
                  {interaction.displayName}
                </Text>

                <Group gap="md" wrap="nowrap" style={{ flexShrink: 0 }}>
                  <Badge
                    size="sm"
                    color={getStatusColor(interaction.status)}
                    variant="light"
                    styles={{
                      label: {
                        fontWeight: 700,
                        fontSize: 11,
                        letterSpacing: "0.03em",
                      },
                    }}
                  >
                    {interaction.status === "success"
                      ? STATUS_LABELS.SUCCESS
                      : interaction.status === "failed"
                        ? STATUS_LABELS.FAILED
                        : STATUS_LABELS.NOT_ATTEMPTED}
                  </Badge>
                  <Text size="xs" c="dimmed" style={{ whiteSpace: "nowrap" }}>
                    Apdex {apdexValue}
                  </Text>
                </Group>
              </Group>
            );
          })}
        </Stack>
      </Card>
    </Box>
  );
}
