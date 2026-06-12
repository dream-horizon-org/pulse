import type { ReactNode } from "react";

export interface MetricCardProps {
  icon?: ReactNode;
  label: string;
  value: string | number;
  unit?: string;
  subtitle?: string;
  trend?: string;
  trendDirection?: "up" | "down" | "neutral";
  color?: string;
  backgroundColor?: string;
  size?: "small" | "medium" | "large";
  onClick?: () => void;
}
