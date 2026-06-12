import { CriticalInteractionDetailsFilterValues } from "../../../../../../CriticalInteractionDetails.interface";

export interface OsCrashData {
  os: string;
  crashes: number;
}

export interface OsAnrData {
  os: string;
  anr: number;
}

export interface OsFrozenFrameData {
  os: string;
  frozenFrames: number;
}

export interface OsPerformanceData {
  crashesByOS: OsCrashData[];
  anrByOS: OsAnrData[];
  frozenFramesByOS: OsFrozenFrameData[];
}

export interface UseGetOsPerformanceParams {
  interactionName: string;
  startTime: string;
  endTime: string;
  enabled?: boolean;
  dashboardFilters?: CriticalInteractionDetailsFilterValues;
}

export interface UseGetOsPerformanceReturn {
  osPerformanceData: OsPerformanceData;
  isLoading: boolean;
  isError: boolean;
  error?: any;
}

