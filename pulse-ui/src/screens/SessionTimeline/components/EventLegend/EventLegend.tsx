import { Group, Button } from "@mantine/core";
import { useMantineTheme } from "@mantine/core";
import {
  OtelEventType,
  OTEL_EVENT_TYPES,
  getOtelEventIcon,
  getOtelEventColor,
  getOtelEventLabel,
} from "../../constants/otelConstants";
import classes from "./EventLegend.module.css";

interface EventLegendProps {
  activeFilters: Set<OtelEventType>;
  onFilterToggle: (type: OtelEventType) => void;
}

export function EventLegend({
  activeFilters,
  onFilterToggle,
}: EventLegendProps) {
  const theme = useMantineTheme();

  return (
    <Group gap="xs" className={classes.legend}>
      {OTEL_EVENT_TYPES.map((type) => {
        const EventIcon = getOtelEventIcon(type);
        const eventColor = getOtelEventColor(type, theme);
        const isActive = activeFilters.has(type);
        return (
          <Button
            key={type}
            variant={isActive ? "filled" : "outline"}
            size="xs"
            onClick={() => onFilterToggle(type)}
            color={eventColor}
            leftSection={<EventIcon size={14} />}
            className={classes.filterButton}
          >
            {getOtelEventLabel(type)}
          </Button>
        );
      })}
    </Group>
  );
}
