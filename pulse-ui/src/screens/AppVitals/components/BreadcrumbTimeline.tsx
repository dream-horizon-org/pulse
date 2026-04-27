import {
  useRef,
  useState,
  useCallback,
  useLayoutEffect,
  useEffect,
} from "react";
import {
  Box,
  Text,
  Button,
  Stack,
  Group,
  Alert,
  Loader,
  Divider,
  Center,
} from "@mantine/core";
import {
  IconTag,
  IconChevronDown,
  IconChevronRight,
  IconTimeline,
  IconHistory,
  IconAlertCircle,
} from "@tabler/icons-react";
import { useVirtualizer, measureElement } from "@tanstack/react-virtual";
import {
  fetchBreadcrumbLogAttributes,
  type BreadcrumbItem,
} from "../pages/hooks/useOccurrenceBreadcrumbLogs";
import classes from "./BreadcrumbTimeline.module.css";

/** Above this count, rows are windowed with @tanstack/react-virtual (dynamic height). */
const VIRTUALIZE_THRESHOLD = 80;

const ESTIMATED_ROW_NO_EXPAND = 52;
const ESTIMATED_ROW_WITH_CHEVRON = 58;

function estimateRowSize(item: BreadcrumbItem | undefined): number {
  if (!item) return ESTIMATED_ROW_NO_EXPAND;
  return item.hasLogAttributes
    ? ESTIMATED_ROW_WITH_CHEVRON
    : ESTIMATED_ROW_NO_EXPAND;
}

function hasAttrsCacheEntry(
  cache: Record<string, Record<string, unknown>>,
  id: string,
): boolean {
  return Object.prototype.hasOwnProperty.call(cache, id);
}

interface BreadcrumbTimelineProps {
  breadcrumbs: BreadcrumbItem[];
  sessionId: string;
  occurrenceResetKey: string;
  isLoading?: boolean;
  isError?: boolean;
  errorMessage?: string;
  /** More rows available (older logs); merged list is oldest-first. */
  hasMore?: boolean;
  onLoadMore?: () => void;
  isLoadingMore?: boolean;
}

function formatRelativeTime(ms: number): string {
  const absMs = Math.abs(ms);
  const sign = ms < 0 ? "-" : "+";
  if (absMs < 1000) return `${sign}${absMs}ms`;
  const seconds = absMs / 1000;
  if (seconds < 60) return `${sign}${seconds.toFixed(1)}s`;
  const minutes = seconds / 60;
  return `${sign}${minutes.toFixed(1)}m`;
}

function formatAbsoluteTime(date: Date): string {
  if (isNaN(date.getTime())) return "";
  const h = String(date.getHours()).padStart(2, "0");
  const m = String(date.getMinutes()).padStart(2, "0");
  const s = String(date.getSeconds()).padStart(2, "0");
  return `${h}:${m}:${s}`;
}

