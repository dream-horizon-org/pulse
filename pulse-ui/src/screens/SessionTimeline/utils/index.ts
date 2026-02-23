/**
 * Session Timeline Utils - Main Entry Point
 * 
 * This module provides utilities for the Session Timeline feature:
 * - Type definitions for flame chart data structures
 * - Color utilities for consistent styling
 * - Formatting utilities for display
 * - Parsers for API response data
 * - Transform functions for building flame chart trees
 * 
 * For most use cases, import directly from this index:
 * @example import { FlameChartNode, formatDuration } from "../utils";
 */

// ============================================================================
// Type Definitions (from types.ts)
// ============================================================================
export type {
  FlameChartItem,
  FlameChartNode,
  FlameChartData,
  ExtendedFlameChartData,
  TransformResult,
  RawSpanRow,
  RawLogRow,
  RawExceptionRow,
  ActiveFilters,
  TracesApiResponse,
  LogsApiResponse,
  ExceptionsApiResponse,
} from "./types";

// ============================================================================
// Color Utilities (from colors.ts)
// ============================================================================
export {
  getColorForPulseType,
  getSpanColor,
  getLogColor,
  getExceptionColor,
  getOrphanColor,
} from "./colors";

// ============================================================================
// Formatting Utilities (from formatters.ts)
// ============================================================================
export {
  formatDuration,
  formatTimestamp,
  formatTimeOnly,
  formatPulseType,
  formatSpanName,
  formatBytes,
} from "./formatters";

// ============================================================================
// Parsers (from parsers.ts)
// ============================================================================
export {
  parseTraceRow,
  parseLogRow,
  parseExceptionRow,
  parseJsonSafe,
  isEmptySpanId,
} from "./parsers";

// ============================================================================
// Transform Functions (from flameChartTransform.ts)
// Note: flameChartTransform.ts is the legacy main file, still contains
// the core transformation logic. Will be refactored incrementally.
// ============================================================================
export {
  // Types (re-exported for backward compatibility)
  type FlameChartItem as LegacyFlameChartItem,
  type FlameChartNode as LegacyFlameChartNode,
  type FlameChartData as LegacyFlameChartData,
  type TransformResult as LegacyTransformResult,
  type FilterOptions,
  type ExtendedFlameChartData as LegacyExtendedFlameChartData,
  // Functions
  transformToFlameChart,
  toFlameChartJsFormat,
  findItemByTraceId,
  findAllItemsForTrace,
  // Color functions (re-exported for backward compatibility)
  getColorForPulseType as legacyGetColorForPulseType,
  getSpanColor as legacyGetSpanColor,
  getLogColor as legacyGetLogColor,
  getExceptionColor as legacyGetExceptionColor,
  // Format functions (re-exported for backward compatibility)
  formatPulseType as legacyFormatPulseType,
} from "./flameChartTransform";
