import type { ReactNode } from "react";
import { Box, Group, SegmentedControl, Text, Tooltip } from "@mantine/core";
import { IconClick, IconDeviceMobile, IconInfoCircle } from "@tabler/icons-react";
import type { AnalysisBasis } from "../../../services/funnels.service";
import classes from "./AnalysisBasisChoice.module.css";

export type AnalysisBasisChoiceVariant = "funnel" | "journey";

const LABELS: Record<AnalysisBasisChoiceVariant, string> = {
  funnel: "Define steps as",
  journey: "Explore path by",
};

const INFO_TOOLTIPS: Record<AnalysisBasisChoiceVariant, ReactNode> = {
  funnel: (
    <Box w={240}>
      <Text size="xs" fw={600} mb={4}>
        Events
      </Text>
      <Text size="xs" mb={8}>
        Each funnel step is a custom event in your app (e.g. add to cart, purchase).
      </Text>
      <Text size="xs" fw={600} mb={4}>
        Screens
      </Text>
      <Text size="xs">
        Each step is a screen users visit (e.g. product list → product detail → checkout).
      </Text>
    </Box>
  ),
  journey: (
    <Box w={240}>
      <Text size="xs" fw={600} mb={4}>
        Events
      </Text>
      <Text size="xs" mb={8}>
        Steps before and after the anchor use custom events.
      </Text>
      <Text size="xs" fw={600} mb={4}>
        Screens
      </Text>
      <Text size="xs">
        Steps before and after the anchor follow screen-to-screen navigation.
      </Text>
    </Box>
  ),
};

const SEGMENT_DATA = [
  {
    value: "EVENT",
    label: (
      <Group gap={6} justify="center" wrap="nowrap" className={classes.segmentLabel}>
        <IconClick size={15} stroke={1.75} className={classes.segmentIcon} />
        <span>Events</span>
      </Group>
    ),
  },
  {
    value: "SCREEN",
    label: (
      <Group gap={6} justify="center" wrap="nowrap" className={classes.segmentLabel}>
        <IconDeviceMobile size={15} stroke={1.75} className={classes.segmentIcon} />
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
    <Box
      className={classes.wrapper}
      data-accent={accent}
      data-disabled={disabled ? "true" : undefined}
    >
      <Group gap="xs" mb={4}>
        <Text size="sm" fw={500}>
          {LABELS[variant]}
        </Text>
        <Tooltip
          label={INFO_TOOLTIPS[variant]}
          position="right"
          withArrow
          multiline
        >
          <IconInfoCircle
            size={14}
            color="#94a3b8"
            style={{ cursor: "help" }}
            aria-label={`About ${LABELS[variant]}`}
          />
        </Tooltip>
      </Group>
      <SegmentedControl
        value={value}
        onChange={(val) => onChange(val as AnalysisBasis)}
        data={SEGMENT_DATA}
        size={size}
        fullWidth
        color={accent}
        mb="wd"
        aria-label={LABELS[variant]}
        classNames={{
          root: classes.controlRoot,
          indicator: classes.controlIndicator,
          label: classes.controlLabel,
        }}
      />
    </Box>
  );
}
