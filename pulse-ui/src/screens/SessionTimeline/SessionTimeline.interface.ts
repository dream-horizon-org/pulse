export interface SessionTimelineEvent {
  id: string;
  name: string;
  type: EventType;
  timestamp: number; // milliseconds from session start
  absoluteTimestamp?: number; // absolute timestamp in milliseconds (Unix epoch)
  duration?: number; // milliseconds, undefined for instantaneous events
  children?: SessionTimelineEvent[]; // nested events
  attributes?: Record<string, any>;
}

export type EventType =
  | "crash"
  | "anr"
  | "frozen_frame"
  | "trace"
  | "span"
  | "log"
  | "custom";

// Re-export OTel types for backward compatibility
export type { OtelEventType } from "./constants/otelConstants";

export interface SessionSummary {
  sessionId: string;
  platform: string;
  status: "active" | "crashed" | "completed";
  duration: number; // total duration in milliseconds
  crashes: number;
  anrs: number;
  frozenFrames: number;
  totalEvents: number;
  spanName?: string;
}

export interface SessionTimelineProps {
  // Component props if needed
}
