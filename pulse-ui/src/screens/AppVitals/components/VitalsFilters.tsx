import { Box, Group, SegmentedControl, Badge } from "@mantine/core";
import {
  IconBug,
  IconAlertTriangle,
  IconExclamationCircle,
} from "@tabler/icons-react";
import { ISSUE_TYPES } from "../AppVitals.constants";
import type { VitalsStats } from "../AppVitals.interface";
import classes from "./VitalsFilters.module.css";

interface VitalsFiltersProps {
  issueType: string;
  onIssueTypeChange: (value: string) => void;
  stats: VitalsStats;
}

// Format large numbers for better display (e.g., 1234 → "1.2K", 12345 → "12K")
const formatCount = (count: number): string => {
  if (count >= 1000000) {
    return `${(count / 1000000).toFixed(1).replace(/\.0$/, '')}M`;
  }
  if (count >= 1000) {
    return `${(count / 1000).toFixed(1).replace(/\.0$/, '')}K`;
  }
  return count.toString();
};

export const VitalsFilters: React.FC<VitalsFiltersProps> = ({
  issueType,
  onIssueTypeChange,
  stats,
}) => {
  return (
    <Box>
      <SegmentedControl
        value={issueType}
        onChange={onIssueTypeChange}
        size="md"
        className={classes.segmentedControl}
        fullWidth={false}
        data={[
          {
            label: (
              <Group gap={8} wrap="nowrap">
                <IconBug size={16} />
                <span>Crashes</span>
                <Badge
                  size="sm"
                  variant="filled"
                  color="red"
                  className={classes.countBadge}
                >
                  {formatCount(stats.crashes)}
                </Badge>
              </Group>
            ),
            value: ISSUE_TYPES.CRASHES,
          },
          {
            label: (
              <Group gap={8} wrap="nowrap">
                <IconAlertTriangle size={16} />
                <span>ANRs</span>
                <Badge
                  size="sm"
                  variant="filled"
                  color="orange"
                  className={classes.countBadge}
                >
                  {formatCount(stats.anrs)}
                </Badge>
              </Group>
            ),
            value: ISSUE_TYPES.ANRS,
          },
          {
            label: (
              <Group gap={8} wrap="nowrap">
                <IconExclamationCircle size={16} />
                <span>Non-Fatal</span>
                <Badge
                  size="sm"
                  variant="filled"
                  color="blue"
                  className={classes.countBadge}
                >
                  {formatCount(stats.nonFatals)}
                </Badge>
              </Group>
            ),
            value: ISSUE_TYPES.NON_FATALS,
          },
        ]}
      />
    </Box>
  );
};
