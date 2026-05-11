import { useEffect, useRef, useState, useCallback, useMemo } from "react";
import { FlameChart as FlameChartLib } from "flame-chart-js";
import { Box, Text, Loader, ActionIcon, Tooltip, Group } from "@mantine/core";
import {
  IconZoomIn,
  IconZoomOut,
  IconZoomReset,
  IconChartBar,
  IconArrowUp,
  IconArrowDown,
} from "@tabler/icons-react";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import {
  FlameChartNode,
  toFlameChartJsFormat,
  getColorForPulseType,
  formatPulseType,
} from "../../utils/flameChartTransform";
import classes from "./FlameChart.module.css";
dayjs.extend(utc);

interface FlameChartProps {
  data: FlameChartNode[];
  sessionDuration: number;
  sessionStartTime: number; // Unix timestamp in ms
  totalDepth?: number; // Maximum depth of the tree for scroll indicator
  highlightTraceId?: string | null;
  onItemClick?: (item: FlameChartNode) => void;
  isLoading?: boolean;
}

interface ScrollInfo {
  scrollTop: number;
  scrollHeight: number;
  clientHeight: number;
  canScrollUp: boolean;
  canScrollDown: boolean;
}

// Block height used by flame-chart-js
const BLOCK_HEIGHT = 18;
const TIME_GRID_HEIGHT = 24; // Height of the time grid at the top

// Extended type for flame chart instance with access to internal properties
type FlameChartInstance = FlameChartLib & {
  renderEngine?: {
    clear?: () => void;
    positionX?: number;
    zoom?: number;
    blockHeight?: number;
    render?: () => void;
  };
  setNodes?: (nodes: any) => void;
  plugins?: any[];
};

/**
 * Flattened node info for custom hit-testing.
 * Uses start, end, and type to uniquely identify nodes.
 */
interface FlatNodeInfo {
  node: FlameChartNode;
  level: number;
  start: number;
  end: number;
  type: string;
}

// Legend item with filter key for matching nodes
interface LegendItem {
  key: string;
  label: string;
  color: string;
  // Raw pulseType this legend entry represents - used for O(1) Set-based
  // lookup during per-node filtering instead of an O(L) some()+closure scan.
  pulseType: string;
  // Function to check if a node matches this legend category
  matches: (node: FlameChartNode) => boolean;
}

// Module-scoped self-time cache. WeakMap auto-clears when nodes are GC'd, so
// no manual invalidation is needed. Re-used across hover frames so the
// tooltip doesn't reallocate intervals on every mouse move.
const selfTimeCache = new WeakMap<FlameChartNode, number>();

function calculateSelfTime(
  parentStart: number,
  parentDuration: number,
  childNodes: FlameChartNode[] | undefined,
): number {
  if (!childNodes || childNodes.length === 0) return parentDuration;

  const parentEnd = parentStart + parentDuration;

  // Build clipped intervals in a single pass (no .filter().map().filter() chain)
  const intervals: [number, number][] = [];
  for (const child of childNodes) {
    if (child.duration <= 0) continue;
    const childStart = Math.max(child.start, parentStart);
    const childEnd = Math.min(child.start + child.duration, parentEnd);
    if (childEnd > childStart) intervals.push([childStart, childEnd]);
  }
  if (intervals.length === 0) return parentDuration;

  intervals.sort((a, b) => a[0] - b[0]);

  // Merge overlapping intervals in place
  let mergedEndIdx = 0;
  for (let i = 1; i < intervals.length; i++) {
    const last = intervals[mergedEndIdx];
    const cur = intervals[i];
    if (cur[0] <= last[1]) {
      if (cur[1] > last[1]) last[1] = cur[1];
    } else {
      mergedEndIdx++;
      intervals[mergedEndIdx] = cur;
    }
  }

  let totalChildTime = 0;
  for (let i = 0; i <= mergedEndIdx; i++) {
    totalChildTime += intervals[i][1] - intervals[i][0];
  }

  return Math.max(0, parentDuration - totalChildTime);
}

function getSelfTime(node: FlameChartNode): number {
  const cached = selfTimeCache.get(node);
  if (cached !== undefined) return cached;
  const v = calculateSelfTime(node.start, node.duration, node.children);
  selfTimeCache.set(node, v);
  return v;
}

