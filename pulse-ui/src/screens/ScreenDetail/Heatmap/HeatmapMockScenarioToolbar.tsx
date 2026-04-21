import { Group, Select, Text } from "@mantine/core";
import {
  HEATMAP_MOCK_PROFILE_LABELS,
  HEATMAP_MOCK_PROFILES_COMPARE_B,
  HEATMAP_MOCK_PROFILES_PRIMARY,
  type HeatmapMockProfile,
} from "./heatmapMockDev";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapMockScenarioToolbarProps {
  compareMode: boolean;
  profileMain: HeatmapMockProfile;
  onProfileMainChange: (p: HeatmapMockProfile) => void;
  profileCompareB: HeatmapMockProfile;
  onProfileCompareBChange: (p: HeatmapMockProfile) => void;
}

const primaryData = HEATMAP_MOCK_PROFILES_PRIMARY.map((p) => ({
  value: p,
  label: HEATMAP_MOCK_PROFILE_LABELS[p],
}));

const compareBData = HEATMAP_MOCK_PROFILES_COMPARE_B.map((p) => ({
  value: p,
  label: HEATMAP_MOCK_PROFILE_LABELS[p],
}));

/**
 * Visible only when `REACT_APP_USE_MOCK_SERVER=true`. Drives magic `screenName`
 * values so QA can exercise empty, error, no-screenshot, sparse, and compare‑B layouts.
 */
export function HeatmapMockScenarioToolbar({
  compareMode,
  profileMain,
  onProfileMainChange,
  profileCompareB,
  onProfileCompareBChange,
}: HeatmapMockScenarioToolbarProps) {
  return (
    <div className={classes.mockScenarioBar}>
      <Text size="xs" fw={600} c="dimmed" tt="uppercase" style={{ letterSpacing: "0.04em" }}>
        Mock scenarios
      </Text>
      <Group align="flex-end" wrap="wrap" gap="md">
        <Select
          label={compareMode ? "Screen A data" : "Heatmap data"}
          size="xs"
          data={primaryData}
          value={profileMain}
          onChange={(v) => {
            if (v && HEATMAP_MOCK_PROFILES_PRIMARY.includes(v as HeatmapMockProfile)) {
              onProfileMainChange(v as HeatmapMockProfile);
            }
          }}
          comboboxProps={{ withinPortal: true }}
          classNames={{ input: classes.mockScenarioSelectInput }}
        />
        {compareMode && (
          <Select
            label="Screen B data"
            description={
              profileCompareB === "alternate"
                ? "Uses alternate dense fixture vs. A"
                : undefined
            }
            size="xs"
            data={compareBData}
            value={profileCompareB}
            onChange={(v) => {
              if (v && HEATMAP_MOCK_PROFILES_COMPARE_B.includes(v as HeatmapMockProfile)) {
                onProfileCompareBChange(v as HeatmapMockProfile);
              }
            }}
            comboboxProps={{ withinPortal: true }}
            classNames={{ input: classes.mockScenarioSelectInput }}
          />
        )}
      </Group>
    </div>
  );
}
