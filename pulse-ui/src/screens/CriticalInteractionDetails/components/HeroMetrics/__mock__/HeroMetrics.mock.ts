import {
  IconBug,
  IconHourglassEmpty,
  IconCloudOff,
  IconAlertCircle,
  IconPlugOff,
  IconWifiOff,
  IconSnowflake,
  IconGauge,
  IconShieldCheck,
  IconNetwork,
} from "@tabler/icons-react";
import { MetricGroupData } from "../HeroMetrics.interface";

/**
 * Mock data for Hero Metrics
 * Organized into logical sections: Stability, Network Health, and Performance
 */
export const mockMetricGroups: MetricGroupData[] = [
  {
    title: "Interaction Health",
    description: "Critical errors and performance issues",
    icon: IconShieldCheck,
    iconColor: "violet",
    metrics: [
      {
        title: "Crashes",
        value: 12,
        trend: "+0.8%",
        color: "#c92a2a",
        icon: IconBug,
      },
      {
        title: "ANRs",
        value: 8,
        trend: "-0.5%",
        color: "#f76707",
        icon: IconHourglassEmpty,
      },
      {
        title: "Frozen Frames",
        value: 156,
        trend: "-24",
        color: "#3b82f6",
        icon: IconSnowflake,
      },
      {
        title: "Slow Frames",
        value: 342,
        trend: "+12",
        color: "#8b5cf6",
        icon: IconGauge,
      },
    ],
  },
  {
    title: "Network Health",
    description: "API and connectivity issues",
    icon: IconNetwork,
    iconColor: "yellow",
    metrics: [
      {
        title: "Timeouts",
        value: 18,
        trend: "+1.1%",
        color: "#ff6b6b",
        icon: IconCloudOff,
      },
      {
        title: "HTTP 4xx",
        value: 18,
        trend: "+1.1%",
        color: "#f59e0b",
        icon: IconAlertCircle,
      },
      {
        title: "HTTP 5xx",
        value: 12,
        trend: "-0.8%",
        color: "#dc2626",
        icon: IconAlertCircle,
      },
      {
        title: "Connection Failures",
        value: 8,
        trend: "+0.5%",
        color: "#7c3aed",
        icon: IconPlugOff,
      },
      {
        title: "Network Errors",
        value: 47,
        trend: "-5",
        color: "#eab308",
        icon: IconWifiOff,
      },
    ],
  },
];

/**
 * Mock data with no errors - for testing success states
 */
export const mockMetricGroupsNoErrors: MetricGroupData[] = [
  {
    title: "Interaction Health",
    description: "Critical errors and performance issues",
    icon: IconShieldCheck,
    iconColor: "teal",
    metrics: [
      {
        title: "Crashes",
        value: 0,
        trend: "0%",
        color: "#12b886",
        icon: IconBug,
      },
      {
        title: "ANRs",
        value: 0,
        trend: "0%",
        color: "#12b886",
        icon: IconHourglassEmpty,
      },
      {
        title: "Frozen Frames",
        value: 0,
        trend: "0%",
        color: "#12b886",
        icon: IconSnowflake,
      },
      {
        title: "Slow Frames",
        value: 0,
        trend: "0%",
        color: "#12b886",
        icon: IconGauge,
      },
    ],
  },
  {
    title: "Network Health",
    description: "API and connectivity issues",
    icon: IconNetwork,
    iconColor: "teal",
    metrics: [
      {
        title: "Timeouts",
        value: 0,
        trend: "0%",
        color: "#12b886",
        icon: IconCloudOff,
      },
      {
        title: "HTTP 4xx",
        value: 0,
        trend: "0%",
        color: "#12b886",
        icon: IconAlertCircle,
      },
      {
        title: "HTTP 5xx",
        value: 0,
        trend: "0%",
        color: "#12b886",
        icon: IconAlertCircle,
      },
      {
        title: "Connection Failures",
        value: 0,
        trend: "0%",
        color: "#12b886",
        icon: IconPlugOff,
      },
      {
        title: "Network Errors",
        value: 0,
        trend: "0%",
        color: "#12b886",
        icon: IconWifiOff,
      },
    ],
  },
];

/**
 * Mock data with high error rates - for testing worst case scenarios
 */
export const mockMetricGroupsHighErrors: MetricGroupData[] = [
  {
    title: "Interaction Health",
    description: "Critical errors and performance issues",
    icon: IconShieldCheck,
    iconColor: "red",
    metrics: [
      {
        title: "Crashes",
        value: 245,
        trend: "+45.2%",
        color: "#c92a2a",
        icon: IconBug,
      },
      {
        title: "ANRs",
        value: 189,
        trend: "+32.5%",
        color: "#f76707",
        icon: IconHourglassEmpty,
      },
      {
        title: "Frozen Frames",
        value: 2456,
        trend: "+567",
        color: "#3b82f6",
        icon: IconSnowflake,
      },
      {
        title: "Slow Frames",
        value: 5432,
        trend: "+892",
        color: "#8b5cf6",
        icon: IconGauge,
      },
    ],
  },
  {
    title: "Network Health",
    description: "API and connectivity issues",
    icon: IconNetwork,
    iconColor: "red",
    metrics: [
      {
        title: "Timeouts",
        value: 567,
        trend: "+78.1%",
        color: "#ff6b6b",
        icon: IconCloudOff,
      },
      {
        title: "HTTP 4xx",
        value: 892,
        trend: "+65.4%",
        color: "#f59e0b",
        icon: IconAlertCircle,
      },
      {
        title: "HTTP 5xx",
        value: 432,
        trend: "+89.2%",
        color: "#dc2626",
        icon: IconAlertCircle,
      },
      {
        title: "Connection Failures",
        value: 278,
        trend: "+43.7%",
        color: "#7c3aed",
        icon: IconPlugOff,
      },
      {
        title: "Network Errors",
        value: 1234,
        trend: "+156",
        color: "#eab308",
        icon: IconWifiOff,
      },
    ],
  },
];
