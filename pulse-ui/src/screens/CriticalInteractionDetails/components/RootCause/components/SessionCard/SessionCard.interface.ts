export interface SessionCardProps {
  /** Session identifier (e.g. s_301) */
  sessionId: string;
  /** Human-readable duration (e.g. "2:34") */
  duration: string;
  /** Relative timestamp (e.g. "30 min ago") */
  relativeTime: string;
  /** Device model and OS (e.g. "SM-S911B • Android 13") */
  device: string;
  /** Failure or issue summary shown in the highlighted box */
  failureSummary: string;
  /** Optional URL for "Watch Replay" link; when set, link uses href */
  replayUrl?: string;
  /** Optional callback when "Watch Replay" is clicked; used when replayUrl is not provided */
  onWatchReplay?: () => void;
}
