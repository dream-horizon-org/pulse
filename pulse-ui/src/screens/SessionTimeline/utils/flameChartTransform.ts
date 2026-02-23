/**
 * Flame Chart Transform
 * 
 * Main transformation logic for converting API data to flame chart format.
 * Transforms trace, log, and exception data from ClickHouse into a hierarchical
 * tree structure suitable for flame chart visualization.
 */

import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import type { AttributeValue } from "../../../types/attributes";

dayjs.extend(utc);

/**
 * Types for flame chart data structures
 */
export interface FlameChartItem {
  id: string;
  name: string;
  start: number; // Relative time from session start in ms
  duration: number; // Duration in ms
  type: "span" | "log" | "exception" | "orphan-span" | "orphan-log";
  color?: string;
  traceId: string;
  spanId: string;
  parentSpanId?: string;
  metadata?: Record<string, AttributeValue>;
}

export interface FlameChartNode {
  id: string;
  name: string;
  start: number;
  duration: number;
  type: "span" | "log" | "exception" | "orphan-span" | "orphan-log";
  color?: string;
  traceId: string;
  spanId: string;
  parentSpanId?: string;
  children: FlameChartNode[];
  metadata?: Record<string, AttributeValue>;
}

export interface FlameChartData {
  name: string;
  start: number;
  duration: number;
  type?: string;
  children?: FlameChartData[];
  color?: string;
}

export interface TransformResult {
  flameChartData: FlameChartNode[];
  sessionStartTime: number;
  sessionDuration: number;
  itemsMap: Map<string, FlameChartItem>;
  orphanItems: FlameChartItem[];
  totalDepth: number; // Maximum depth of the tree for minimap
}

export interface FilterOptions {
  showSpans: boolean;
  showLogs: boolean;
  showExceptions: boolean;
}

interface RawTraceRow {
  traceId: string;
  spanId: string;
  parentSpanId: string;
  spanName: string;
  spanKind: string;
  serviceName: string;
  timestamp: string;
  duration: number;
  statusCode: string;
  spanType: string;
  pulseType: string;
}

interface RawLogRow {
  traceId: string;
  spanId: string;
  timestamp: string;
  severityText: string;
  severityNumber: number;
  body: string;
  eventName: string;
  pulseType: string;
  serviceName: string;
  scopeName: string;
  logAttributesJson: string;
  resourceAttributesJson: string;
}

interface RawExceptionRow {
  timestamp: string;
  eventName: string;
  title: string;
  exceptionMessage: string;
  exceptionType: string;
  screenName: string;
  traceId: string;
  spanId: string;
  groupId: string;
  pulseType: string;
}

/**
 * Parse ClickHouse result row to typed object
 */
function parseTraceRow(
  fields: string[],
  row: (string | number | null)[]
): RawTraceRow {
  const getField = (name: string): string | number | null => {
    const index = fields.findIndex((f) => f.toLowerCase() === name.toLowerCase());
    return index >= 0 ? row[index] : null;
  };

  return {
    traceId: String(getField("traceid") || ""),
    spanId: String(getField("spanid") || ""),
    parentSpanId: String(getField("parentspanid") || ""),
    spanName: String(getField("spanname") || ""),
    spanKind: String(getField("spankind") || ""),
    serviceName: String(getField("servicename") || ""),
    timestamp: String(getField("timestamp") || ""),
    duration: Number(getField("duration") || 0),
    statusCode: String(getField("statuscode") || ""),
    spanType: String(getField("spantype") || ""),
    pulseType: String(getField("pulsetype") || ""),
  };
}

function parseLogRow(
  fields: string[],
  row: (string | number | null)[]
): RawLogRow {
  const getField = (name: string): string | number | null => {
    const index = fields.findIndex((f) => f.toLowerCase() === name.toLowerCase());
    return index >= 0 ? row[index] : null;
  };

  return {
    traceId: String(getField("traceid") || ""),
    spanId: String(getField("spanid") || ""),
    timestamp: String(getField("timestamp") || ""),
    severityText: String(getField("severitytext") || ""),
    severityNumber: Number(getField("severitynumber") || 0),
    body: String(getField("body") || ""),
    eventName: String(getField("eventname") || ""),
    pulseType: String(getField("pulsetype") || ""),
    serviceName: String(getField("servicename") || ""),
    scopeName: String(getField("scopename") || ""),
    logAttributesJson: String(getField("logattributesjson") || "{}"),
    resourceAttributesJson: String(getField("resourceattributesjson") || "{}"),
  };
}