// Extract unique pulse types from data using metadata.pulseType
function extractPulseTypes(nodes: FlameChartNode[]): Set<string> {
  const types = new Set<string>();

  const traverse = (nodeList: FlameChartNode[]) => {
    for (const node of nodeList) {
      const pulseType = node.metadata?.pulseType;
      if (typeof pulseType === "string" && pulseType.length > 0) {
        types.add(pulseType);
      }
      traverse(node.children);
    }
  };

  traverse(nodes);
  return types;
}

// Generate legend items from pulse types
function generateLegendItems(pulseTypes: Set<string>): LegendItem[] {
  const items: LegendItem[] = [];
  const sortedTypes = Array.from(pulseTypes).sort();

  for (const pulseType of sortedTypes) {
    const key = pulseType.toLowerCase().replace(/[^a-z0-9]/g, "_");
    const color = getColorForPulseType(pulseType);
    const label = formatPulseType(pulseType);

    items.push({
      key,
      label,
      color,
      pulseType,
      matches: (node: FlameChartNode) => node.metadata?.pulseType === pulseType,
    });
  }

  return items;
}

/**
 * Format duration for display
 */
function formatDuration(ms: number): string {
  if (ms < 1) return `${(ms * 1000).toFixed(0)}µs`;
  if (ms < 1000) return `${ms.toFixed(2)}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

export function FlameChart({
  data,
  sessionDuration,
  sessionStartTime,
  totalDepth = 0,
  highlightTraceId,
  onItemClick,
  isLoading,
}: FlameChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const flameChartRef = useRef<FlameChartInstance | null>(null);
  const [zoom, setZoom] = useState(1);
  const [scrollInfo, setScrollInfo] = useState<ScrollInfo>({
    scrollTop: 0,
    scrollHeight: 0,
    clientHeight: 0,
    canScrollUp: false,
    canScrollDown: false,
  });

  // Extract unique pulse types from data and generate legend items
  const legendItems = useMemo(() => {
    const pulseTypes = extractPulseTypes(data);
    return generateLegendItems(pulseTypes);
  }, [data]);

  // State for active legend filters - all enabled by default
  const [activeFilters, setActiveFilters] = useState<Set<string>>(new Set());

  // Sync active filters when legend items change. Compare by key-set identity
  // so that we don't trigger a redundant filterContext recomputation when the
  // legend regenerates with the same set of keys (which happens whenever
  // `data`'s reference changes even if shape is identical).
  useEffect(() => {
    setActiveFilters((prev) => {
      if (prev.size === legendItems.length) {
        let identical = true;
        for (const item of legendItems) {
          if (!prev.has(item.key)) {
            identical = false;
            break;
          }
        }
        if (identical) return prev;
      }
      return new Set(legendItems.map((item) => item.key));
    });
  }, [legendItems]);

  // Toggle a filter on/off
  const toggleFilter = useCallback((key: string) => {
    setActiveFilters((prev) => {
      const next = new Set(prev);
      if (next.has(key)) {
        // Don't allow deselecting all filters - keep at least one
        if (next.size > 1) next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }, []);

  // Select only this filter (exclusive selection)
  const selectOnlyFilter = useCallback((key: string) => {
    setActiveFilters(new Set([key]));
  }, []);

  // Reset to all filters active
  const resetFilters = useCallback(() => {
    setActiveFilters(new Set(legendItems.map((item) => item.key)));
  }, [legendItems]);

  // Calculate content height based on total depth
  const contentHeight = useMemo(() => {
    const calculatedHeight = TIME_GRID_HEIGHT + totalDepth * BLOCK_HEIGHT + 50;
    return Math.max(400, calculatedHeight);
  }, [totalDepth]);

  // Combined filter pass: a single tree traversal that produces filteredData,
  // nodeByIdMap, and flatNodesList. Replaces what used to be four separate
  // recursions over the (potentially huge) tree on every legend toggle.
  //
  // Optimizations:
  //  - When all filters are active (the common case) we return `data` by
  //    reference and only walk the tree once to build the indexes - no
  //    cloning, no allocation of new children arrays.
  //  - Per-node match check is an O(1) Set.has lookup instead of an O(L)
  //    `legendItems.some(...)` + closure invocation.
  //  - When a node matches and none of its descendants were filtered out,
  //    we reuse the original node reference instead of allocating a clone.
  const filterContext = useMemo(() => {
    const nodeByIdMap = new Map<string, FlameChartNode>();
    const flatNodesList: FlatNodeInfo[] = [];

    if (!data || data.length === 0) {
      return {
        filteredData: [] as FlameChartNode[],
        nodeByIdMap,
        flatNodesList,
      };
    }

    const activePulseTypes = new Set<string>();
    for (const item of legendItems) {
      if (activeFilters.has(item.key)) activePulseTypes.add(item.pulseType);
    }
    const allActive =
      legendItems.length > 0 && activePulseTypes.size === legendItems.length;

    const register = (node: FlameChartNode, level: number) => {
      nodeByIdMap.set(node.id, node);
      flatNodesList.push({
        node,
        level,
        start: node.start,
        end: node.start + node.duration,
        type: node.type,
      });
    };

    if (allActive) {
      const visit = (nodes: FlameChartNode[], level: number) => {
        for (const node of nodes) {
          register(node, level);
          if (node.children.length > 0) visit(node.children, level + 1);
        }
      };
      visit(data, 0);
      return { filteredData: data, nodeByIdMap, flatNodesList };
    }

    const visit = (
      node: FlameChartNode,
      level: number,
    ): FlameChartNode | null => {
      const pulseTypeRaw = node.metadata?.pulseType;
      const pulseType = typeof pulseTypeRaw === "string" ? pulseTypeRaw : "";
      const matches = activePulseTypes.has(pulseType);

      let nextChildren: FlameChartNode[] = node.children;
      let childrenChanged = false;

      if (node.children.length > 0) {
        const collected: FlameChartNode[] = [];
        for (const child of node.children) {
          const filtered = visit(child, level + 1);
          if (filtered === null) {
            childrenChanged = true;
            continue;
          }
          if (filtered !== child) childrenChanged = true;
          collected.push(filtered);
        }
        nextChildren = collected;
      }

      if (matches) {
        const out = childrenChanged
          ? { ...node, children: nextChildren }
          : node;
        register(out, level);
        return out;
      }

      if (nextChildren.length > 0) {
        const out: FlameChartNode = {
          ...node,
          children: nextChildren,
          color: "#e0e0e0",
        };
        register(out, level);
        return out;
      }

      return null;
    };

    const filteredData: FlameChartNode[] = [];
    for (const root of data) {
      const filtered = visit(root, 0);
      if (filtered !== null) filteredData.push(filtered);
    }

    return { filteredData, nodeByIdMap, flatNodesList };
  }, [data, legendItems, activeFilters]);

  const filteredData = filterContext.filteredData;
  const nodeByIdMap = filterContext.nodeByIdMap;
  const flatNodesList = filterContext.flatNodesList;

  // Spatial index: bucket flat nodes by level. Hover hit-testing then only
  // scans the row under the cursor instead of the entire flattened tree.
  // Each bucket is sorted by start time so we can binary-search within a row
  // (still using linear filter for simplicity here, but the sort is in place
  // for a future upgrade).
  const flatNodesByLevel = useMemo(() => {
    const map = new Map<number, FlatNodeInfo[]>();
    for (const info of flatNodesList) {
      const arr = map.get(info.level);
      if (arr) arr.push(info);
      else map.set(info.level, [info]);
    }
    for (const arr of Array.from(map.values()))
      arr.sort((a, b) => a.start - b.start);
    return map;
  }, [flatNodesList]);

  const flatNodesByLevelRef = useRef(flatNodesByLevel);
  useEffect(() => {
    flatNodesByLevelRef.current = flatNodesByLevel;
  }, [flatNodesByLevel]);

  // Stable callback ref for onItemClick to avoid re-creating the flame chart
  const onItemClickRef = useRef(onItemClick);
  useEffect(() => {
    onItemClickRef.current = onItemClick;
  }, [onItemClick]);

  // Transform filtered data to flame-chart-js format (memoized)
  const flameChartData = useMemo(() => {
    if (!filteredData || filteredData.length === 0) return null;
    return toFlameChartJsFormat(filteredData, sessionDuration);
  }, [filteredData, sessionDuration]);

  // Keep nodeByIdMap in a ref so select handler always has latest version
  const nodeByIdMapRef = useRef(nodeByIdMap);
  useEffect(() => {
    nodeByIdMapRef.current = nodeByIdMap;
  }, [nodeByIdMap]);

  // Keep sessionStartTime / contentHeight in refs so the chart-init effect's
  // closures stay current even though we no longer re-run init when those
  // props change. (Re-running init would tear down and rebuild the entire
  // FlameChartLib instance, which is the freeze we are trying to eliminate.)
  const sessionStartTimeRef = useRef(sessionStartTime);
  useEffect(() => {
    sessionStartTimeRef.current = sessionStartTime;
  }, [sessionStartTime]);

  const contentHeightRef = useRef(contentHeight);
  useEffect(() => {
    contentHeightRef.current = contentHeight;
  }, [contentHeight]);

  // Teardown for the FlameChartLib instance + its listeners. Stored in a ref
  // so it only runs on actual component unmount, not on every flameChartData
  // change.
  const teardownRef = useRef<(() => void) | null>(null);
  useEffect(() => {
    return () => {
      teardownRef.current?.();
      teardownRef.current = null;
    };
  }, []);

  // Initialize flame chart when data is available
  useEffect(() => {
    if (
      !containerRef.current ||
      !canvasRef.current ||
      !flameChartData ||
      flameChartData.length === 0
    ) {
      return;
    }

    // Hot path: existing chart instance, just push new data into it.
    // This is the single biggest win for filter-toggle responsiveness.
    if (flameChartRef.current?.setNodes) {
      flameChartRef.current.setNodes(flameChartData);
      // Top-level render() does the recalc + paint; renderEngine.render()
      // alone may skip layout updates when depth changes.
      flameChartRef.current.render();
      return;
    }

    const canvas = canvasRef.current;
    const container = containerRef.current;

    const resizeCanvas = () => {
      const rect = container.getBoundingClientRect();
      const width = rect.width || 800;
      const containerHeight = rect.height || 400;
      const height = Math.min(containerHeight, contentHeightRef.current);
      canvas.width = width;
      canvas.height = Math.max(400, height);
      return { width, height: Math.max(400, height) };
    };

    const { width, height } = resizeCanvas();

    if (flameChartRef.current) {
      const ctx = canvas.getContext("2d");
      if (ctx) ctx.clearRect(0, 0, canvas.width, canvas.height);
      flameChartRef.current = null;
    }

    try {
      /**
       * Find the topmost (latest-starting) overlapping node at a given position.
       *
       * Uses the level-bucketed spatial index so we only scan nodes on the
       * row under the cursor, not the entire flattened tree.
       */
      const findTopmostNodeAtPosition = (
        mouseX: number,
        mouseY: number,
        renderEngine: any,
      ): { node: FlameChartNode; level: number } | null => {
        const chart = flameChartRef.current as any;
        if (!chart?.plugins) return null;

        const flameChartPlugin = chart.plugins.find(
          (p: any) => p.name === "flameChartPlugin",
        );
        if (!flameChartPlugin) return null;

        const pluginYOffset = flameChartPlugin.renderEngine?.position || 0;
        const positionX = renderEngine.positionX || 0;
        const zoom = renderEngine.zoom || 1;
        const blockHeight = renderEngine.blockHeight || BLOCK_HEIGHT;

        if (zoom <= 0) return null;

        const clickTime = positionX + mouseX / zoom;
        const adjustedMouseY = mouseY - pluginYOffset;
        const clickLevel = Math.floor(adjustedMouseY / blockHeight);

        if (clickLevel < 0) return null;

        // Spatial-index lookup: only scan the row under the cursor
        const levelNodes = flatNodesByLevelRef.current.get(clickLevel);
        if (!levelNodes || levelNodes.length === 0) return null;

        // Linear scan within the row. (Could be upgraded to binary search on
        // start, but a single row is much smaller than the full flat list.)
        const matchingNodes: FlatNodeInfo[] = [];
        for (const info of levelNodes) {
          if (clickTime >= info.start && clickTime <= info.end) {
            matchingNodes.push(info);
          } else if (info.start > clickTime) {
            // Sorted by start - we've passed any possible match
            break;
          }
        }

        if (matchingNodes.length === 0) return null;
        if (matchingNodes.length === 1)
          return { node: matchingNodes[0].node, level: clickLevel };

        // Multiple overlapping nodes at the same level - determine "on top"
        matchingNodes.sort((a, b) => {
          if (b.start !== a.start) return b.start - a.start;
          const aDuration = a.end - a.start;
          const bDuration = b.end - b.start;
          if (aDuration !== bDuration) return aDuration - bDuration;
          return a.node.name.localeCompare(b.node.name);
        });

        return { node: matchingNodes[0].node, level: clickLevel };
      };

      /**
       * Update the library's internal selection to show highlight on the
       * correct node.
       */
      const updateLibraryHighlight = (
        node: FlameChartNode,
        clickedLevel: number,
      ) => {
        const chart = flameChartRef.current as any;
        if (!chart?.plugins) return;

        const flameChartPlugin = chart.plugins.find(
          (p: any) => p.name === "flameChartPlugin" && p.flatTree,
        );
        if (!flameChartPlugin?.flatTree) return;

        const libraryNode = flameChartPlugin.flatTree.find((n: any) => {
          const nodeStart = n.source.start;
          const nodeDuration = n.source.duration;
          const nodeType = n.source.type;
          const nodeLevel = n.level;

          return (
            Math.abs(nodeStart - node.start) < 0.01 &&
            Math.abs(nodeDuration - node.duration) < 0.01 &&
            nodeType === node.type &&
            nodeLevel === clickedLevel
          );
        });

        if (libraryNode) {
          flameChartPlugin.selectedRegion = {
            type: "node",
            data: libraryNode,
          };
          if (chart.renderEngine?.render) chart.renderEngine.render();
        }
      };

      // Custom tooltip - uses our hit-testing to show correct tooltip for
      // overlapping nodes. Self-time is cached per node in a WeakMap so we
      // don't recompute interval merges on every hover frame.
      const customTooltip = (
        hoveredRegion: any,
        renderEngine: any,
        mouse: any,
      ) => {
        if (!hoveredRegion) return;

        const result = findTopmostNodeAtPosition(
          mouse.x,
          mouse.y,
          renderEngine,
        );

        let nodeForSelfTime: FlameChartNode | null = null;
        let nodeData: {
          start: number;
          duration: number;
          name: string;
          children?: any[];
          type?: string;
        };

        if (result) {
          nodeForSelfTime = result.node;
          nodeData = {
            start: result.node.start,
            duration: result.node.duration,
            name: result.node.name,
            children: result.node.children,
            type: result.node.type,
          };
        } else if (hoveredRegion.data?.source) {
          nodeData = hoveredRegion.data.source;
        } else {
          return;
        }

        const { start, duration, name, children, type } = nodeData;
        const timeUnits = renderEngine.getTimeUnits();
        const nodeAccuracy = renderEngine.getAccuracy() + 2;

        const isPointInTimeEvent =
          type === "log" || type === "exception" || type === "orphan-log";

        // Use cached self-time when we have a real FlameChartNode reference;
        // fall back to ad-hoc calc only for library-source fallback path.
        const selfTime = nodeForSelfTime
          ? getSelfTime(nodeForSelfTime)
          : calculateSelfTime(
              start,
              duration,
              children as FlameChartNode[] | undefined,
            );

        const absoluteStart = sessionStartTimeRef.current + start;
        const absoluteEnd = absoluteStart + duration;

        const startTimeStr = dayjs(absoluteStart).format("HH:mm:ss.SSS");
        const endTimeStr = dayjs(absoluteEnd).format("HH:mm:ss.SSS");

        const tooltipData: { text: string }[] = [{ text: name }];

        if (isPointInTimeEvent) {
          tooltipData.push({
            text: `duration: 0 ${timeUnits} (instant event)`,
          });
          tooltipData.push({ text: `ℹ️ Bar width is for visibility only` });
        } else {
          tooltipData.push({
            text: `duration: ${duration.toFixed(nodeAccuracy)} ${timeUnits}${children?.length ? ` (self ${selfTime.toFixed(nodeAccuracy)} ${timeUnits})` : ""}`,
          });
        }

        tooltipData.push({
          text: `start: ${start.toFixed(nodeAccuracy)} ${timeUnits}`,
        });
        tooltipData.push({ text: `────────────────` });
        tooltipData.push({ text: `🕐 Time: ${startTimeStr}` });

        if (!isPointInTimeEvent) {
          tooltipData.push({ text: `🕐 End: ${endTimeStr}` });
        }

        renderEngine.renderTooltipFromData(tooltipData, mouse);
      };

      const flameChart = new FlameChartLib({
        canvas,
        data: flameChartData,
        settings: {
          options: {
            tooltip: customTooltip,
            timeUnits: "ms",
          },
          styles: {
            main: {
              backgroundColor: "#fafafa",
              blockHeight: 18,
              blockPaddingLeftRight: 4,
              font: "12px Inter, system-ui, sans-serif",
              fontColor: "#333333",
            },
          },
        },
      }) as FlameChartInstance;

      flameChartRef.current = flameChart;

      flameChart.resize(width, height);
      flameChart.render();

      // Track mouse position for custom hit-testing
      let lastMousePos = { x: 0, y: 0 };
      const handleMouseMove = (e: MouseEvent) => {
        const rect = canvas.getBoundingClientRect();
        lastMousePos = { x: e.clientX - rect.left, y: e.clientY - rect.top };
      };
      canvas.addEventListener("mousemove", handleMouseMove);

      // Listen to select event
      flameChart.on("select", (selection: any) => {
        if (!selection) return;

        const selectedNode = selection.node || selection;
        if (!selectedNode) return;

        const currentNodeMap = nodeByIdMapRef.current;

        const chart = flameChartRef.current as any;
        const renderEngine = chart?.renderEngine;

        if (renderEngine) {
          const result = findTopmostNodeAtPosition(
            lastMousePos.x,
            lastMousePos.y,
            renderEngine,
          );

          if (result) {
            updateLibraryHighlight(result.node, result.level);
            onItemClickRef.current?.(result.node);
            return;
          }
        }

        // Fallback to library's selection
        const sourceNode = selectedNode.source || selectedNode;

        if (selectedNode.id && currentNodeMap.has(selectedNode.id)) {
          const originalNode = currentNodeMap.get(selectedNode.id)!;
          onItemClickRef.current?.(originalNode);
          return;
        }

        if (sourceNode.id && currentNodeMap.has(sourceNode.id)) {
          const originalNode = currentNodeMap.get(sourceNode.id)!;
          onItemClickRef.current?.(originalNode);
          return;
        }

        // Fallback: search by start, duration, and type. Iterate the Map's
        // values directly instead of materializing an array.
        const nodeStart = sourceNode.start ?? selectedNode.start ?? 0;
        const nodeDuration = sourceNode.duration ?? selectedNode.duration ?? 0;
        const nodeType = sourceNode.type ?? selectedNode.type;

        for (const node of Array.from(currentNodeMap.values())) {
          if (
            Math.abs(node.start - nodeStart) < 1 &&
            Math.abs(node.duration - nodeDuration) < 1 &&
            node.type === nodeType
          ) {
            onItemClickRef.current?.(node);
            return;
          }
        }
      });

      // rAF-throttled resize observer. Without this, dragging the window
      // edge fires resize events at ~60Hz, each kicking off a full render().
      let resizeRaf = 0;
      const resizeObserver = new ResizeObserver(() => {
        if (resizeRaf) return;
        resizeRaf = requestAnimationFrame(() => {
          resizeRaf = 0;
          if (!flameChartRef.current) return;
          const { width: newWidth, height: newHeight } = resizeCanvas();
          flameChartRef.current.resize(newWidth, newHeight);
          flameChartRef.current.render();
        });
      });
      resizeObserver.observe(container);

      // Stash teardown for unmount-only execution. We deliberately do NOT
      // return this as the effect's cleanup, so it doesn't run when
      // `flameChartData` changes (which is handled by the in-place
      // `setNodes` early return at the top of this effect).
      teardownRef.current = () => {
        if (resizeRaf) {
          cancelAnimationFrame(resizeRaf);
          resizeRaf = 0;
        }
        resizeObserver.disconnect();
        canvas.removeEventListener("mousemove", handleMouseMove);
        const ctx = canvas.getContext("2d");
        if (ctx) ctx.clearRect(0, 0, canvas.width, canvas.height);
        flameChartRef.current = null;
      };
    } catch (error) {
      console.error("Error initializing flame chart:", error);
    }
    // NOTE: `sessionStartTime`, `sessionDuration`, and `contentHeight` are
    // intentionally NOT in this deps array. They are read via refs so prop
    // changes don't tear down and rebuild the FlameChartLib instance.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flameChartData]);

  // Zoom controls
  const handleZoomIn = useCallback(() => {
    if (flameChartRef.current) {
      const newZoom = Math.min(zoom * 1.5, 10);
      setZoom(newZoom);
    }
  }, [zoom]);

  const handleZoomOut = useCallback(() => {
    if (flameChartRef.current) {
      const newZoom = Math.max(zoom / 1.5, 0.5);
      setZoom(newZoom);
    }
  }, [zoom]);

  const handleZoomReset = useCallback(() => {
    if (flameChartRef.current) {
      setZoom(1);
      flameChartRef.current.setZoom(0, sessionDuration);
    }
  }, [sessionDuration]);

  // Track if we've already scrolled to the highlighted trace
  const hasScrolledToHighlight = useRef(false);

  // Scroll to and highlight trace on initial load
  useEffect(() => {
    if (
      !highlightTraceId ||
      !flameChartRef.current ||
      !filteredData.length ||
      hasScrolledToHighlight.current
    ) {
      return;
    }

    // Find a span node matching the trace; prefer root spans for context.
    // Single pass: collect first match of each priority and pick at end.
    let bestSpan: FlameChartNode | null = null;
    let firstMatch: FlameChartNode | null = null;

    const search = (nodes: FlameChartNode[]) => {
      for (const node of nodes) {
        if (node.traceId === highlightTraceId) {
          if (!firstMatch) firstMatch = node;
          if (!bestSpan && node.type === "span") {
            bestSpan = node;
            return; // root-level span: best possible match, stop early
          }
        }
        if (node.children.length > 0) {
          search(node.children);
          if (bestSpan) return;
        }
      }
    };

    search(filteredData);
    const targetNode = bestSpan || firstMatch;

    if (targetNode) {
      // Type assertion needed because TS narrows targetNode to never inside
      // setTimeout closure due to the let-reassignment pattern above.
      const node: FlameChartNode = targetNode;
      const timeoutId = setTimeout(() => {
        if (!flameChartRef.current) return;

        const padding = Math.max(100, node.duration * 0.2);
        const targetStart = Math.max(0, node.start - padding);
        const targetEnd = node.start + node.duration + padding;

        flameChartRef.current.setZoom(targetStart, targetEnd);
        hasScrolledToHighlight.current = true;
      }, 300);

      return () => clearTimeout(timeoutId);
    }
  }, [highlightTraceId, filteredData]);

  useEffect(() => {
    hasScrolledToHighlight.current = false;
  }, [highlightTraceId]);

  // rAF-throttled scroll handler. Without this, every scroll pixel triggers
  // a setState -> re-render of the entire component (legend, minimap, etc).
  const scrollRafRef = useRef(0);
  const handleScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {
    const target = e.currentTarget;
    if (scrollRafRef.current) return;
    scrollRafRef.current = requestAnimationFrame(() => {
      scrollRafRef.current = 0;
      const scrollTop = target.scrollTop;
      const scrollHeight = target.scrollHeight;
      const clientHeight = target.clientHeight;
      setScrollInfo({
        scrollTop,
        scrollHeight,
        clientHeight,
        canScrollUp: scrollTop > 0,
        canScrollDown: scrollTop + clientHeight < scrollHeight - 5,
      });
    });
  }, []);

  useEffect(() => {
    return () => {
      if (scrollRafRef.current) {
        cancelAnimationFrame(scrollRafRef.current);
        scrollRafRef.current = 0;
      }
    };
  }, []);

  if (isLoading) {
    return (
      <Box className={classes.flameChartContainer}>
        <Box className={classes.loadingOverlay}>
          <Loader color="teal" size="lg" />
        </Box>
      </Box>
    );
  }

  if (!data || data.length === 0) {
    return (
      <Box className={classes.emptyState}>
        <IconChartBar size={48} className={classes.emptyIcon} />
        <Text size="lg" fw={500} c="dimmed">
          No timeline data available
        </Text>
        <Text size="sm" c="dimmed" mt="xs">
          No spans or logs found for this session
        </Text>
      </Box>
    );
  }

  return (
    <Box className={classes.flameChartContainer}>
      {/* Controls */}
      <Box className={classes.controls}>
        <Group gap="xs">
          <Tooltip label="Zoom In" position="bottom">
            <ActionIcon
              variant="subtle"
              size="sm"
              className={classes.controlButton}
              onClick={handleZoomIn}
            >
              <IconZoomIn size={16} />
            </ActionIcon>
          </Tooltip>
          <Tooltip label="Zoom Out" position="bottom">
            <ActionIcon
              variant="subtle"
              size="sm"
              className={classes.controlButton}
              onClick={handleZoomOut}
            >
              <IconZoomOut size={16} />
            </ActionIcon>
          </Tooltip>
          <Tooltip label="Reset Zoom" position="bottom">
            <ActionIcon
              variant="subtle"
              size="sm"
              className={classes.controlButton}
              onClick={handleZoomReset}
            >
              <IconZoomReset size={16} />
            </ActionIcon>
          </Tooltip>
        </Group>

        <Box className={classes.zoomInfo}>
          <Text size="xs" c="dimmed">
            Duration: {formatDuration(sessionDuration)}
          </Text>
          {zoom !== 1 && (
            <Text size="xs" c="teal">
              {zoom.toFixed(1)}x
            </Text>
          )}
        </Box>
      </Box>

      {/* Main Content Area with Canvas and Minimap */}
      <Box className={classes.mainContent}>
        <Box
          ref={containerRef}
          className={classes.canvasContainer}
          style={{ maxHeight: contentHeight }}
          onScroll={handleScroll}
        >
          <canvas
            ref={canvasRef}
            className={classes.flameChartCanvas}
            style={{ cursor: "pointer" }}
          />
        </Box>

        {/* Vertical Scroll Minimap */}
        {totalDepth > 5 && (
          <Box className={classes.minimap}>
            <Box className={classes.minimapHeader}>
              <Text size="xs" c="dimmed">
                Depth
              </Text>
            </Box>
            <Box className={classes.minimapTrack}>
              {Array.from({ length: Math.min(totalDepth, 20) }).map((_, i) => (
                <Box
                  key={i}
                  className={classes.minimapBar}
                  style={{
                    opacity: 0.3 + (i / totalDepth) * 0.7,
                    width: `${100 - (i / totalDepth) * 50}%`,
                  }}
                />
              ))}
              {scrollInfo.scrollHeight > scrollInfo.clientHeight && (
                <Box
                  className={classes.minimapViewport}
                  style={{
                    top: `${(scrollInfo.scrollTop / scrollInfo.scrollHeight) * 100}%`,
                    height: `${(scrollInfo.clientHeight / scrollInfo.scrollHeight) * 100}%`,
                  }}
                />
              )}
            </Box>
            <Box className={classes.minimapFooter}>
              <Text size="xs" c="dimmed">
                {totalDepth} levels
              </Text>
            </Box>
          </Box>
        )}
      </Box>

      {/* Scroll Boundary Indicators */}
      {totalDepth > 5 && (
        <Box className={classes.scrollIndicators}>
          {scrollInfo.canScrollUp && (
            <Box className={classes.scrollIndicatorUp}>
              <Group gap={4}>
                <IconArrowUp size={12} />
                <Text size="xs">Scroll up for more</Text>
              </Group>
            </Box>
          )}
          {scrollInfo.canScrollDown && (
            <Box className={classes.scrollIndicatorDown}>
              <Group gap={4}>
                <Text size="xs">Scroll down for more</Text>
                <IconArrowDown size={12} />
              </Group>
            </Box>
          )}
          {!scrollInfo.canScrollUp &&
            !scrollInfo.canScrollDown &&
            scrollInfo.scrollHeight > 0 && (
              <Box className={classes.scrollIndicatorEnd}>
                <Text size="xs">✓ All content visible</Text>
              </Box>
            )}
        </Box>
      )}

      {/* Legend - Click to filter, Shift+Click for exclusive selection,
          Double-click to reset */}
      <Box className={classes.legend}>
        <Tooltip label="Double-click any filter to reset all" position="top">
          <Text
            size="xs"
            c="dimmed"
            className={classes.legendLabel}
            onDoubleClick={resetFilters}
            style={{ cursor: "pointer" }}
          >
            Filter:
          </Text>
        </Tooltip>
        {legendItems.map((item) => {
          const isActive = activeFilters.has(item.key);
          return (
            <Tooltip
              key={item.key}
              label={isActive ? "Click to hide" : "Click to show"}
              position="top"
            >
              <Box
                className={`${classes.legendItem} ${isActive ? classes.legendItemActive : classes.legendItemInactive}`}
                onClick={(e) => {
                  if (e.shiftKey) {
                    selectOnlyFilter(item.key);
                  } else {
                    toggleFilter(item.key);
                  }
                }}
                onDoubleClick={resetFilters}
                style={{ cursor: "pointer" }}
              >
                <Box
                  className={classes.legendColor}
                  style={{
                    backgroundColor: item.color,
                    opacity: isActive ? 1 : 0.3,
                  }}
                />
                <Text
                  size="xs"
                  style={{
                    opacity: isActive ? 1 : 0.5,
                    textDecoration: isActive ? "none" : "line-through",
                  }}
                >
                  {item.label}
                </Text>
              </Box>
            </Tooltip>
          );
        })}
        {activeFilters.size < legendItems.length && (
          <Text
            size="xs"
            c="teal"
            className={classes.legendReset}
            onClick={resetFilters}
            style={{ cursor: "pointer", marginLeft: 8 }}
          >
            Reset
          </Text>
        )}
      </Box>
    </Box>
  );
}
