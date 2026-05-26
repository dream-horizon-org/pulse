import { useState } from "react";
import { Badge, Box, Button, Collapse, Group, Text, Tooltip } from "@mantine/core";
import { IconChevronDown, IconChevronUp } from "@tabler/icons-react";
import type {
  FunnelDropoffCause,
  FunnelDropoffCauseKind,
} from "../../../services/funnels.service";
import {
  CAUSE_KIND_COLOR,
  CAUSE_KIND_LABELS,
  SIGNIFICANT_LIFT_THRESHOLD,
} from "../DropoffPanel.constants";
import { EvidenceLinks } from "./EvidenceLinks";

interface CauseRowProps {
  cause: FunnelDropoffCause;
  funnelId: string;
  stepIndex: number;
  runTime?: string;
}

/**
 * One cause entry in the drop-off panel. Collapsed state shows cause label,
 * lift, and drop-off rate; expanding it fetches and renders {@link EvidenceLinks}
 * for up to 5 example sessions.
 */
export function CauseRow({ cause, funnelId, stepIndex, runTime }: CauseRowProps) {
  const [expanded, setExpanded] = useState(false);
  const kind = cause.causeKind as FunnelDropoffCauseKind;
  const color = CAUSE_KIND_COLOR[kind] ?? "gray";
  const label = CAUSE_KIND_LABELS[kind] ?? cause.causeKind;

  const liftStr =
    cause.lift >= 999 ? "only droppers" : `${cause.lift.toFixed(2)}×`;
  const liftIsSignificant = cause.lift >= SIGNIFICANT_LIFT_THRESHOLD;

  return (
    <Box
      style={{
        border: "1px solid var(--mantine-color-gray-3)",
        borderRadius: "var(--mantine-radius-sm)",
        padding: "var(--mantine-spacing-sm)",
        marginBottom: "var(--mantine-spacing-xs)",
      }}
    >
      <Group justify="space-between" wrap="nowrap">
        <Box style={{ minWidth: 0, flex: 1 }}>
          <Group gap="xs" wrap="nowrap">
            <Badge color={color} variant="light" size="sm">
              {label}
            </Badge>
            <Text size="sm" fw={500} truncate="end" title={cause.causeLabel}>
              {cause.causeLabel}
            </Text>
          </Group>
          <Group gap="md" mt={6}>
            <Tooltip
              label={`${cause.dropoffAffected} of ${cause.dropoffCohort} droppers experienced this signal`}
              withArrow
            >
              <Text size="xs" c="dimmed">
                Drop-off rate: <b>{cause.dropoffRate.toFixed(1)}%</b>
              </Text>
            </Tooltip>
            <Tooltip
              label={
                cause.lift >= 999
                  ? "No converter saw this signal — only droppers did"
                  : "Rate in droppers vs rate in converters"
              }
              withArrow
            >
              <Text
                size="xs"
                c={liftIsSignificant ? "red" : "dimmed"}
                fw={liftIsSignificant ? 600 : 400}
              >
                Lift: {liftStr}
              </Text>
            </Tooltip>
          </Group>
        </Box>

        <Button
          variant="subtle"
          size="xs"
          rightSection={
            expanded ? <IconChevronUp size={14} /> : <IconChevronDown size={14} />
          }
          onClick={() => setExpanded((v) => !v)}
          disabled={cause.exampleSessionIds.length === 0}
        >
          {cause.exampleSessionIds.length} example
          {cause.exampleSessionIds.length === 1 ? "" : "s"}
        </Button>
      </Group>

      <Collapse in={expanded}>
        <Box mt="sm">
          <EvidenceLinks
            funnelId={funnelId}
            stepIndex={stepIndex}
            sessionIds={cause.exampleSessionIds}
            runTime={runTime}
            enabled={expanded}
          />
        </Box>
      </Collapse>
    </Box>
  );
}