function parseExceptionRow(
  fields: string[],
  row: (string | number | null)[]
): RawExceptionRow {
  const getField = (name: string): string | number | null => {
    const index = fields.findIndex((f) => f.toLowerCase() === name.toLowerCase());
    return index >= 0 ? row[index] : null;
  };

  return {
    timestamp: String(getField("timestamp") || ""),
    eventName: String(getField("eventname") || ""),
    title: String(getField("title") || ""),
    exceptionMessage: String(getField("exceptionmessage") || ""),
    exceptionType: String(getField("exceptiontype") || ""),
    screenName: String(getField("screenname") || ""),
    traceId: String(getField("traceid") || ""),
    spanId: String(getField("spanid") || ""),
    groupId: String(getField("groupid") || ""),
    pulseType: String(getField("pulsetype") || ""),
  };
}

/**
 * Get color for exception based on event type
 */
export function getExceptionColor(eventName: string): string {
  const name = eventName.toLowerCase();
  if (name.includes("crash")) {
    return "#dc2626"; // Red for crashes
  }
  if (name.includes("anr")) {
    return "#ea580c"; // Orange for ANRs
  }
  if (name.includes("non_fatal") || name.includes("nonfatal")) {
    return "#ca8a04"; // Yellow for non-fatal
  }
  return "#ef4444"; // Default red for exceptions
}

/**
 * Get color based on PulseType - generates consistent colors for filtering
 * Uses a deterministic hash-based approach so the same pulseType always gets the same color
 */
export function getColorForPulseType(pulseType: string): string {
  if (!pulseType) {
    return "#64b5f6"; // Default blue for missing pulseType
  }
  
  // Generate a deterministic color based on the type string
  let hash = 0;
  for (let i = 0; i < pulseType.length; i++) {
    hash = pulseType.charCodeAt(i) + ((hash << 5) - hash);
  }
  const hue = Math.abs(hash % 360);
  const saturation = 55 + (hash % 20); // 55-75%
  const lightness = 45 + (hash % 15);  // 45-60%
  return `hsl(${hue}, ${saturation}%, ${lightness}%)`;
}

/**
 * Format pulseType for display (capitalize, replace dots/underscores/hyphens with spaces)
 * e.g., "app.jank.frozen" -> "App Jank Frozen"
 */
export function formatPulseType(pulseType: string | undefined): string {
  if (!pulseType) return "Unknown";
  return pulseType
    .split(/[._-]/)
    .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(" ");
}

/**
 * Get color based on span type or severity (legacy - for backwards compatibility)
 */
export function getSpanColor(spanType: string, statusCode: string): string {
  // Error states
  if (statusCode?.toLowerCase() === "error") {
    return "#ff4d4d"; // Red for errors
  }

  // Span type colors
  const typeColors: Record<string, string> = {
    "http": "#42a5f5",
    "network": "#42a5f5",
    "database": "#66bb6a",
    "db": "#66bb6a",
    "internal": "#ab47bc",
    "screen": "#ffa726",
    "activity": "#ffa726",
    "interaction": "#26c6da",
    "crash": "#ff4d4d",
    "anr": "#ff9800",
    "exception": "#ff4d4d",
  };

  const normalizedType = spanType.toLowerCase();
  for (const [key, color] of Object.entries(typeColors)) {
    if (normalizedType.includes(key)) {
      return color;
    }
  }

  return "#64b5f6"; // Default blue
}

export function getLogColor(severityText: string): string {
  const severity = severityText.toLowerCase();
  if (severity.includes("error") || severity.includes("fatal")) {
    return "#ff4d4d";
  }
  if (severity.includes("warn")) {
    return "#ffa726";
  }
  if (severity.includes("info")) {
    return "#66bb6a";
  }
  if (severity.includes("debug")) {
    return "#78909c";
  }
  return "#90a4ae";
}

