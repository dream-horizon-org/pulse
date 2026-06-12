import { ReactNode } from "react";

export interface MetricBreakdownSectionProps {
  title: string;
  icon: ReactNode;
  color: "primary" | "error" | "warning" | "success" | "info" | "secondary";
  metric: string;
  platformData: any[];
  appVersionData: any[];
  deviceModelData: any[];
  osVersionData: any[];
  geographicData: any[];
  networkTypeData: any[];
  networkProviderData: any[];
  showSecondaryMetric?: boolean;
  secondaryMetric?: string | null;
  secondaryColor?: string | null;
}
