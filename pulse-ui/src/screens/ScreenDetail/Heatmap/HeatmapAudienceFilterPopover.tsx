import { Badge, Button, Divider, Group, Popover, Stack, Text } from "@mantine/core";
import { IconFilter } from "@tabler/icons-react";
import { useState } from "react";
import type { HeatmapAudienceFilterPopoverProps } from "./heatmap.ui.types";
import filterClasses from "../../CriticalInteractionDetails/components/InteractionDetailsFilters/InteractionDetailsFilters.module.css";

export function HeatmapAudienceFilterPopover({
  opened,
  onOpenChange,
  audienceActiveCount,
  dropdownWidth = 420,
  audienceHint,
  children,
  onApply,
  onReset,
}: HeatmapAudienceFilterPopoverProps) {
  const [internalOpened, setInternalOpened] = useState(opened);

  const handleOpenChange = (newOpened: boolean) => {
    setInternalOpened(newOpened);
    onOpenChange(newOpened);
  };

  const handleApply = () => {
    onApply?.();
    handleOpenChange(false);
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
            <IconFilter size={14} stroke={2.5} />
            <span>Filters</span>
            {audienceActiveCount > 0 ? (
              <Badge size="xs" circle className={filterClasses.filterBadge}>
                {audienceActiveCount}
              </Badge>
            ) : null}
          </Group>
        </Button>
      </Popover.Target>
      <Popover.Dropdown className={filterClasses.filterDropdown}>
        <Stack gap="md">
          {children}
          <Divider />
          <Group justify="flex-end" gap="sm">
            <Button
              variant="outline"
              size="xs"
              onClick={() => {
                onReset?.();
                // Auto-apply the reset
                setTimeout(() => {
                  onApply?.();
                }, 0);
              }}
            >
              Reset
            </Button>
            <Button variant="filled" size="xs" onClick={handleApply}>
              Apply
            </Button>
          </Group>
          <Text size="xs" c="dimmed" lh={1.45}>
            {audienceHint}
          </Text>
        </Stack>
      </Popover.Dropdown>
    </Popover>
  );
}
