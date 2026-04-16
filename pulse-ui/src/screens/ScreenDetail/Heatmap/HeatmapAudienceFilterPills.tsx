import { ActionIcon, Badge, Group } from "@mantine/core";
import { IconX } from "@tabler/icons-react";
import type { HeatmapLocalFilters } from "./heatmapLocalFilters";
import {
  getHeatmapAudiencePillEntries,
  type HeatmapAudiencePillKey,
} from "./heatmapFilterPanelUtils";

export interface HeatmapAudienceFilterPillsProps {
  value: HeatmapLocalFilters;
  onChange: (next: HeatmapLocalFilters) => void;
}

export function HeatmapAudienceFilterPills({
  value,
  onChange,
}: HeatmapAudienceFilterPillsProps) {
  const entries = getHeatmapAudiencePillEntries(value);
  if (entries.length === 0) {
    return null;
  }

  const clear = (key: HeatmapAudiencePillKey) => {
    onChange({ ...value, [key]: "" });
  };

  return (
    <Group gap={6} wrap="wrap">
      {entries.map((e) => (
        <Badge
          key={e.key}
          variant="light"
          color="teal"
          size="sm"
          pr={3}
          rightSection={
            <ActionIcon
              size="xs"
              color="teal"
              variant="transparent"
              aria-label={`Clear ${e.key}`}
              onClick={(ev) => {
                ev.stopPropagation();
                clear(e.key);
              }}
            >
              <IconX size={12} stroke={2} />
            </ActionIcon>
          }
        >
          {e.label}
        </Badge>
      ))}
    </Group>
  );
}