/**
 * Check if a span ID represents an empty/null parent
 */
function isEmptySpanId(spanId: string): boolean {
  if (!spanId) return true;
  // ClickHouse FixedString(16) empty value
  const trimmed = spanId.trim();
  return (
    trimmed === "" ||
    trimmed === "\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000" ||
    /^0+$/.test(trimmed)
  );
}

/**
 * Transform traces, logs, and exceptions data into flame chart format
 */
export function transformToFlameChart(
  tracesData: { fields: string[]; rows: (string | number | null)[][] } | null,
  logsData: { fields: string[]; rows: (string | number | null)[][] } | null,
  exceptionsData?: { fields: string[]; rows: (string | number | null)[][] } | null,
  filters?: FilterOptions
): TransformResult {
  // Default filters if not provided
  const activeFilters: FilterOptions = filters || {
    showSpans: true,
    showLogs: true,
    showExceptions: true,
  };
  const items: FlameChartItem[] = [];
  const itemsMap = new Map<string, FlameChartItem>();
  const spanIdToItem = new Map<string, FlameChartItem>();

  // Parse traces (only if filter enabled)
  if (activeFilters.showSpans && tracesData?.rows) {
    for (const row of tracesData.rows) {
      const parsed = parseTraceRow(tracesData.fields, row);
      const ts = dayjs.utc(parsed.timestamp);
      
      const item: FlameChartItem = {
        id: `span-${parsed.traceId}-${parsed.spanId}`,
        name: parsed.spanName || "Unknown Span",
        start: ts.valueOf(),
        duration: parsed.duration / 1_000_000, // Convert nanoseconds to ms
        type: "span",
        color: getColorForPulseType(parsed.pulseType),
        traceId: parsed.traceId,
        spanId: parsed.spanId,
        parentSpanId: isEmptySpanId(parsed.parentSpanId) ? undefined : parsed.parentSpanId,
        metadata: {
          spanKind: parsed.spanKind,
          serviceName: parsed.serviceName,
          statusCode: parsed.statusCode,
          spanType: parsed.spanType,
          pulseType: parsed.pulseType,
          timestamp: parsed.timestamp,
        },
      };

      items.push(item);
      itemsMap.set(item.id, item);
      spanIdToItem.set(parsed.spanId, item);
    }
  }

  // Parse logs - these are point-in-time events (duration = 0)
  if (activeFilters.showLogs && logsData?.rows) {
    for (const row of logsData.rows) {
      const parsed = parseLogRow(logsData.fields, row);
      const ts = dayjs.utc(parsed.timestamp);

      // Parse JSON attributes
      let logAttributes: Record<string, AttributeValue> = {};
      let resourceAttributes: Record<string, AttributeValue> = {};
      try {
        logAttributes = JSON.parse(parsed.logAttributesJson || "{}");
      } catch {
        // Ignore parse errors
      }
      try {
        resourceAttributes = JSON.parse(parsed.resourceAttributesJson || "{}");
      } catch {
        // Ignore parse errors
      }

      const item: FlameChartItem = {
        id: `log-${parsed.traceId}-${parsed.spanId}-${ts.valueOf()}`,
        name: formatPulseType(parsed.pulseType) || parsed.eventName || "Log",
        start: ts.valueOf(),
        duration: 0, // Logs are point-in-time events
        type: "log",
        color: getColorForPulseType(parsed.pulseType),
        traceId: parsed.traceId,
        spanId: parsed.spanId,
        parentSpanId: undefined, // Logs don't have parent span ID directly
        metadata: {
          severityText: parsed.severityText,
          severityNumber: parsed.severityNumber,
          body: parsed.body,
          eventName: parsed.eventName,
          pulseType: parsed.pulseType,
          timestamp: parsed.timestamp,
          serviceName: parsed.serviceName,
          scopeName: parsed.scopeName,
          // Store parsed attributes for display in sidebar
          logAttributes,
          resourceAttributes,
        },
      };

      items.push(item);
      itemsMap.set(item.id, item);
    }
  }

  // Parse exceptions (crashes, ANRs, non-fatal) - these are point-in-time events
  if (activeFilters.showExceptions && exceptionsData?.rows) {
    for (const row of exceptionsData.rows) {
      const parsed = parseExceptionRow(exceptionsData.fields, row);
      const ts = dayjs.utc(parsed.timestamp);

      // Use pulseType as primary display name, with emoji prefix based on event type
      const pulseTypeDisplay = formatPulseType(parsed.pulseType);
      let displayName = pulseTypeDisplay !== "Unknown" ? pulseTypeDisplay : (parsed.title || parsed.exceptionType || "Exception");
      
      // Add emoji prefix based on exception severity
      if (parsed.eventName?.includes("crash")) {
        displayName = `🔴 ${displayName}`;
      } else if (parsed.eventName?.includes("anr")) {
        displayName = `🟠 ${displayName}`;
      } else if (parsed.eventName?.includes("non_fatal")) {
        displayName = `🟡 ${displayName}`;
      }

      const item: FlameChartItem = {
        id: `exception-${parsed.traceId}-${parsed.groupId}-${ts.valueOf()}`,
        name: displayName,
        start: ts.valueOf(),
        duration: 0, // Exceptions are point-in-time events
        type: "exception",
        color: getColorForPulseType(parsed.pulseType),
        traceId: parsed.traceId,
        spanId: parsed.spanId,
        parentSpanId: undefined,
        metadata: {
          eventName: parsed.eventName,
          title: parsed.title,
          exceptionMessage: parsed.exceptionMessage,
          exceptionType: parsed.exceptionType,
          screenName: parsed.screenName,
          groupId: parsed.groupId,
          pulseType: parsed.pulseType,
          timestamp: parsed.timestamp,
        },
      };

      items.push(item);
      itemsMap.set(item.id, item);
    }
  }

  if (items.length === 0) {
    return {
      flameChartData: [],
      sessionStartTime: Date.now(),
      sessionDuration: 0,
      itemsMap,
      orphanItems: [],
      totalDepth: 0,
    };
  }

  // Find session time bounds
  let minTime = Infinity;
  let maxTime = -Infinity;

  for (const item of items) {
    if (item.start < minTime) minTime = item.start;
    const endTime = item.start + item.duration;
    if (endTime > maxTime) maxTime = endTime;
  }

  const sessionStartTime = minTime;
  const sessionDuration = maxTime - minTime;

  // Convert absolute times to relative times
  for (const item of items) {
    item.start = item.start - sessionStartTime;
  }

  // Build hierarchical tree structure
  const rootNodes: FlameChartNode[] = [];
  const orphanItems: FlameChartItem[] = [];
  const processedIds = new Set<string>();

  // First pass: identify root spans (no parent or parent not in set)
  const spanItems = items.filter((item) => item.type === "span");
  const logItems = items.filter((item) => item.type === "log");

  // Group spans by trace
  const spansByTrace = new Map<string, FlameChartItem[]>();
  for (const item of spanItems) {
    const existing = spansByTrace.get(item.traceId) || [];
    existing.push(item);
    spansByTrace.set(item.traceId, existing);
  }

  // Build tree for each trace
  for (const [, traceSpans] of Array.from(spansByTrace.entries())) {
    const traceRoots: FlameChartNode[] = [];
    const nodeMap = new Map<string, FlameChartNode>();

    // Create nodes for all spans
    for (const span of traceSpans) {
      const node: FlameChartNode = {
        ...span,
        children: [],
      };
      nodeMap.set(span.spanId, node);
    }

    // Build parent-child relationships
    for (const span of traceSpans) {
      const node = nodeMap.get(span.spanId);
      if (!node) continue;

      if (span.parentSpanId) {
        const parentNode = nodeMap.get(span.parentSpanId);
        if (parentNode) {
          parentNode.children.push(node);
          processedIds.add(span.id);
        } else {
          // Orphan span - parent not found
          node.type = "orphan-span";
          traceRoots.push(node);
          orphanItems.push(span);
        }
      } else {
        // Root span
        traceRoots.push(node);
      }
      processedIds.add(span.id);
    }

    // Sort children by start time
    const sortChildren = (node: FlameChartNode) => {
      node.children.sort((a, b) => a.start - b.start);
      for (const child of node.children) {
        sortChildren(child);
      }
    };

    for (const root of traceRoots) {
      sortChildren(root);
      rootNodes.push(root);
    }
  }

  // Handle logs - try to attach to their parent span or add as orphans
  for (const log of logItems) {
    const parentSpan = spanIdToItem.get(log.spanId);
    if (parentSpan) {
      // Find the node for this span and add log as a child
      const findAndAttach = (nodes: FlameChartNode[]): boolean => {
        for (const node of nodes) {
          if (node.spanId === log.spanId) {
            node.children.push({
              ...log,
              children: [],
            });
            return true;
          }
          if (findAndAttach(node.children)) return true;
        }
        return false;
      };
      
      if (!findAndAttach(rootNodes)) {
        // Couldn't attach, treat as orphan but keep original color
        const orphanNode: FlameChartNode = {
          ...log,
          type: "orphan-log",
          // Keep the original pulseType color instead of grey
          children: [],
        };
        rootNodes.push(orphanNode);
        orphanItems.push(log);
      }
    } else {
      // Orphan log - no parent span found, but keep original color
      const orphanNode: FlameChartNode = {
        ...log,
        type: "orphan-log",
        // Keep the original pulseType color instead of grey
        children: [],
      };
      rootNodes.push(orphanNode);
      orphanItems.push(log);
    }
  }

  // Handle exceptions - add as root nodes (they are point-in-time events)
  const exceptionItems = items.filter((item) => item.type === "exception");
  for (const exception of exceptionItems) {
    const exceptionNode: FlameChartNode = {
      ...exception,
      children: [],
    };
    
    // Try to attach to parent span if spanId exists
    if (exception.spanId && spanIdToItem.has(exception.spanId)) {
      const findAndAttach = (nodes: FlameChartNode[]): boolean => {
        for (const node of nodes) {
          if (node.spanId === exception.spanId) {
            node.children.push(exceptionNode);
            return true;
          }
          if (findAndAttach(node.children)) return true;
        }
        return false;
      };
      
      if (!findAndAttach(rootNodes)) {
        // Couldn't attach to parent span, add as root node
        rootNodes.push(exceptionNode);
      }
    } else {
      // No parent span, add as root node
      rootNodes.push(exceptionNode);
    }
  }

  // Sort root nodes by start time
  rootNodes.sort((a, b) => a.start - b.start);

  // Calculate total depth of the tree
  const calculateDepth = (nodes: FlameChartNode[], currentDepth: number): number => {
    let maxDepth = currentDepth;
    for (const node of nodes) {
      if (node.children.length > 0) {
        const childDepth = calculateDepth(node.children, currentDepth + 1);
        if (childDepth > maxDepth) maxDepth = childDepth;
      }
    }
    return maxDepth;
  };
  const totalDepth = calculateDepth(rootNodes, rootNodes.length > 0 ? 1 : 0);

  return {
    flameChartData: rootNodes,
    sessionStartTime,
    sessionDuration,
    itemsMap,
    orphanItems,
    totalDepth,
  };
}

