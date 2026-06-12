import { CriticalInteractionDetailsFilterValues } from "../../../../../../CriticalInteractionDetails.interface";

export interface DeviceCrashData {
  device: string;
  crashes: number;
}

export interface DeviceAnrData {
  device: string;
  anr: number;
}

export interface DeviceFrozenFrameData {
  device: string;
  frozenFrames: number;
}

export interface DevicePerformanceData {
  crashesByDevice: DeviceCrashData[];
  anrByDevice: DeviceAnrData[];
  frozenFramesByDevice: DeviceFrozenFrameData[];
}

export interface UseGetDevicePerformanceParams {
  interactionName: string;
  startTime: string;
  endTime: string;
  enabled?: boolean;
  dashboardFilters?: CriticalInteractionDetailsFilterValues;
}

export interface UseGetDevicePerformanceReturn {
  devicePerformanceData: DevicePerformanceData;
  isLoading: boolean;
  isError: boolean;
  error?: any;
}

