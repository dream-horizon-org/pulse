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
import { FlameChartNode, toFlameChartJsFormat } from "../../utils/flameChartTransform";
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

// Extended type for flame chart instance
type FlameChartInstance = FlameChartLib & {
  renderEngine?: { clear?: () => void };
  setNodes?: (nodes: any) => void;
};

// Color legend for the flame chart
const LEGEND_ITEMS = [
  { label: "HTTP/Network", color: "#42a5f5" },
  { label: "Database", color: "#66bb6a" },
  { label: "Screen/Activity", color: "#ffa726" },
  { label: "Interaction", color: "#26c6da" },
  { label: "Error", color: "#ff4d4d" },
  { label: "Log", color: "#90a4ae" },
  { label: "Crash", color: "#dc2626" },
  { label: "ANR", color: "#ea580c" },
  { label: "Non-Fatal", color: "#ca8a04" },
  { label: "Orphan", color: "#9e9e9e" },
];

/**
 * Format duration for display
 */
function formatDuration(ms: number): string {
  if (ms < 1) {
    return `${(ms * 1000).toFixed(0)}µs`;
  }
  if (ms < 1000) {
    return `${ms.toFixed(2)}ms`;
  }
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
  
  // Calculate content height based on total depth
  const contentHeight = useMemo(() => {
    // Each level is BLOCK_HEIGHT pixels, plus time grid
    const calculatedHeight = TIME_GRID_HEIGHT + (totalDepth * BLOCK_HEIGHT) + 50; // 50px buffer
    return Math.max(400, calculatedHeight);
  }, [totalDepth]);
  
  // Create a Map of node ID -> FlameChartNode for fast lookup
  const nodeByIdMap = useMemo(() => {
    const map = new Map<string, FlameChartNode>();
    
    const traverse = (nodes: FlameChartNode[]) => {
      for (const node of nodes) {
        // Use node.id as primary key (unique)
        map.set(node.id, node);
        traverse(node.children);
      }
    };
    
    if (data) {
      traverse(data);
    }
    
    return map;
  }, [data]);

  // Stable callback ref for onItemClick to avoid re-creating the flame chart
  const onItemClickRef = useRef(onItemClick);
  useEffect(() => {
    onItemClickRef.current = onItemClick;
  }, [onItemClick]);

  // Transform data to flame-chart-js format (memoized)
  const flameChartData = useMemo(() => {
    if (!data || data.length === 0) return null;
    return toFlameChartJsFormat(data);
  }, [data]);

  // Keep nodeByIdMap in a ref so select handler always has latest version
  const nodeByIdMapRef = useRef(nodeByIdMap);
  useEffect(() => {
    nodeByIdMapRef.current = nodeByIdMap;
  }, [nodeByIdMap]);

  // Initialize flame chart when data is available
  useEffect(() => {
    if (!containerRef.current || !canvasRef.current || !flameChartData || flameChartData.length === 0) {
      return;
    }

    const canvas = canvasRef.current;
    const container = containerRef.current;

    // Set canvas size - use calculated content height to avoid extra whitespace
    const resizeCanvas = () => {
      const rect = container.getBoundingClientRect();
      const width = rect.width || 800;
      // Use the smaller of container height or content height to avoid empty space
      const containerHeight = rect.height || 400;
      const height = Math.min(containerHeight, contentHeight);
      canvas.width = width;
      canvas.height = Math.max(400, height);
      return { width, height: Math.max(400, height) };
    };

    const { width, height } = resizeCanvas();

    // Clean up existing instance
    if (flameChartRef.current) {
      const ctx = canvas.getContext("2d");
      if (ctx) {
        ctx.clearRect(0, 0, canvas.width, canvas.height);
      }
      flameChartRef.current = null;
    }

    // Create flame chart instance with data
    try {
      // Custom tooltip function to show start/end timestamps
      const customTooltip = (hoveredRegion: any, renderEngine: any, mouse: any) => {
        if (!hoveredRegion || !hoveredRegion.data?.source) return;
        
        const { start, duration, name, children } = hoveredRegion.data.source;
        const timeUnits = renderEngine.getTimeUnits();
        const nodeAccuracy = renderEngine.getAccuracy() + 2;
        
        // Calculate self time (duration minus children's total duration)
        const selfTime = duration - (children ? children.reduce((acc: number, child: any) => acc + child.duration, 0) : 0);
        
        // Calculate absolute timestamps (session start + relative offset)
        const absoluteStart = sessionStartTime + start;
        const absoluteEnd = absoluteStart + duration;
        
        // Format to local time with milliseconds
        const startTimeStr = dayjs(absoluteStart).format("HH:mm:ss.SSS");
        const endTimeStr = dayjs(absoluteEnd).format("HH:mm:ss.SSS");
        
        // Build tooltip lines
        const tooltipData = [
          { text: name },
          { text: `duration: ${duration.toFixed(nodeAccuracy)} ${timeUnits}${children?.length ? ` (self ${selfTime.toFixed(nodeAccuracy)} ${timeUnits})` : ""}` },
          { text: `start: ${start.toFixed(nodeAccuracy)} ${timeUnits}` },
          { text: `────────────────` },
          { text: `🕐 Start: ${startTimeStr}` },
          { text: `🕐 End: ${endTimeStr}` },
        ];
        
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

      // Resize and render
      flameChart.resize(width, height);
      flameChart.render();

      // Listen to select event - this is the ONLY click handler we need
      // Use ref to always get the latest nodeByIdMap
      flameChart.on("select", (selection: any) => {
        if (!selection) return;
        
        // The library returns { node: {...}, type: 'flame-chart-node' }
        // The actual node data is inside selection.node
        const selectedNode = selection.node || selection;
        
        if (!selectedNode) return;
        
        const currentNodeMap = nodeByIdMapRef.current;
        
        // Try to look up by ID first (our custom property)
        if (selectedNode.id && currentNodeMap.has(selectedNode.id)) {
          const originalNode = currentNodeMap.get(selectedNode.id)!;
          if (onItemClickRef.current) {
            onItemClickRef.current(originalNode);
          }
          return;
        }
        
        // Check if there's a source property with our data
        const sourceNode = selectedNode.source || selectedNode;
        if (sourceNode.id && currentNodeMap.has(sourceNode.id)) {
          const originalNode = currentNodeMap.get(sourceNode.id)!;
          if (onItemClickRef.current) {
            onItemClickRef.current(originalNode);
          }
          return;
        }
        
        // Fallback: search through all nodes for matching name/start/duration
        const nodeName = sourceNode.name || selectedNode.name;
        const nodeStart = sourceNode.start ?? selectedNode.start ?? 0;
        const nodeDuration = sourceNode.duration ?? selectedNode.duration ?? 0;
        
        let foundNode: FlameChartNode | undefined;
        const allNodes = Array.from(currentNodeMap.values());
        for (const node of allNodes) {
          if (
            node.name === nodeName &&
            Math.abs(node.start - nodeStart) < 1 &&
            Math.abs(node.duration - nodeDuration) < 1
          ) {
            foundNode = node;
            break;
          }
        }
        
        if (foundNode) {
          if (onItemClickRef.current) {
            onItemClickRef.current(foundNode);
          }
        }
      });

      // Handle resize
      const resizeObserver = new ResizeObserver(() => {
        const { width: newWidth, height: newHeight } = resizeCanvas();
        flameChart.resize(newWidth, newHeight);
        flameChart.render();
      });
      resizeObserver.observe(container);

      return () => {
        resizeObserver.disconnect();
        const ctx = canvas.getContext("2d");
        if (ctx) {
          ctx.clearRect(0, 0, canvas.width, canvas.height);
        }
        flameChartRef.current = null;
      };
    } catch (error) {
      console.error("Error initializing flame chart:", error);
    }
  }, [flameChartData, sessionStartTime, sessionDuration, contentHeight]);

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

  // Scroll to highlighted trace
  useEffect(() => {
    if (!highlightTraceId || !flameChartRef.current || !data.length) return;

    // Find node with matching traceId
    const findNode = (nodes: FlameChartNode[]): FlameChartNode | null => {
      for (const node of nodes) {
        if (node.traceId === highlightTraceId) {
          return node;
        }
        const found = findNode(node.children);
        if (found) return found;
      }
      return null;
    };

    const targetNode = findNode(data);
    if (targetNode) {
      const targetStart = Math.max(0, targetNode.start - 100);
      const targetEnd = targetNode.start + targetNode.duration + 100;
      flameChartRef.current.setZoom(targetStart, targetEnd);
    }
  }, [highlightTraceId, data]);

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
        {/* Canvas - no manual click handler, using library's select event */}
        <Box
          ref={containerRef}
          className={classes.canvasContainer}
          style={{ maxHeight: contentHeight }}
          onScroll={(e) => {
            const target = e.currentTarget;
            const scrollTop = target.scrollTop;
            const scrollHeight = target.scrollHeight;
            const clientHeight = target.clientHeight;
            const canScrollUp = scrollTop > 0;
            const canScrollDown = scrollTop + clientHeight < scrollHeight - 5; // 5px threshold
            
            setScrollInfo({
              scrollTop,
              scrollHeight,
              clientHeight,
              canScrollUp,
              canScrollDown,
            });
          }}
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
              <Text size="xs" c="dimmed">Depth</Text>
            </Box>
            <Box className={classes.minimapTrack}>
              {/* Depth indicator bars */}
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
              {/* Viewport indicator */}
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
              <Text size="xs" c="dimmed">{totalDepth} levels</Text>
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
          {!scrollInfo.canScrollUp && !scrollInfo.canScrollDown && scrollInfo.scrollHeight > 0 && (
            <Box className={classes.scrollIndicatorEnd}>
              <Text size="xs">✓ All content visible</Text>
            </Box>
          )}
        </Box>
      )}

      {/* Legend */}
      <Box className={classes.legend}>
        {LEGEND_ITEMS.map((item) => (
          <Box key={item.label} className={classes.legendItem}>
            <Box
              className={classes.legendColor}
              style={{ backgroundColor: item.color }}
            />
            <Text size="xs">{item.label}</Text>
          </Box>
        ))}
      </Box>
    </Box>
  );
}
