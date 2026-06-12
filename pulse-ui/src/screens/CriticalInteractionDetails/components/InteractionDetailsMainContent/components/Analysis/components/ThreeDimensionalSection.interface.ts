export interface BubbleDataPoint {
  latency: number;
  crashes: number;
  apdex: number;
  platform: string;
}

export interface HeatmapOSDeviceRow {
  os: string;
  devices: {
    [deviceName: string]: number | undefined;
  };
}

export interface ComprehensiveTableRow {
  appVersion: string;
  device: string;
  os: string;
  location: string;
  apdex: number;
  errorRate: number;
  latency: number;
  crashes: number;
  anrs: number;
  frozenFrames: number;
}

export interface ThreeDimensionalSectionProps {
  bubbleData: BubbleDataPoint[];
  heatmapOSDevice: HeatmapOSDeviceRow[];
  comprehensiveTableData: ComprehensiveTableRow[];
}
