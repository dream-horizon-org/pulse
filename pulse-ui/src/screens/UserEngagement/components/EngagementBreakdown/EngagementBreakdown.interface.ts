export type BreakdownDimension =
  | "region"
  | "network"
  | "platform"
  | "os"
  | "device"
  | "custom";

export type NonCustomDimension = Exclude<BreakdownDimension, "custom">;

export type BreakdownItem = {
  name: string;
  dau: number;
  wau: number;
  mau: number;
  sessions: number;
  wowChange: number;
};

export type BreakdownDataMap = Record<NonCustomDimension, BreakdownItem[]>;

export type CustomAttributeOption = {
  value: string;
  label: string;
  description?: string;
};

export type CustomAttributeDataMap = Record<string, BreakdownItem[]>;

export interface EngagementBreakdownProps {
  customAttributeOptions?: CustomAttributeOption[];
  customAttributeData?: CustomAttributeDataMap;
}
