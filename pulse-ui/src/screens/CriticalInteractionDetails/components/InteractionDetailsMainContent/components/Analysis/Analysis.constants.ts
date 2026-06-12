export const ANALYSIS_ERROR_MESSAGES = {
  SOMETHING_WENT_WRONG: "Something went wrong",
  NETWORK_ISSUES: {
    ERROR: "Something went wrong",
    EMPTY: "No network issues data available",
  },
  LATENCY: {
    ERROR: "Something went wrong",
    EMPTY: "No latency analysis data available",
  },
  DEVICE_PERFORMANCE: {
    ERROR: "Something went wrong",
    EMPTY: "No device performance data available",
  },
  OS_PERFORMANCE: {
    ERROR: "Something went wrong",
    EMPTY: "No OS performance data available",
  },
  REGIONAL_INSIGHTS: {
    ERROR: "Something went wrong",
    EMPTY: "No regional insights data available",
  },
  PLATFORM_INSIGHTS: {
    ERROR: "Something went wrong",
    EMPTY: "No platform insights data available",
  },
  RELEASE_PERFORMANCE: {
    ERROR: "Something went wrong",
    EMPTY: "No release performance data available",
  },
} as const;

export const ANALYSIS_LOADING_MESSAGES = {
  NETWORK_ISSUES: "Fetching network issues analysis details",
  LATENCY: "Fetching latency analysis details",
  DEVICE_PERFORMANCE: "Fetching device performance analysis details",
  OS_PERFORMANCE: "Fetching OS performance analysis details",
  REGIONAL_INSIGHTS: "Fetching regional insights analysis details",
  PLATFORM_INSIGHTS: "Fetching platform insights analysis details",
  RELEASE_PERFORMANCE: "Fetching release performance analysis details",
} as const;
