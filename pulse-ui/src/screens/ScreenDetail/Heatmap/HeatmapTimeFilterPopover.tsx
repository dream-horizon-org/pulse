import { Button, Group, Popover } from "@mantine/core";
import { IconClock } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import type { HeatmapTimeFilterPopoverProps } from "./heatmap.ui.types";
import filterClasses from "../../CriticalInteractionDetails/components/InteractionDetailsFilters/InteractionDetailsFilters.module.css";

export function HeatmapTimeFilterPopover({
  opened,
  onOpenChange,
  timeButtonLabel,
  dropdownWidth = 420,
  children,
}: HeatmapTimeFilterPopoverProps) {
  const [internalOpened, setInternalOpened] = useState(opened);

  useEffect(() => {
    setInternalOpened(opened);
  }, [opened]);

  const handleOpenChange = (newOpened: boolean) => {
    setInternalOpened(newOpened);
    onOpenChange(newOpened);
  };

  return (
    <Popover
      opened={internalOpened}
      onChange={handleOpenChange}
      width={dropdownWidth}
      position="bottom-start"
      withArrow
      shadow="md"
      closeOnEscape
      closeOnClickOutside
    >
      <Popover.Target>
        <Button
          variant="transparent"
          size="compact-sm"
          className={filterClasses.filterButton}
          onClick={() => handleOpenChange(!internalOpened)}
        >
          <Group gap={6} wrap="nowrap">
            <IconClock size={14} stroke={2.5} />
            <span>{timeButtonLabel}</span>
          </Group>
        </Button>
      </Popover.Target>
      <Popover.Dropdown className={filterClasses.filterDropdown}>
        {children}
      </Popover.Dropdown>
    </Popover>
  );
}
