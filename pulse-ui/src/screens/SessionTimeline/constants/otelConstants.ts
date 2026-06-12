import {
  IconAlertCircle,
  IconAlertTriangle,
  IconBolt,
  IconChartLine,
  IconDatabase,
  IconFileText,
  IconSparkles,
} from "@tabler/icons-react";
import { MantineTheme } from "@mantine/core";

export type OtelEventType =
  | "crash"
  | "anr"
  | "frozen_frame"
  | "trace"
  | "span"
  | "log"
  | "custom";

export const OTEL_EVENT_TYPES: OtelEventType[] = [
  "crash",
  "anr",
  "frozen_frame",
  "trace",
  "span",
  "log",
  "custom",
];

export const getOtelEventIcon = (type: OtelEventType) => {
  switch (type) {
    case "crash":
      return IconAlertCircle;
    case "anr":
      return IconAlertTriangle;
    case "frozen_frame":
      return IconBolt;
    case "trace":
      return IconChartLine;
    case "span":
      return IconDatabase;
    case "log":
      return IconFileText;
    case "custom":
      return IconSparkles;
    default:
      return IconFileText;
  }
};

export const getOtelEventColor = (
  type: OtelEventType,
  theme: MantineTheme,
): string => {
  switch (type) {
    case "crash":
      return theme.colors.error[6];
    case "anr":
      return theme.colors.warning[6];
    case "frozen_frame":
      return theme.colors.warning[5];
    case "trace":
      return theme.colors.primary[6];
    case "span":
      return theme.colors.primary[7];
    case "log":
      return theme.colors.info[6];
    case "custom":
      return theme.colors.primary[8];
    default:
      return theme.colors.gray[6];
  }
};

export const getOtelEventLabel = (type: OtelEventType): string => {
  switch (type) {
    case "frozen_frame":
      return "Frozen Frame";
    default:
      return type.charAt(0).toUpperCase() + type.slice(1);
  }
};
