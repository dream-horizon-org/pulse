export type EvidenceCardType =
  | "session-replay"
  | "funnel"
  | "journey"
  | "heatmap";

export interface EvidenceCardProps {
  type: EvidenceCardType;
  name: string;
  /** Optional relative time string, e.g. "45 min ago" */
  timestamp?: string;
  /** Short secondary line (device, tags, heatmap focus, etc.) */
  subtitle?: string;
  /** Longer supporting text (truncated in the card) */
  detail?: string;
  href: string;
}
