export type SessionStartReason =
  | "sdk_init"
  | "inactivity_timeout"
  | "max_lifetime";

export type SessionEndReason =
  | "inactivity_timeout"
  | "shutdown"
  | "page_unload"
  | "max_lifetime";

export interface SessionChangeEvent {
  type: "start" | "end";
  newSessionId?: string;
  previousSessionId?: string;
  sessionId?: string;
  /** Duration in nanoseconds */
  durationNs?: number;
  reason: SessionStartReason | SessionEndReason;
}
