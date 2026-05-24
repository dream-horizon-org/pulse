import { Group, SegmentedControl, Text } from "@mantine/core";
import { IconClick, IconDeviceMobile } from "@tabler/icons-react";
import type { AnalysisBasis } from "../../../services/funnels.service";
import classes from "./AnalysisBasisChoice.module.css";

export type AnalysisBasisChoiceVariant = "funnel" | "journey";

const LABELS: Record<AnalysisBasisChoiceVariant, string> = {
  funnel: "Define steps as",
  journey: "Explore path by",
};

const SEGMENT_DATA = [
  {
    value: "EVENT",
    label: (
      <Group gap={6} justify="center" wrap="nowrap" className={classes.segmentLabel}>
        <IconClick size={15} stroke={1.75} />
        <span>Events</span>
      </Group>
    ),
  },
  {
    value: "SCREEN",
    label: (
      <Group gap={6} justify="center" wrap="nowrap" className={classes.segmentLabel}>
        <IconDeviceMobile size={15} stroke={1.75} />
        <span>Screens</span>
      </Group>
    ),
  },
];

type AnalysisBasisChoiceProps = {
  value: AnalysisBasis;
  onChange: (value: AnalysisBasis) => void;
  variant: AnalysisBasisChoiceVariant;
  disabled?: boolean;
  accent?: "blue" | "teal";
  size?: "xs" | "sm" | "md" | "lg" | "xl";
};

export function AnalysisBasisChoice({
  value,
  onChange,
  variant,
  disabled = false,
  accent = "blue",
  size = "sm",
}: AnalysisBasisChoiceProps) {
  return (
    <>
      <Text size="sm" fw={500} mb={4}>
        {LABELS[variant]}
      </Text>
      <SegmentedControl
        value={value}
        onChange={(val) => onChange(val as AnalysisBasis)}
        data={SEGMENT_DATA}
        size={size}
        fullWidth
        color={accent}
        mb="md"
        disabled={disabled}
        aria-label={LABELS[variant]}
      />
    </>
  );
}
