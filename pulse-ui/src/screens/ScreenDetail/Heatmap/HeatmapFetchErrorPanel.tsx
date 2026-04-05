import { Button, Stack, Text } from "@mantine/core";
import { HEATMAP_COPY_RETRY } from "./heatmapCopy";
import {
  HEATMAP_USER_VISIBLE_ERROR_BODY,
  HEATMAP_USER_VISIBLE_ERROR_TITLE,
} from "./heatmapFetchErrors";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapFetchErrorPanelProps {
  onRetry?: () => void;
  retryLoading?: boolean;
  /** Optional compact layout for compare columns */
  compact?: boolean;
}

export function HeatmapFetchErrorPanel({
  onRetry,
  retryLoading = false,
  compact = false,
}: HeatmapFetchErrorPanelProps) {
  return (
    <div
      className={
        compact ? classes.heatmapFetchErrorCompact : classes.heatmapFetchErrorFull
      }
    >
      <Stack gap="md" align={compact ? "stretch" : "center"} ta={compact ? "left" : "center"}>
        <Text fw={700} size={compact ? "sm" : "lg"}>
          {HEATMAP_USER_VISIBLE_ERROR_TITLE}
        </Text>
        <Text size="sm" c="dimmed" maw={compact ? "100%" : 420} lh={1.6}>
          {HEATMAP_USER_VISIBLE_ERROR_BODY}
        </Text>
        {onRetry != null && (
          <Button
            variant="light"
            color="teal"
            loading={retryLoading}
            onClick={() => onRetry()}
            size={compact ? "xs" : "sm"}
          >
            {HEATMAP_COPY_RETRY}
          </Button>
        )}
      </Stack>
    </div>
  );
}