function formatEventName(name: string): string {
  if (!name) return "Unknown Event";
  return name.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

interface TimelineItemProps {
  item: BreadcrumbItem;
  isLast: boolean;
  detailProps?: Record<string, unknown>;
  detailLoading?: boolean;
  detailError?: string;
  onLoadDetail: (item: BreadcrumbItem) => Promise<void>;
  onInvalidateMeasure?: () => void;
}

const TimelineItem: React.FC<TimelineItemProps> = ({
  item,
  isLast,
  detailProps,
  detailLoading,
  detailError,
  onLoadDetail,
  onInvalidateMeasure,
}) => {
  const [expanded, setExpanded] = useState(false);
  const canExpand = item.hasLogAttributes;

  useLayoutEffect(() => {
    onInvalidateMeasure?.();
  }, [expanded, detailProps, detailLoading, detailError, onInvalidateMeasure]);

  const handleHeaderClick = async () => {
    if (!canExpand) return;
    if (!expanded) {
      setExpanded(true);
      const notLoaded = detailProps === undefined && !detailLoading;
      const canRetry = !!detailError && !detailLoading;
      if (notLoaded || canRetry) {
        await onLoadDetail(item);
      }
    } else {
      setExpanded(false);
    }
  };

  const showDetailsPanel = expanded && canExpand;
  const hasLoadedDetail = detailProps !== undefined;
  const propEntries =
    detailProps && Object.keys(detailProps).length > 0
      ? Object.entries(detailProps)
      : [];

  return (
    <Box className={classes.timelineItem}>
      <Box className={classes.rail}>
        <Box className={`${classes.iconWrapper} ${classes.iconCustom}`}>
          <IconTag size={12} />
        </Box>
        {!isLast && <Box className={classes.connector} />}
      </Box>

      <Box
        className={`${classes.content}${isLast ? ` ${classes.contentLast}` : ""}`}
      >
        <Box
          className={classes.titleRow}
          style={canExpand ? { cursor: "pointer" } : undefined}
          onClick={canExpand ? () => void handleHeaderClick() : undefined}
        >
          {canExpand && (
            <Box className={classes.expandIcon}>
              {expanded ? (
                <IconChevronDown size={11} />
              ) : (
                <IconChevronRight size={11} />
              )}
            </Box>
          )}
          <Text className={classes.eventTitle} title={item.eventName}>
            {formatEventName(item.eventName)}
          </Text>
          {item.screenName && (
            <span className={classes.screenBadge}>{item.screenName}</span>
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

        {showDetailsPanel && (
          <Box className={classes.detailsPanel}>
            {detailLoading ? (
              <Center py="xs">
                <Loader size="xs" color="teal" />
              </Center>
            ) : detailError ? (
              <Text size="xs" c="red" py={4}>
                {detailError}
              </Text>
            ) : hasLoadedDetail && propEntries.length > 0 ? (
              <Box className={classes.propsContainer}>
                {propEntries.map(([key, value]) => (
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
            ) : hasLoadedDetail ? (
              <Text size="xs" c="dimmed" py={4}>
                No attributes on this log.
              </Text>
            ) : null}
          </Box>
        )}
      </Box>
    </Box>
  );
};

interface RowListSharedProps {
  breadcrumbs: BreadcrumbItem[];
  attrsCache: Record<string, Record<string, unknown>>;
  loadingIds: Record<string, boolean>;
  errors: Record<string, string>;
  loadDetail: (item: BreadcrumbItem) => Promise<void>;
}

function BreadcrumbTimelineRowsPlain({
  breadcrumbs,
  attrsCache,
  loadingIds,
  errors,
  loadDetail,
}: RowListSharedProps) {
  return (
    <Box className={classes.scrollRegion}>
      <Box className={classes.container}>
        {breadcrumbs.map((item, index) => (
          <TimelineItem
            key={item.id}
            item={item}
            isLast={index === breadcrumbs.length - 1}
            detailProps={
              hasAttrsCacheEntry(attrsCache, item.id)
                ? attrsCache[item.id]
                : undefined
            }
            detailLoading={!!loadingIds[item.id]}
            detailError={errors[item.id]}
            onLoadDetail={loadDetail}
          />
        ))}
      </Box>
    </Box>
  );
}

function BreadcrumbTimelineRowsVirtualized({
  breadcrumbs,
  attrsCache,
  loadingIds,
  errors,
  loadDetail,
}: RowListSharedProps) {
  const parentRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: breadcrumbs.length,
    getScrollElement: () => parentRef.current,
    estimateSize: (index) => estimateRowSize(breadcrumbs[index]),
    overscan: 16,
    getItemKey: (index) => breadcrumbs[index]?.id ?? index,
    measureElement,
    useAnimationFrameWithResizeObserver: true,
  });

  const invalidate = useCallback(() => {
    virtualizer.measure();
  }, [virtualizer]);

  useEffect(() => {
    virtualizer.measure();
  }, [attrsCache, virtualizer]);

  const virtualItems = virtualizer.getVirtualItems();
  const totalSize = virtualizer.getTotalSize();

  return (
    <Box ref={parentRef} className={classes.scrollRegion}>
      <Box className={classes.virtualListInner} style={{ height: totalSize }}>
        {virtualItems.map((virtualRow) => {
          const item = breadcrumbs[virtualRow.index];
          if (!item) return null;
          return (
            <Box
              key={virtualRow.key}
              data-index={virtualRow.index}
              ref={virtualizer.measureElement}
              className={classes.virtualRow}
              style={{
                position: "absolute",
                top: 0,
                transform: `translateY(${virtualRow.start}px)`,
              }}
            >
              <TimelineItem
                item={item}
                isLast={virtualRow.index === breadcrumbs.length - 1}
                detailProps={
                  hasAttrsCacheEntry(attrsCache, item.id)
                    ? attrsCache[item.id]
                    : undefined
                }
                detailLoading={!!loadingIds[item.id]}
                detailError={errors[item.id]}
                onLoadDetail={loadDetail}
                onInvalidateMeasure={invalidate}
              />
            </Box>
          );
        })}
      </Box>
    </Box>
  );
}

export const BreadcrumbTimeline: React.FC<BreadcrumbTimelineProps> = ({
  breadcrumbs,
  sessionId,
  occurrenceResetKey,
  isLoading,
  isError,
  errorMessage,
  hasMore,
  onLoadMore,
  isLoadingMore,
}) => {
  const [attrsCache, setAttrsCache] = useState<
    Record<string, Record<string, unknown>>
  >({});
  const [loadingIds, setLoadingIds] = useState<Record<string, boolean>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const attrsCacheRef = useRef(attrsCache);
  const inFlightRef = useRef<Set<string>>(new Set());

  useEffect(() => {
    attrsCacheRef.current = attrsCache;
  }, [attrsCache]);

  useEffect(() => {
    setAttrsCache({});
    setLoadingIds({});
    setErrors({});
    inFlightRef.current = new Set();
  }, [occurrenceResetKey]);

  const loadDetail = useCallback(
    async (item: BreadcrumbItem) => {
      if (!sessionId || !item.hasLogAttributes) return;
      if (hasAttrsCacheEntry(attrsCacheRef.current, item.id)) return;
      if (inFlightRef.current.has(item.id)) return;
      inFlightRef.current.add(item.id);
      setLoadingIds((m) => ({ ...m, [item.id]: true }));
      setErrors((e) => {
        const next = { ...e };
        delete next[item.id];
        return next;
      });
      try {
        const props = await fetchBreadcrumbLogAttributes(sessionId, item);
        setAttrsCache((c) => ({ ...c, [item.id]: props }));
      } catch (err: unknown) {
        const msg =
          err instanceof Error ? err.message : "Failed to load attributes";
        setErrors((e) => ({ ...e, [item.id]: msg }));
      } finally {
        inFlightRef.current.delete(item.id);
        setLoadingIds((m) => {
          const next = { ...m };
          delete next[item.id];
          return next;
        });
      }
    },
    [sessionId],
  );

  if (isLoading) {
    return (
      <Stack
        className={classes.stateRoot}
        align="center"
        justify="center"
        gap="md"
        py="xl"
        px="md"
      >
        <Text size="sm" c="dimmed" ta="center">
          Loading session logs from this occurrence…
        </Text>
      </Stack>
    );
  }

  if (isError) {
    return (
      <Box className={classes.stateRoot} p="md">
        <Alert
          variant="light"
          color="red"
          title="Could not load breadcrumbs"
          icon={<IconAlertCircle size={18} />}
        >
          {errorMessage ||
            "Something went wrong. Try again or open another occurrence."}
        </Alert>
      </Box>
    );
  }

  if (!breadcrumbs || breadcrumbs.length === 0) {
    return (
      <Stack
        className={classes.stateRoot}
        align="center"
        justify="center"
        py="xl"
        px="md"
        gap="xs"
      >
        <IconTimeline size={28} stroke={1.25} className={classes.emptyIcon} />
        <Text size="sm" c="dimmed" ta="center" maw={320}>
          No session logs were returned for this occurrence. Try another
          occurrence or confirm the SDK is sending logs for this session.
        </Text>
      </Stack>
    );
  }

  const rowProps: RowListSharedProps = {
    breadcrumbs,
    attrsCache,
    loadingIds,
    errors,
    loadDetail,
  };

  return (
    <Box className={classes.root}>
      <Box className={classes.panelHeader}>
        <Group gap="xs" wrap="nowrap" align="flex-start">
          <IconTimeline size={16} className={classes.panelHeaderIcon} />
          <Box style={{ minWidth: 0 }}>
            <Text size="sm" fw={600} className={classes.panelHeaderTitle}>
              Session timeline
            </Text>
            <Text size="xs" c="dimmed" lh={1.35}>
              {breadcrumbs.length} log{breadcrumbs.length !== 1 ? "s" : ""}{" "}
              loaded, oldest first · times relative to this crash
            </Text>
          </Box>
        </Group>
      </Box>

      {breadcrumbs.length >= VIRTUALIZE_THRESHOLD ? (
        <BreadcrumbTimelineRowsVirtualized {...rowProps} />
      ) : (
        <BreadcrumbTimelineRowsPlain {...rowProps} />
      )}

      {hasMore && onLoadMore ? (
        <Box className={classes.loadMoreFooter}>
          <Divider
            color="rgba(14, 201, 194, 0.2)"
            label={
              <Text size="xs" c="dimmed" fw={500}>
                Earlier in session
              </Text>
            }
            labelPosition="center"
          />
          <Button
            variant="light"
            color="teal"
            size="sm"
            fullWidth
            className={classes.loadMoreButton}
            loading={isLoadingMore}
            leftSection={<IconHistory size={16} />}
            onClick={onLoadMore}
          >
            Load earlier logs
          </Button>
          <Text size="xs" c="dimmed" ta="center" mt={4}>
            Fetches the next 50 older entries from this session
          </Text>
        </Box>
      ) : null}
    </Box>
  );
};
