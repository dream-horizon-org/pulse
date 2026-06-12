import { Paper, Group, Text, Switch } from "@mantine/core";
import { useState, useRef, useEffect } from "react";
import { SessionTimelineEvent } from "../../SessionTimeline.interface";
import { SpanRow } from "../SpanRow";
import { EventLegend } from "../EventLegend";
import { TimelineAxis } from "../TimelineAxis";
import { OtelEventType } from "../../constants/otelConstants";
import classes from "./TimelineView.module.css";

interface TimelineViewProps {
  spans: SessionTimelineEvent[];
  maxTime: number;
  expandedSpans: Set<string>;
  onToggleExpand: (spanId: string) => void;
  activeFilters: Set<OtelEventType>;
  onFilterToggle: (type: OtelEventType) => void;
  onSpanClick: (span: SessionTimelineEvent) => void;
}

export function TimelineView({
  spans,
  maxTime,
  expandedSpans,
  onToggleExpand,
  activeFilters,
  onFilterToggle,
  onSpanClick,
}: TimelineViewProps) {
  const [showMarkers, setShowMarkers] = useState(false);
  const headerScrollRef = useRef<HTMLDivElement>(null);
  const contentScrollRef = useRef<HTMLDivElement>(null);

  // Sync horizontal scrolling between header markers and timeline content
  useEffect(() => {
    if (!showMarkers) return;

    const headerElement = headerScrollRef.current;
    const contentElement = contentScrollRef.current;

    if (!headerElement || !contentElement) return;

    let isScrolling = false;

    const handleContentScroll = () => {
      if (isScrolling) return;
      isScrolling = true;
      headerElement.scrollLeft = contentElement.scrollLeft;
      requestAnimationFrame(() => {
        isScrolling = false;
      });
    };

    const handleHeaderScroll = () => {
      if (isScrolling) return;
      isScrolling = true;
      contentElement.scrollLeft = headerElement.scrollLeft;
      requestAnimationFrame(() => {
        isScrolling = false;
      });
    };

    contentElement.addEventListener("scroll", handleContentScroll, {
      passive: true,
    });
    headerElement.addEventListener("scroll", handleHeaderScroll, {
      passive: true,
    });

    return () => {
      contentElement.removeEventListener("scroll", handleContentScroll);
      headerElement.removeEventListener("scroll", handleHeaderScroll);
    };
  }, [showMarkers]);

  const renderSpanList = (span: SessionTimelineEvent, level: number = 0) => {
    const hasChildren = span.children && span.children.length > 0;
    const isExpanded = expandedSpans.has(span.id);

    return (
      <div key={span.id} className={classes.eventListRow}>
        <div className={classes.eventListCell}>
          <SpanRow
            span={span}
            level={level}
            maxTime={maxTime}
            isExpanded={isExpanded}
            onToggleExpand={() => onToggleExpand(span.id)}
            onSpanClick={onSpanClick}
            showTimeline={false}
          />
        </div>
        {hasChildren && isExpanded && (
          <div className={classes.nestedSpans}>
            {span.children?.map((child) => renderSpanList(child, level + 1))}
          </div>
        )}
      </div>
    );
  };

  const renderSpanTimeline = (
    span: SessionTimelineEvent,
    level: number = 0,
  ) => {
    const hasChildren = span.children && span.children.length > 0;
    const isExpanded = expandedSpans.has(span.id);

    return (
      <div key={span.id} className={classes.timelineRow}>
        <div className={classes.timelineCell}>
          <SpanRow
            span={span}
            level={level}
            maxTime={maxTime}
            isExpanded={isExpanded}
            onToggleExpand={() => onToggleExpand(span.id)}
            onSpanClick={onSpanClick}
            showTimeline={true}
          />
        </div>
        {hasChildren && isExpanded && (
          <div className={classes.nestedSpans}>
            {span.children?.map((child) =>
              renderSpanTimeline(child, level + 1),
            )}
          </div>
        )}
      </div>
    );
  };

  return (
    <Paper className={classes.timelineSection} p="md">
      <Group justify="space-between" align="center" mb="md" wrap="nowrap">
        <Group gap="md" align="center" wrap="nowrap">
          <Text className={classes.sectionTitle}>Session Timeline</Text>
          <EventLegend
            activeFilters={activeFilters}
            onFilterToggle={onFilterToggle}
          />
        </Group>
        <Group gap="md" align="center">
          <Switch
            label="Show markers"
            checked={showMarkers}
            onChange={(e) => setShowMarkers(e.currentTarget.checked)}
            size="xs"
          />
          <Text size="sm" c="dimmed">
            Showing {spans.length} spans
          </Text>
        </Group>
      </Group>

      <div className={classes.timelineWrapper}>
        {showMarkers && (
          <div className={classes.markerHeaderRow}>
            <div className={classes.markerSpacer}></div>
            <div className={classes.scrollableColumn} ref={headerScrollRef}>
              <div
                className={classes.scrollableContent}
                style={{ width: `${Math.max(100, (maxTime / 1000) * 2)}%` }}
              >
                <div className={classes.markerContainer}>
                  <TimelineAxis maxTime={maxTime} />
                </div>
              </div>
            </div>
          </div>
        )}
        <div className={classes.contentRow}>
          <div className={classes.fixedColumn}>
            {showMarkers && <div className={classes.markerSpacer}></div>}
            <div className={classes.eventListContainer}>
              {spans.map((span) => renderSpanList(span))}
            </div>
          </div>
          <div className={classes.scrollableColumn} ref={contentScrollRef}>
            <div
              className={classes.scrollableContent}
              style={{ width: `${Math.max(100, (maxTime / 1000000) * 2)}%` }}
            >
              <div className={classes.timelineContainer}>
                {spans.map((span) => renderSpanTimeline(span))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </Paper>
  );
}
