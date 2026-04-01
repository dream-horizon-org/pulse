import { Badge, Button, Divider, Group, Popover, Stack, Text } from "@mantine/core";
import { IconFilter } from "@tabler/icons-react";
import type { HeatmapAudienceFilterPopoverProps } from "./heatmapFilterPanel.types";
import filterClasses from "../../CriticalInteractionDetails/components/InteractionDetailsFilters/InteractionDetailsFilters.module.css";

export function HeatmapAudienceFilterPopover({
  opened,
  onOpenChange,
  audienceActiveCount,
  dropdownWidth = 420,
  onResetToPage,
  audienceHint,
  children,
}: HeatmapAudienceFilterPopoverProps) {
  return (
    <Popover
      opened={opened}
      onChange={onOpenChange}
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
          onClick={() => onOpenChange(!opened)}
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
          {onResetToPage ? (
            <>
              <Divider />
              <Button variant="light" color="gray" size="xs" onClick={onResetToPage}>
                Match this page
              </Button>
            </>
          ) : null}
          <Text size="xs" c="dimmed" lh={1.45}>
            {audienceHint}
          </Text>
        </Stack>
      </Popover.Dropdown>
    </Popover>
  );
}