/**
 * Extended flame chart data with our custom properties
 */
export interface ExtendedFlameChartData extends FlameChartData {
  id?: string;
  traceId?: string;
  spanId?: string;
  parentSpanId?: string;
  metadata?: Record<string, AttributeValue>;
}

/**
 * Calculate minimum visible duration for point-in-time events (logs, exceptions)
 * This ensures they are visible on the timeline regardless of session length
 */
function calculateMinVisibleDuration(sessionDuration: number): number {
  // Use 0.5% of session duration, with a minimum of 50ms and maximum of 500ms
  const percentageBased = sessionDuration * 0.005;
  return Math.max(50, Math.min(percentageBased, 500));
}

/**
 * Check if a node type is a point-in-time event (no real duration)
 */
function isPointInTimeEvent(type: string): boolean {
  return type === "log" || type === "exception" || type === "orphan-log";
}

/**
 * Offset overlapping point-in-time events so they render side-by-side instead of on top of each other.
 * Groups events by start time (within a threshold) and offsets them.
 * @param nodes - Array of nodes at the same level
 * @param minVisibleDuration - The visual duration used for point-in-time events
 * @returns Map of node id to offset value
 */
function calculateOverlapOffsets(
  nodes: FlameChartNode[],
  minVisibleDuration: number
): Map<string, number> {
  const offsets = new Map<string, number>();
  
  // Only process point-in-time events
  const pointEvents = nodes.filter(n => isPointInTimeEvent(n.type) || n.duration === 0);
  
  if (pointEvents.length <= 1) {
    return offsets;
  }
  
  // Sort by start time
  const sorted = [...pointEvents].sort((a, b) => a.start - b.start);
  
  // Group events that would visually overlap
  // Two events overlap if their visual bars would intersect
  // (start2 < start1 + minVisibleDuration)
  let currentEnd = -Infinity;
  let overlapCount = 0;
  
  for (const event of sorted) {
    if (event.start < currentEnd) {
      // This event overlaps with previous ones - offset it
      overlapCount++;
      // Offset by a fraction of the minVisibleDuration so events stack horizontally
      offsets.set(event.id, overlapCount * minVisibleDuration * 0.6);
    } else {
      // No overlap, reset counter
      overlapCount = 0;
    }
    // Update the end of the current "group"
    currentEnd = Math.max(currentEnd, event.start + minVisibleDuration);
  }
  
  return offsets;
}

