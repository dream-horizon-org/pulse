/**
 * Duration formatting utilities for displaying time values in human-readable format
 */

/**
 * Formats seconds into a human-readable duration string
 * 
 * @param seconds - The duration in seconds
 * @returns A human-readable string like "1h 23m", "21m 16s", "45.3s", "500ms"
 * 
 * @example
 * formatDuration(0.5)    // "500ms"
 * formatDuration(45.3)   // "45.3s"
 * formatDuration(75)     // "1m 15s"
 * formatDuration(1276.5) // "21m 17s"
 * formatDuration(3723)   // "1h 2m"
 * formatDuration(7200)   // "2h"
 */
export function formatDuration(seconds: number): string {
  if (seconds === 0 || isNaN(seconds)) {
    return "0s";
  }

  // For very small values, show in milliseconds
  if (seconds < 1) {
    return `${Math.round(seconds * 1000)}ms`;
  }

  // For values less than 60 seconds, show as seconds
  if (seconds < 60) {
    return `${seconds.toFixed(1)}s`;
  }

  // For values >= 60 seconds, convert to minutes and seconds
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainingSeconds = Math.round(seconds % 60);

  if (hours > 0) {
    // Show hours and minutes (skip seconds for readability)
    if (minutes > 0) {
      return `${hours}h ${minutes}m`;
    }
    return `${hours}h`;
  }

  // Show minutes and seconds
  if (remainingSeconds > 0) {
    return `${minutes}m ${remainingSeconds}s`;
  }
  return `${minutes}m`;
}

/**
 * Formats seconds for compact display (e.g., Y-axis labels on charts)
 * 
 * @param seconds - The duration in seconds
 * @returns A compact string like "30s", "5m", "1.5h"
 * 
 * @example
 * formatDurationCompact(30)   // "30s"
 * formatDurationCompact(120)  // "2m"
 * formatDurationCompact(3600) // "1h"
 * formatDurationCompact(5400) // "1.5h"
 */
export function formatDurationCompact(seconds: number): string {
  if (seconds === 0) return "0";
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.round(seconds / 60)}m`;
  return `${(seconds / 3600).toFixed(1)}h`;
}

/**
 * Formats nanoseconds into a human-readable duration string
 * Useful for data coming directly from the database (which stores duration in nanoseconds)
 * 
 * @param nanoseconds - The duration in nanoseconds
 * @returns A human-readable string like "1h 23m", "21m 16s", "45.3s", "500ms"
 * 
 * @example
 * formatDurationFromNanoseconds(1_000_000_000) // "1.0s"
 * formatDurationFromNanoseconds(60_000_000_000) // "1m"
 */
export function formatDurationFromNanoseconds(nanoseconds: number): string {
  const seconds = nanoseconds / 1_000_000_000;
  return formatDuration(seconds);
}

/**
 * Formats milliseconds into a human-readable duration string
 * 
 * @param milliseconds - The duration in milliseconds
 * @returns A human-readable string like "1h 23m", "21m 16s", "45.3s", "500ms"
 * 
 * @example
 * formatDurationFromMilliseconds(1000) // "1.0s"
 * formatDurationFromMilliseconds(60000) // "1m"
 */
export function formatDurationFromMilliseconds(milliseconds: number): string {
  const seconds = milliseconds / 1000;
  return formatDuration(seconds);
}

