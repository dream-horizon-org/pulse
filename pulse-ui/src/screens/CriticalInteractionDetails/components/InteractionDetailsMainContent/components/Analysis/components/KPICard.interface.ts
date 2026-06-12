import { ReactNode } from "react";

export type TrendDirection = "up" | "down" | "neutral";

export interface KPICardProps {
  title: string;
  value: string | number;
  unit?: string;
  trend?: TrendDirection;
  trendValue?: string;
  icon: ReactNode;
  color?: "primary" | "secondary" | "success" | "error" | "warning" | "info";
}
