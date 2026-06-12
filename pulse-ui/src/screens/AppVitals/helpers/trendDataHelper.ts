// Helper function to generate trend data based on time range and filters
export const generateTrendData = (
  issue: any,
  startTime: string,
  endTime: string,
  trendView: string,
  filters: any,
) => {
  // If no time range specified, use default 7 days
  const end = endTime ? new Date(endTime) : new Date();
  const start = startTime
    ? new Date(startTime)
    : new Date(end.getTime() - 7 * 24 * 60 * 60 * 1000);

  // Calculate time difference
  const diffMs = end.getTime() - start.getTime();
  const diffHours = diffMs / (1000 * 60 * 60);
  const diffDays = diffMs / (1000 * 60 * 60 * 24);

  // Determine granularity and number of data points
  let numPoints: number;
  let bucketSize: number; // in milliseconds
  let dateFormat: Intl.DateTimeFormatOptions;

  if (diffHours <= 1) {
    // 1 hour - show every 5 minutes
    numPoints = 12;
    bucketSize = 5 * 60 * 1000;
    dateFormat = { hour: "2-digit", minute: "2-digit" };
  } else if (diffHours <= 6) {
    // 6 hours - show every 30 minutes
    numPoints = 12;
    bucketSize = 30 * 60 * 1000;
    dateFormat = { hour: "2-digit", minute: "2-digit" };
  } else if (diffHours <= 24) {
    // 24 hours - show every hour
    numPoints = 24;
    bucketSize = 60 * 60 * 1000;
    dateFormat = { hour: "2-digit", minute: "2-digit" };
  } else if (diffDays <= 7) {
    // 7 days - show daily
    numPoints = 7;
    bucketSize = 24 * 60 * 60 * 1000;
    dateFormat = { month: "short", day: "numeric" };
  } else if (diffDays <= 30) {
    // 30 days - show daily
    numPoints = 30;
    bucketSize = 24 * 60 * 60 * 1000;
    dateFormat = { month: "short", day: "numeric" };
  } else {
    // More than 30 days - show by week
    numPoints = Math.ceil(diffDays / 7);
    bucketSize = 7 * 24 * 60 * 60 * 1000;
    dateFormat = { month: "short", day: "numeric" };
  }

  const baseOccurrences = issue.occurrences || 100;

  if (trendView === "aggregated") {
    return Array.from({ length: numPoints }, (_, i) => {
      const timestamp = start.getTime() + i * bucketSize;
      const date = new Date(timestamp);
      const variation = Math.random() * 0.3 - 0.15; // -15% to +15% variation
      const count = Math.max(
        1,
        Math.floor(
          baseOccurrences / numPoints +
            (baseOccurrences / numPoints) * variation,
        ),
      );
      return {
        label: date.toLocaleString("en-US", dateFormat),
        count,
      };
    });
  } else if (trendView === "appVersion") {
    const versions = ["2.1.0", "2.2.0", "2.3.0", "2.4.0"];
    return Array.from({ length: numPoints }, (_, i) => {
      const timestamp = start.getTime() + i * bucketSize;
      const date = new Date(timestamp);
      const point: any = {
        label: date.toLocaleString("en-US", dateFormat),
      };
      versions.forEach((version) => {
        const variation = Math.random() * 0.4 - 0.2;
        point[version] = Math.max(
          0,
          Math.floor(
            baseOccurrences / (numPoints * 4) +
              (baseOccurrences / (numPoints * 4)) * variation,
          ),
        );
      });
      return point;
    });
  } else if (trendView === "os") {
    const osVersions = ["Android 13", "Android 12", "Android 11", "Android 10"];
    return Array.from({ length: numPoints }, (_, i) => {
      const timestamp = start.getTime() + i * bucketSize;
      const date = new Date(timestamp);
      const point: any = {
        label: date.toLocaleString("en-US", dateFormat),
      };
      osVersions.forEach((os) => {
        const variation = Math.random() * 0.4 - 0.2;
        point[os] = Math.max(
          0,
          Math.floor(
            baseOccurrences / (numPoints * 4) +
              (baseOccurrences / (numPoints * 4)) * variation,
          ),
        );
      });
      return point;
    });
  }

  return [];
};

export const getXAxisInterval = (
  startTime: string,
  endTime: string,
): number => {
  if (!startTime || !endTime) return 0;

  const diffMs = new Date(endTime).getTime() - new Date(startTime).getTime();
  const diffHours = diffMs / (1000 * 60 * 60);
  const diffDays = diffMs / (1000 * 60 * 60 * 24);

  if (diffHours <= 1 || diffHours <= 6) return 1;
  if (diffHours <= 24) return 2;
  if (diffDays <= 7) return 0;
  if (diffDays <= 30) return 2;
  return 3;
};
