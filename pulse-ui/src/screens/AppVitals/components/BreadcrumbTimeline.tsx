import { useState } from "react";
import { Box, Text, Collapse } from "@mantine/core";
import {
  IconTag,
  IconChevronDown,
  IconChevronRight,
} from "@tabler/icons-react";
import type { BreadcrumbItem } from "../pages/hooks/useOccurrenceBreadcrumbs";
import classes from "./BreadcrumbTimeline.module.css";

interface BreadcrumbTimelineProps {
  breadcrumbs: BreadcrumbItem[];
  isLoading?: boolean;
  isError?: boolean;
  errorMessage?: string;
}

function formatRelativeTime(ms: number): string {
  const absMs = Math.abs(ms);
  if (absMs < 1000) {
    return `${ms < 0 ? "-" : "+"}${absMs}ms`;
  }
  const seconds = absMs / 1000;
  if (seconds < 60) {
    return `${ms < 0 ? "-" : "+"}${seconds.toFixed(1)}s`;
  }
  const minutes = seconds / 60;
  return `${ms < 0 ? "-" : "+"}${minutes.toFixed(1)}m`;
}

function formatAbsoluteTime(date: Date): string {
  if (isNaN(date.getTime())) return "";
  const h = String(date.getUTCHours()).padStart(2, "0");
  const m = String(date.getUTCMinutes()).padStart(2, "0");
  const s = String(date.getUTCSeconds()).padStart(2, "0");
  return `${h}:${m}:${s}`;
}

const TimelineItem: React.FC<{ item: BreadcrumbItem; isLast: boolean }> = ({
  item,
  isLast,
}) => {
  const [expanded, setExpanded] = useState(false);
  const hasProps = Object.keys(item.props).length > 0;

  return (
    <Box className={classes.timelineItem}>
      <Box className={classes.rail}>
        <Box className={`${classes.iconWrapper} ${classes.iconCustom}`}>
          <IconTag size={14} />
        </Box>
        {!isLast && <Box className={classes.connector} />}
      </Box>

      <Box className={classes.content}>
        <Box
          className={classes.titleRow}
          style={hasProps ? { cursor: "pointer" } : undefined}
          onClick={hasProps ? () => setExpanded((v) => !v) : undefined}
        >
          {hasProps && (
            <Box className={classes.expandIcon}>
              {expanded ? (
                <IconChevronDown size={12} />
              ) : (
                <IconChevronRight size={12} />
              )}
            </Box>
          )}
          <Text className={classes.eventTitle} title={item.eventName}>
            {item.eventName || "Unknown Event"}
          </Text>
          {item.screenName && (
            <span className={`${classes.categoryBadge} ${classes.catCustom}`}>
              {item.screenName}
            </span>
          )}

          <Box className={classes.meta}>
            <Text className={classes.absTime}>
              {formatAbsoluteTime(item.timestamp)}
            </Text>
            <Text
              className={`${classes.relativeTime} ${
                item.relativeMs <= 0 ? classes.timeBefore : classes.timeAfter
              }`}
            >
              {formatRelativeTime(item.relativeMs)}
            </Text>
          </Box>
        </Box>

        {hasProps && (
          <Collapse in={expanded}>
            <Box className={classes.propsContainer}>
              {Object.entries(item.props).map(([key, value]) => (
                <Box key={key} className={classes.propRow}>
                  <Text className={classes.propKey}>{key}</Text>
                  <Text className={classes.propValue}>
                    {typeof value === "object"
                      ? JSON.stringify(value)
                      : String(value)}
                  </Text>
                </Box>
              ))}
            </Box>
          </Collapse>
        )}
      </Box>
    </Box>
  );
};

export const BreadcrumbTimeline: React.FC<BreadcrumbTimelineProps> = ({
  breadcrumbs,
  isLoading,
  isError,
  errorMessage,
}) => {
  if (isLoading) {
    return (
      <Box className={classes.emptyState}>
        <Text c="dimmed">Fetching breadcrumbs from data warehouse...</Text>
      </Box>
    );
  }

  if (isError) {
    return (
      <Box className={classes.emptyState}>
        <Text c="red">{errorMessage || "Failed to load breadcrumbs."}</Text>
      </Box>
    );
  }

  if (!breadcrumbs || breadcrumbs.length === 0) {
    return (
      <Box className={classes.emptyState}>
        <Text c="dimmed">
          No breadcrumbs available for this occurrence. Business events for this
          session were not found in the data warehouse.
        </Text>
      </Box>
    );
  }

  return (
    <Box>
      <Text size="xs" c="dimmed" className={classes.eventCount}>
        {breadcrumbs.length} event{breadcrumbs.length !== 1 ? "s" : ""}
      </Text>
      <Box className={classes.container}>
        {breadcrumbs.map((item, index) => (
          <TimelineItem
            key={item.id}
            item={item}
            isLast={index === breadcrumbs.length - 1}
          />
        ))}
      </Box>
    </Box>
  );
};
