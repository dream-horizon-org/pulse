export interface DeviceModelData {
  device: string;
  apdex: number;
  latency: number;
}

export interface AppVersionData {
  version: string;
  crashes: number;
  frozenFrames: number;
}

export interface NetworkTypeData {
  type: string;
  apdex: number;
  latency: number;
}

export interface UserSatisfactionByPlatform {
  platform: string;
  poor: number;
  average: number;
  good: number;
  excellent: number;
}

export interface OSVersionData {
  version: string;
  errorRate: number;
  crashes: number;
}

export interface CombinedMetricsSectionProps {
  deviceModelData: DeviceModelData[];
  appVersionData: AppVersionData[];
  networkTypeData: NetworkTypeData[];
  userSatisfactionByPlatform: UserSatisfactionByPlatform[];
  osVersionData: OSVersionData[];
}