/**
 * Convert FlameChartNode tree to flame-chart-js data format
 * Note: We include all our custom properties so they come back with the select event
 * @param nodes - The nodes to convert
 * @param sessionDuration - Total session duration in ms, used to calculate minimum visible width for point events
 */
export function toFlameChartJsFormat(
  nodes: FlameChartNode[],
  sessionDuration: number = 10000
): ExtendedFlameChartData[] {
  const minVisibleDuration = calculateMinVisibleDuration(sessionDuration);
  
  // Convert a list of nodes, handling overlap at this level
  const convertNodeList = (nodeList: FlameChartNode[]): ExtendedFlameChartData[] => {
    // Calculate offsets for overlapping point-in-time events at this level
    const overlapOffsets = calculateOverlapOffsets(nodeList, minVisibleDuration);
    
    return nodeList.map((node) => {
      // For point-in-time events (logs, exceptions), use minimum visible duration
      // For spans with actual duration, use actual duration (with small minimum for very short spans)
      let displayDuration: number;
      if (isPointInTimeEvent(node.type) || node.duration === 0) {
        displayDuration = minVisibleDuration;
      } else {
        displayDuration = Math.max(node.duration, 1);
      }
      
      // Apply overlap offset if needed
      const offset = overlapOffsets.get(node.id) || 0;
      const displayStart = node.start + offset;
      
      return {
        // Required flame-chart-js fields
        name: node.name,
        start: displayStart,
        duration: displayDuration,
        type: node.type,
        color: node.color,
        children: node.children.length > 0 
          ? convertNodeList(node.children)
          : undefined,
        // Our custom fields - library will pass these back in select event
        id: node.id,
        traceId: node.traceId,
        spanId: node.spanId,
        parentSpanId: node.parentSpanId,
        metadata: node.metadata,
      };
    });
  };
  
  return convertNodeList(nodes);
}

/**
 * Find item by traceId for highlighting
 */
export function findItemByTraceId(
  nodes: FlameChartNode[],
  traceId: string
): FlameChartNode | null {
  for (const node of nodes) {
    if (node.traceId === traceId) {
      return node;
    }
    const found = findItemByTraceId(node.children, traceId);
    if (found) return found;
  }
  return null;
}

/**
 * Find all items belonging to a trace
 */
export function findAllItemsForTrace(
  nodes: FlameChartNode[],
  traceId: string
): FlameChartNode[] {
  const result: FlameChartNode[] = [];
  
  const traverse = (nodeList: FlameChartNode[]) => {
    for (const node of nodeList) {
      if (node.traceId === traceId) {
        result.push(node);
      }
      traverse(node.children);
    }
  };
  
  traverse(nodes);
  return result;
}

