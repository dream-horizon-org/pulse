import { Group, Text } from "@mantine/core";
import { IconChevronDown, IconChevronRight } from "@tabler/icons-react";
import { useMantineTheme } from "@mantine/core";
import { SessionTimelineEvent } from "../../SessionTimeline.interface";
import {
  getOtelEventIcon,
  getOtelEventColor,
} from "../../constants/otelConstants";
import {
  calculateEventPosition,
  calculateEventWidth,
} from "../../utils/formatters";
import classes from "./SpanRow.module.css";

interface SpanRowProps {
  span: SessionTimelineEvent;
  level: number;
  maxTime: number;
  isExpanded: boolean;
  onToggleExpand: () => void;
  onSpanClick?: (span: SessionTimelineEvent) => void;
  showTimeline?: boolean;
}

export function SpanRow({
  span,
  level,
  maxTime,
  isExpanded,
  onToggleExpand,
  onSpanClick,
  showTimeline = true,
}: SpanRowProps) {
  const theme = useMantineTheme();
  const hasChildren = span.children && span.children.length > 0;
  const EventIcon = getOtelEventIcon(span.type);
  const eventColor = getOtelEventColor(span.type, theme);
  const position = calculateEventPosition(span.timestamp, maxTime);
  const width = calculateEventWidth(span.duration, maxTime);
  const isInstant = !span.duration || span.duration === 0;

  if (showTimeline) {
    const isLog = span.type === "log";
    const durationText =
      span.duration !== undefined ? `${span.duration}ms` : "0ms";
    const barWidthPercent = isInstant ? 0.1 : Math.max(width, 0.5);

    // For instant events (dots), center them at the marker position
    // For all bars (including logs), align left edge to the marker position
    const shouldCenter = isInstant && !isLog;

    return (
      <div className={classes.timelineVisualization}>
        <div
          className={classes.timelineBar}
          style={{
            left: `${position}%`,
            width: isInstant ? "4px" : `${barWidthPercent}%`,
            backgroundColor: eventColor,
            borderRadius: isInstant ? "50%" : "4px",
            height: isInstant ? "12px" : "8px",
            transform: shouldCenter ? "translateX(-50%)" : "none",
          }}
          title={isLog ? span.name : `${span.name} - ${durationText}`}
          onClick={() => onSpanClick?.(span)}
        />
        {!isLog && (
          <span
            className={classes.durationLabel}
            style={{
              left: `${position}%`,
              transform: shouldCenter ? "translateX(-50%)" : "none",
            }}
          >
            {durationText}
          </span>
        )}
      </div>
    );
  }

  return (
    <div
      className={classes.eventList}
      style={{ paddingLeft: `${level * 24}px` }}
      onClick={() => onSpanClick?.(span)}
    >
      <Group gap="xs" align="center" wrap="nowrap" style={{ width: "100%" }}>
        {hasChildren && (
          <button
            className={classes.expandButton}
            onClick={(e) => {
              e.stopPropagation();
              onToggleExpand();
            }}
          >
            {isExpanded ? (
              <IconChevronDown size={14} />
            ) : (
              <IconChevronRight size={14} />
            )}
          </button>
        )}
        {!hasChildren && <div className={classes.expandSpacer} />}
        <EventIcon size={16} color={eventColor} style={{ flexShrink: 0 }} />
        <Text size="sm" fw={500} className={classes.eventName} truncate>
          {span.name}
        </Text>
        {span.type !== "log" && (
          <Text size="xs" c="dimmed" className={classes.durationText}>
            {span.duration !== undefined ? `${span.duration}ms` : "instant"}
          </Text>
        )}
      </Group>
    </div>
  );
}
