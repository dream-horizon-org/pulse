import { ActionIcon, Badge, Box, Button, Group, Text } from "@mantine/core";
import { modals } from "@mantine/modals";
import { IconArrowLeft, IconPlayerStopFilled, IconTrash } from "@tabler/icons-react";
import dayjs from "dayjs";
import funnelClasses from "../FunnelJourneyCreate/FunnelCreate.module.css";
import type { AnalysisBasis } from "../../services/funnels.service";

type DetailChrome = {
  name: string;
  status: string;
  createdBy?: string;
  lastRunAt?: string;
  /** Analysis grouping key — surfaced as a read-only badge when present. */
  mode?: string;
  /** Schedule type — drives whether the Stop button is shown. */
  funnelType?: string;
  journeyType?: string;
};

/** Human-readable label for a FunnelMode value. */
function modeLabel(mode: string | undefined): string | null {
  if (!mode) return null;
  if (mode === "SESSIONS") return "Unique Sessions";
  if (mode === "UNIQUE_USERS") return "Unique Users";
  return mode;
}

function basisBadgeLabel(
  kind: "FUNNEL" | "JOURNEY",
  basis: AnalysisBasis,
): string {
  if (kind === "FUNNEL") {
    return basis === "SCREEN" ? "Screen-based steps" : "Event-based steps";
  }
  return basis === "SCREEN" ? "Screen path" : "Event path";
}

export function FunnelJourneyDetailChrome({
  detail,
  kind,
  analysisBasis = "EVENT",
  onBack,
  onStop,
  isStopping = false,
  onDelete,
  isDeleting = false,
}: {
  detail: DetailChrome;
  kind: "FUNNEL" | "JOURNEY";
  analysisBasis?: AnalysisBasis;
  onBack: () => void;
  /** Provided when the Stop control should be shown (AUTO non-COMPLETED items). */
  onStop?: () => void;
  isStopping?: boolean;
  /** Provided when delete is enabled. Cascades server-side. */
  onDelete?: () => void;
  isDeleting?: boolean;
}) {
  // Show "Stop" only for AUTO funnels/journeys that haven't already been stopped.
  // Backend's stopAuto is idempotent but the button should disappear once the row
  // becomes COMPLETED so the chrome reflects the final state.
  const scheduleType =
    kind === "FUNNEL" ? detail.funnelType : detail.journeyType;
  const canStop =
    onStop != null &&
    scheduleType === "AUTO" &&
    detail.status !== "COMPLETED" &&
    detail.status !== "FAILED";

  const handleStopClick = () => {
    if (!onStop) return;
    modals.openConfirmModal({
      title: `Mark this ${kind === "FUNNEL" ? "funnel" : "journey"} as Completed?`,
      centered: true,
      children: (
        <Text size="sm">
          The {kind === "FUNNEL" ? "funnel" : "journey"} will stop auto-updating
          and be marked as Completed. Existing computed data is preserved. This
          can&apos;t be undone from here — you&apos;d need to recreate the{" "}
          {kind === "FUNNEL" ? "funnel" : "journey"} to resume auto-refresh.
        </Text>
      ),
      labels: { confirm: "Mark as Completed", cancel: "Cancel" },
      confirmProps: { color: "red" },
      onConfirm: onStop,
    });
  };

  const handleDeleteClick = () => {
    if (!onDelete) return;
    modals.openConfirmModal({
      title: `Delete this ${kind === "FUNNEL" ? "funnel" : "journey"}?`,
      centered: true,
      children: (
        <Text size="sm">
          The {kind === "FUNNEL" ? "funnel" : "journey"} &quot;
          <Text span fw={600}>{detail.name}</Text>&quot; and all its data will be
          permanently removed: tag mappings, run history, and computed results.
          This can&apos;t be undone.
        </Text>
      ),
      labels: { confirm: "Delete permanently", cancel: "Cancel" },
      confirmProps: { color: "red" },
      onConfirm: onDelete,
    });
  };

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
              <Badge
                color={analysisBasis === "SCREEN" ? "indigo" : "gray"}
                variant="light"
                size="sm"
              >
                {basisBadgeLabel(kind, analysisBasis)}
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
              {detail.lastRunAt && (
                <>
                  <Text size="xs" c="dimmed">·</Text>
                  <Text size="xs" c="dark.3" fw={500}>
                    Updated {dayjs(detail.lastRunAt).format("MMM D, YYYY HH:mm")}
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
      <Group gap="xs">
        {canStop && (
          <Button
            variant="light"
            color="red"
            size="xs"
            leftSection={<IconPlayerStopFilled size={14} />}
            onClick={handleStopClick}
            loading={isStopping}
          >
            Mark as Completed
          </Button>
        )}
        {onDelete && (
          <Button
            variant="subtle"
            color="red"
            size="xs"
            leftSection={<IconTrash size={14} />}
            onClick={handleDeleteClick}
            loading={isDeleting}
          >
            Delete
          </Button>
        )}
      </Group>
    </Box>
  );
}
