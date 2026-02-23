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
import { FlameChartNode, toFlameChartJsFormat, getColorForPulseType, formatPulseType } from "../../utils/flameChartTransform";
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
  // Function to check if a node matches this legend category
  matches: (node: FlameChartNode) => boolean;
}


// Extract unique pulse types from data using metadata.pulseType
function extractPulseTypes(nodes: FlameChartNode[]): Set<string> {
  const types = new Set<string>();
  
  const traverse = (nodeList: FlameChartNode[]) => {
    for (const node of nodeList) {
      // Get pulseType directly from metadata
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
  
  // Sort types alphabetically for consistent ordering
  const sortedTypes = Array.from(pulseTypes).sort();
  
  for (const pulseType of sortedTypes) {
    const key = pulseType.toLowerCase().replace(/[^a-z0-9]/g, '_');
    const color = getColorForPulseType(pulseType);
    const label = formatPulseType(pulseType);
    
    items.push({
      key,
      label,
      color,
      matches: (node: FlameChartNode) => {
        // Simple direct match on pulseType from metadata
        return node.metadata?.pulseType === pulseType;
      },
    });
  }
  
  return items;
}

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
  
  // Extract unique pulse types from data and generate legend items
  const legendItems = useMemo(() => {
    if (!data || data.length === 0) return [];
    const pulseTypes = extractPulseTypes(data);
    return generateLegendItems(pulseTypes);
  }, [data]);

  // State for active legend filters - all enabled by default
  const [activeFilters, setActiveFilters] = useState<Set<string>>(new Set());

  // Update active filters when legend items change (e.g., new data loaded)
  useEffect(() => {
    setActiveFilters(new Set(legendItems.map(item => item.key)));
  }, [legendItems]);

  // Toggle a filter on/off
  const toggleFilter = useCallback((key: string) => {
    setActiveFilters(prev => {
      const next = new Set(prev);
      if (next.has(key)) {
        // Don't allow deselecting all filters - keep at least one
        if (next.size > 1) {
          next.delete(key);
        }
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
    setActiveFilters(new Set(legendItems.map(item => item.key)));
  }, [legendItems]);

  // Filter nodes based on active legend filters
  // Non-matching parent nodes are kept but dimmed (as containers for matching children)
  const filterNodes = useCallback((nodes: FlameChartNode[]): FlameChartNode[] => {
    const filterNode = (node: FlameChartNode): FlameChartNode | null => {
      // Check if this node matches any active filter
      const matchesActiveFilter = legendItems.some(
        item => activeFilters.has(item.key) && item.matches(node)
      );

      // Filter children recursively
      const filteredChildren = node.children
        .map(filterNode)
        .filter((child): child is FlameChartNode => child !== null);

      // If node matches, include it with filtered children (keep original color)
      if (matchesActiveFilter) {
        return { ...node, children: filteredChildren };
      }

      // If node doesn't match but has matching children, include it as a dimmed container
      // (This keeps the hierarchy intact for child nodes)
      if (filteredChildren.length > 0) {
        // Dim the container node by making it grey with low opacity
        return { 
          ...node, 
          children: filteredChildren,
          color: '#e0e0e0', // Light grey for non-matching containers
        };
      }

      // Node doesn't match and has no matching children - exclude it
      return null;
    };

    return nodes
      .map(filterNode)
      .filter((node): node is FlameChartNode => node !== null);
  }, [activeFilters, legendItems]);

  // Apply filters to data
  const filteredData = useMemo(() => {
    if (!data || data.length === 0) return [];
    return filterNodes(data);
  }, [data, filterNodes]);
  
  // Calculate content height based on total depth
  const contentHeight = useMemo(() => {
    // Each level is BLOCK_HEIGHT pixels, plus time grid
    const calculatedHeight = TIME_GRID_HEIGHT + (totalDepth * BLOCK_HEIGHT) + 50; // 50px buffer
    return Math.max(400, calculatedHeight);
  }, [totalDepth]);
  
  // Create a Map of node ID -> FlameChartNode for fast lookup (uses filtered data)
  const nodeByIdMap = useMemo(() => {
    const map = new Map<string, FlameChartNode>();
    
    const traverse = (nodes: FlameChartNode[]) => {
      for (const node of nodes) {
        // Use node.id as primary key (unique)
        map.set(node.id, node);
        traverse(node.children);
      }
    };
    
    if (filteredData) {
      traverse(filteredData);
    }
    
    return map;
  }, [filteredData]);

  // Create a flat list of all nodes with their level for custom hit-testing
  // Uses start, end (duration), and type to identify nodes (uses filtered data)
  const flatNodesList = useMemo(() => {
    const result: FlatNodeInfo[] = [];
    
    const traverse = (nodes: FlameChartNode[], level: number) => {
      for (const node of nodes) {
        result.push({
          node,
          level,
          start: node.start,
          end: node.start + node.duration,
          type: node.type,
        });
        traverse(node.children, level + 1);
      }
    };
    
    if (filteredData) {
      traverse(filteredData, 0);
    }
    
    return result;
  }, [filteredData]);

  // Ref to store flat nodes for use in event handlers
  const flatNodesRef = useRef(flatNodesList);
  useEffect(() => {
    flatNodesRef.current = flatNodesList;
  }, [flatNodesList]);

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
      /**
       * Find the topmost (latest-starting) overlapping node at a given position.
       * 
       * The flame-chart-js library has a bug where it uses Array.find() which returns
       * the first match (earlier-starting node) instead of the topmost overlapping one.
       * 
       * We identify nodes by: start time, end time (start + duration), and type.
       * Returns both the node and the clicked level for accurate highlight matching.
       * 
       * Edge cases handled:
       * - Overlapping siblings at the same level: returns the latest-starting one
       * - Click outside any node: returns null
       * - Zoom/pan: uses library's positionX and zoom values
       * - Plugin offset: accounts for other plugins above the flame chart
       */
      const findTopmostNodeAtPosition = (
        mouseX: number, 
        mouseY: number,
        renderEngine: any
      ): { node: FlameChartNode; level: number } | null => {
        const chart = flameChartRef.current as any;
        if (!chart?.plugins) return null;
        
        // Find the flameChartPlugin to get its vertical position offset
        const flameChartPlugin = chart.plugins.find(
          (p: any) => p.name === 'flameChartPlugin'
        );
        
        if (!flameChartPlugin) return null;
        
        // Get the plugin's Y position (offset from top of canvas)
        const pluginYOffset = flameChartPlugin.renderEngine?.position || 0;
        
        const positionX = renderEngine.positionX || 0;
        const zoom = renderEngine.zoom || 1;
        const blockHeight = renderEngine.blockHeight || BLOCK_HEIGHT;
        
        // Validate zoom to prevent division issues
        if (zoom <= 0) return null;
        
        // Convert mouse X to time position
        const clickTime = positionX + (mouseX / zoom);
        
        // Calculate which level (row) was clicked
        // Account for the plugin's Y offset (there may be other plugins above the flame chart)
        const adjustedMouseY = mouseY - pluginYOffset;
        const clickLevel = Math.floor(adjustedMouseY / blockHeight);
        
        // Invalid level (clicked above the flame chart area)
        if (clickLevel < 0) return null;
        
        // Find all nodes at this position (matching level and time range)
        const flatNodes = flatNodesRef.current;
        const matchingNodes = flatNodes.filter(({ level, start, end }) => {
          return level === clickLevel && clickTime >= start && clickTime <= end;
        });
        
        if (matchingNodes.length === 0) return null;
        if (matchingNodes.length === 1) return { node: matchingNodes[0].node, level: clickLevel };
        
        // Multiple overlapping nodes at the same level - determine which is "on top"
        // Sort priority:
        // 1. Later start time (later-starting items are rendered on top)
        // 2. Shorter duration (if same start, shorter/more specific items are on top)
        // 3. Node name (stable tiebreaker for identical timing)
        matchingNodes.sort((a, b) => {
          // Primary: later start time first
          if (b.start !== a.start) {
            return b.start - a.start;
          }
          
          // Secondary: shorter duration first (more specific item)
          const aDuration = a.end - a.start;
          const bDuration = b.end - b.start;
          if (aDuration !== bDuration) {
            return aDuration - bDuration;
          }
          
          // Tertiary: alphabetical by name (stable tiebreaker)
          return a.node.name.localeCompare(b.node.name);
        });
        
        return { node: matchingNodes[0].node, level: clickLevel };
      };

      /**
       * Update the library's internal selection to show highlight on the correct node.
       * We find the matching node in the library's flatTree using start, duration, type, AND level.
       * 
       * This is needed because the library's internal hit-testing uses Array.find() which
       * returns the first match, causing the highlight to appear on the wrong node.
       */
      const updateLibraryHighlight = (node: FlameChartNode, clickedLevel: number) => {
        const chart = flameChartRef.current as any;
        if (!chart?.plugins) return;
        
        // Find the flameChartPlugin
        const flameChartPlugin = chart.plugins.find(
          (p: any) => p.name === 'flameChartPlugin' && p.flatTree
        );
        
        if (!flameChartPlugin?.flatTree) return;
        
        // Find the matching node in the library's flatTree using start, duration, type, AND level
        // The level is critical to distinguish between nodes with the same timing at different depths
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
          // Set the library's selectedRegion to the correct node
          flameChartPlugin.selectedRegion = {
            type: 'node',
            data: libraryNode,
          };
          
          // Re-render to show the highlight
          if (chart.renderEngine?.render) {
            chart.renderEngine.render();
          }
        }
      };

      // Custom tooltip - uses our hit-testing to show correct tooltip for overlapping nodes
      const customTooltip = (hoveredRegion: any, renderEngine: any, mouse: any) => {
        if (!hoveredRegion) return;
        
        // Try to find the correct topmost node
        const result = findTopmostNodeAtPosition(mouse.x, mouse.y, renderEngine);
        
        let nodeData: { start: number; duration: number; name: string; children?: any[]; type?: string };
        
        if (result) {
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
        
        // Check if this is a point-in-time event (log, exception) with zero actual duration
        const isPointInTimeEvent = type === "log" || type === "exception" || type === "orphan-log";
        
        // Calculate self time by merging overlapping child intervals
        // This correctly handles cases where children overlap each other
        const calculateSelfTime = (parentStart: number, parentDuration: number, childNodes: any[] | undefined): number => {
          if (!childNodes || childNodes.length === 0) return parentDuration;
          
          const parentEnd = parentStart + parentDuration;
          
          // Get child intervals and clip to parent bounds
          const intervals: [number, number][] = childNodes
            .filter((child: any) => child.duration > 0)
            .map((child: any) => {
              const childStart = Math.max(child.start, parentStart);
              const childEnd = Math.min(child.start + child.duration, parentEnd);
              return [childStart, childEnd] as [number, number];
            })
            .filter(([s, e]) => e > s); // Remove invalid intervals after clipping
          
          if (intervals.length === 0) return parentDuration;
          
          // Sort intervals by start time
          intervals.sort((a, b) => a[0] - b[0]);
          
          // Merge overlapping intervals
          const merged: [number, number][] = [intervals[0]];
          for (let i = 1; i < intervals.length; i++) {
            const last = merged[merged.length - 1];
            const current = intervals[i];
            
            if (current[0] <= last[1]) {
              // Overlapping - extend the last interval
              last[1] = Math.max(last[1], current[1]);
            } else {
              // No overlap - add as new interval
              merged.push(current);
            }
          }
          
          // Calculate total time covered by children (merged)
          const totalChildTime = merged.reduce((acc, [s, e]) => acc + (e - s), 0);
          
          // Ensure self time is never negative (safety clamp)
          return Math.max(0, parentDuration - totalChildTime);
        };
        
        const selfTime = calculateSelfTime(start, duration, children);
        
        const absoluteStart = sessionStartTime + start;
        const absoluteEnd = absoluteStart + duration;
        
        const startTimeStr = dayjs(absoluteStart).format("HH:mm:ss.SSS");
        const endTimeStr = dayjs(absoluteEnd).format("HH:mm:ss.SSS");
        
        // Build tooltip data
        const tooltipData: { text: string }[] = [
          { text: name },
        ];
        
        // For point-in-time events, show actual duration as 0
        if (isPointInTimeEvent) {
          tooltipData.push({ text: `duration: 0 ${timeUnits} (instant event)` });
          tooltipData.push({ text: `ℹ️ Bar width is for visibility only` });
        } else {
          tooltipData.push({ 
            text: `duration: ${duration.toFixed(nodeAccuracy)} ${timeUnits}${children?.length ? ` (self ${selfTime.toFixed(nodeAccuracy)} ${timeUnits})` : ""}` 
          });
        }
        
        tooltipData.push({ text: `start: ${start.toFixed(nodeAccuracy)} ${timeUnits}` });
        tooltipData.push({ text: `────────────────` });
        tooltipData.push({ text: `🕐 Time: ${startTimeStr}` });
        
        // Only show end time for non-instant events
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

      // Resize and render
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
        
        // Try custom hit-testing first to find the correct overlapping node
        const chart = flameChartRef.current as any;
        const renderEngine = chart?.renderEngine;
        
        if (renderEngine) {
          const result = findTopmostNodeAtPosition(
            lastMousePos.x, 
            lastMousePos.y, 
            renderEngine
          );
          
          if (result) {
            // Update the library's highlight to show on the correct node
            // Pass the clicked level to ensure we match the exact node at that level
            updateLibraryHighlight(result.node, result.level);
            
            if (onItemClickRef.current) {
              onItemClickRef.current(result.node);
            }
            return;
          }
        }
        
        // Fallback to library's selection
        const sourceNode = selectedNode.source || selectedNode;
        
        // Try to look up by ID first
        if (selectedNode.id && currentNodeMap.has(selectedNode.id)) {
          const originalNode = currentNodeMap.get(selectedNode.id)!;
          if (onItemClickRef.current) {
            onItemClickRef.current(originalNode);
          }
          return;
        }
        
        if (sourceNode.id && currentNodeMap.has(sourceNode.id)) {
          const originalNode = currentNodeMap.get(sourceNode.id)!;
          if (onItemClickRef.current) {
            onItemClickRef.current(originalNode);
          }
          return;
        }
        
        // Fallback: search by start, duration, and type
        const nodeStart = sourceNode.start ?? selectedNode.start ?? 0;
        const nodeDuration = sourceNode.duration ?? selectedNode.duration ?? 0;
        const nodeType = sourceNode.type ?? selectedNode.type;
        
        let foundNode: FlameChartNode | undefined;
        const allNodes = Array.from(currentNodeMap.values());
        for (const node of allNodes) {
          if (
            Math.abs(node.start - nodeStart) < 1 &&
            Math.abs(node.duration - nodeDuration) < 1 &&
            node.type === nodeType
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
        canvas.removeEventListener("mousemove", handleMouseMove);
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

  // Track if we've already scrolled to the highlighted trace (to only do it once on initial load)
  const hasScrolledToHighlight = useRef(false);

  // Scroll to and highlight trace on initial load
  useEffect(() => {
    // Only run once on initial load when we have data and a chart instance
    if (
      !highlightTraceId ||
      !flameChartRef.current ||
      !filteredData.length ||
      hasScrolledToHighlight.current
    ) {
      return;
    }

    // Find the root span node with matching traceId (prefer root spans for better context)
    const findRootNodeForTrace = (nodes: FlameChartNode[]): FlameChartNode | null => {
      // First, look for root-level nodes with matching traceId
      for (const node of nodes) {
        if (node.traceId === highlightTraceId && node.type === "span") {
          return node;
        }
      }
      // Fallback: search in children if not found at root level
      for (const node of nodes) {
        if (node.traceId === highlightTraceId) {
          return node;
        }
        const found = findRootNodeForTrace(node.children);
        if (found) return found;
      }
      return null;
    };

    const targetNode = findRootNodeForTrace(filteredData);
    if (targetNode) {
      // Small delay to ensure flame chart is fully rendered
      const timeoutId = setTimeout(() => {
        if (!flameChartRef.current) return;

        // Calculate zoom range with padding for context
        const padding = Math.max(100, targetNode.duration * 0.2); // 20% padding or minimum 100ms
        const targetStart = Math.max(0, targetNode.start - padding);
        const targetEnd = targetNode.start + targetNode.duration + padding;

        // Zoom to the trace
        flameChartRef.current.setZoom(targetStart, targetEnd);

        hasScrolledToHighlight.current = true;
      }, 300); // 300ms delay to ensure chart is rendered

      return () => clearTimeout(timeoutId);
    }
  }, [highlightTraceId, filteredData]);

  // Reset the scroll flag when highlightTraceId changes (e.g., navigating to a different trace)
  useEffect(() => {
    hasScrolledToHighlight.current = false;
  }, [highlightTraceId]);

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
            key={Array.from(activeFilters).sort().join(',')}
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

      {/* Legend - Click to filter, Shift+Click for exclusive selection, Double-click to reset */}
      <Box className={classes.legend}>
        <Tooltip label="Double-click any filter to reset all" position="top">
          <Text 
            size="xs" 
            c="dimmed" 
            className={classes.legendLabel}
            onDoubleClick={resetFilters}
            style={{ cursor: 'pointer' }}
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
                    // Shift+click for exclusive selection
                    selectOnlyFilter(item.key);
                  } else {
                    toggleFilter(item.key);
                  }
                }}
                onDoubleClick={resetFilters}
                style={{ cursor: 'pointer' }}
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
                    textDecoration: isActive ? 'none' : 'line-through',
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
            style={{ cursor: 'pointer', marginLeft: 8 }}
          >
            Reset
          </Text>
        )}
      </Box>
    </Box>
  );
}
