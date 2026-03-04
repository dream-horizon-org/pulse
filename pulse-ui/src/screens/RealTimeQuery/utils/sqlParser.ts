/**
 * Extract time range from SQL WHERE clause
 * Parses TIMESTAMP filters to determine start and end times
 */

/**
 * Extract start and end timestamps from SQL WHERE clause
 * @param sql SQL query string
 * @returns Object with startTime and endTime, or null if not found
 */
export function extractTimeRangeFromSql(
  sql: string
): { startTime: Date; endTime: Date } | null {
  if (!sql) return null;

  const sqlUpper = sql.toUpperCase();
  const whereMatch = sqlUpper.match(/WHERE\s+(.+?)(?:\s+GROUP\s+BY|\s+ORDER\s+BY|\s+LIMIT|$)/i);
  if (!whereMatch) return null;

  // Pattern to match TIMESTAMP literals: TIMESTAMP 'YYYY-MM-DD HH:MM:SS'
  const timestampPattern = /TIMESTAMP\s+['"]([\d\s\-:]+)['"]/gi;
  
  const timestamps: Date[] = [];
  let match;
  
  while ((match = timestampPattern.exec(sql)) !== null) {
    try {
      const timestampStr = match[1].trim();
      const date = new Date(timestampStr);
      if (!isNaN(date.getTime())) {
        timestamps.push(date);
      }
    } catch (e) {
      // Invalid timestamp, skip
    }
  }

  if (timestamps.length === 0) return null;

  // Sort timestamps to find earliest and latest
  timestamps.sort((a, b) => a.getTime() - b.getTime());
  
  const startTime = timestamps[0];
  const endTime = timestamps.length > 1 ? timestamps[timestamps.length - 1] : startTime;

  // If only one timestamp found, check for comparison operators to determine range
  if (timestamps.length === 1) {
    const singleTimestamp = timestamps[0];
    // Find the timestamp string in SQL to check context
    const timestampStr = sql.match(/TIMESTAMP\s+['"]([\d\s\-:]+)['"]/i)?.[1];
    if (timestampStr) {
      const timestampIndex = sql.indexOf(timestampStr);
      const beforeTimestamp = sql.substring(Math.max(0, timestampIndex - 20), timestampIndex);
      const afterTimestamp = sql.substring(timestampIndex + timestampStr.length, timestampIndex + timestampStr.length + 20);
      
      // Check for >= or > before timestamp (start time)
      if (beforeTimestamp.match(/>=|>/)) {
        return {
          startTime: singleTimestamp,
          endTime: new Date(singleTimestamp.getTime() + 24 * 60 * 60 * 1000), // Default to +24 hours
        };
      }
      
      // Check for <= or < after timestamp (end time)
      if (afterTimestamp.match(/<=|</)) {
        return {
          startTime: new Date(singleTimestamp.getTime() - 24 * 60 * 60 * 1000), // Default to -24 hours
          endTime: singleTimestamp,
        };
      }
    }
    
    // Default: use single timestamp as start, +24 hours as end
    return {
      startTime: singleTimestamp,
      endTime: new Date(singleTimestamp.getTime() + 24 * 60 * 60 * 1000),
    };
  }

  return { startTime, endTime };
}

/**
 * Generate partition list from time range
 * @param startTime Start date
 * @param endTime End date
 * @returns Array of partition strings in format "YYYY-MM-DD HH:00"
 */
export function generatePartitionsFromTimeRange(
  startTime: Date,
  endTime: Date
): string[] {
  const partitions: string[] = [];
  const current = new Date(startTime);
  
  // Round down to the hour
  current.setMinutes(0, 0, 0);
  
  while (current <= endTime) {
    const year = current.getUTCFullYear();
    const month = String(current.getUTCMonth() + 1).padStart(2, "0");
    const day = String(current.getUTCDate()).padStart(2, "0");
    const hour = String(current.getUTCHours()).padStart(2, "0");
    
    partitions.push(`${year}-${month}-${day} ${hour}:00`);
    
    // Move to next hour
    current.setUTCHours(current.getUTCHours() + 1);
    
    // Safety limit to prevent infinite loops
    if (partitions.length > 1000) break;
  }
  
  return partitions;
}

