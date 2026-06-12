/**
 * Formatting utilities for the Session Timeline
 * Provides consistent formatting for durations, timestamps, and display names
 */

import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);

/**
 * Format duration for display with appropriate units
 * 
 * @param ms - Duration in milliseconds
 * @returns Formatted string with units (µs, ms, or s)
 */
export function formatDuration(ms: number): string {
  if (ms < 1) {
    return `${(ms * 1000).toFixed(0)}µs`;
  }
  if (ms < 1000) {
    return `${ms.toFixed(2)}ms`;
  }
  return `${(ms / 1000).toFixed(2)}s`;
}

/**
 * Format timestamp to human readable with milliseconds in local timezone
 * 
 * @param timestamp - ISO timestamp string (UTC)
 * @returns Formatted local time string
 */
export function formatTimestamp(timestamp: string | undefined): string {
  if (!timestamp) return "—";
  const dt = dayjs.utc(timestamp).local();
  if (!dt.isValid()) return "—";
  return dt.format("MMM D, YYYY HH:mm:ss.SSS");
}

/**
 * Format timestamp for compact display (time only)
 * 
 * @param timestamp - ISO timestamp string (UTC)
 * @returns Formatted local time string (time only)
 */
export function formatTimeOnly(timestamp: string | undefined): string {
  if (!timestamp) return "—";
  const dt = dayjs.utc(timestamp).local();
  if (!dt.isValid()) return "—";
  return dt.format("HH:mm:ss.SSS");
}

/**
 * Format pulseType for display
 * Capitalizes words and replaces dots/underscores/hyphens with spaces
 * 
 * @example "app.jank.frozen" -> "App Jank Frozen"
 * @param pulseType - The pulse type string
 * @returns Formatted display string
 */
export function formatPulseType(pulseType: string | undefined): string {
  if (!pulseType) return "Unknown";
  return pulseType
    .split(/[._-]/)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(" ");
}

/**
 * Format span name for display (truncate if too long)
 * 
 * @param name - The span name
 * @param maxLength - Maximum length before truncation
 * @returns Formatted span name
 */
export function formatSpanName(name: string, maxLength: number = 50): string {
  if (!name) return "Unknown Span";
  if (name.length <= maxLength) return name;
  return `${name.substring(0, maxLength)}...`;
}

/**
 * Format byte size for display
 * 
 * @param bytes - Size in bytes
 * @returns Formatted string with appropriate unit
 */
export function formatBytes(bytes: number): string {
  if (bytes === 0) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
}

// ============================================================================
// Timeline Position Utilities (used by TimelineView components)
// ============================================================================

/**
 * Format time in milliseconds to human-readable format
 * Used by timeline axis and span row components
 * 
 * @param ms - Time in milliseconds
 * @returns Formatted time string
 */
export function formatTimeMs(ms: number): string {
  if (ms < 1000) {
    return `${ms.toFixed(0)}ms`;
  }
  if (ms < 60000) {
    return `${(ms / 1000).toFixed(1)}s`;
  }
  const minutes = Math.floor(ms / 60000);
  const seconds = ((ms % 60000) / 1000).toFixed(1);
  return `${minutes}m ${seconds}s`;
}

/**
 * Calculate the horizontal position of an event on the timeline
 * 
 * @param eventStart - Start time of the event (relative to session start)
 * @param sessionDuration - Total session duration
 * @returns Position as percentage (0-100)
 */
export function calculateEventPosition(
  eventStart: number,
  sessionDuration: number
): number {
  if (sessionDuration <= 0) return 0;
  return (eventStart / sessionDuration) * 100;
}

/**
 * Calculate the width of an event on the timeline
 * 
 * @param eventDuration - Duration of the event (can be undefined for instant events)
 * @param sessionDuration - Total session duration
 * @param minWidth - Minimum width percentage
 * @returns Width as percentage
 */
export function calculateEventWidth(
  eventDuration: number | undefined,
  sessionDuration: number,
  minWidth: number = 0.5
): number {
  if (sessionDuration <= 0) return minWidth;
  if (!eventDuration) return minWidth;
  const width = (eventDuration / sessionDuration) * 100;
  return Math.max(width, minWidth);
}
