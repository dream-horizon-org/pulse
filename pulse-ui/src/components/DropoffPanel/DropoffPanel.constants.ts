import type { FunnelDropoffCauseKind } from "../../services/funnels.service";

/** Human-readable label per cause kind — drives row chip text. */
export const CAUSE_KIND_LABELS: Record<FunnelDropoffCauseKind, string> = {
  crash: "Crash",
  anr: "ANR",
  non_fatal: "Non-fatal",
  http_5xx: "HTTP 5xx",
  http_4xx: "HTTP 4xx",
  frozen_frame: "Frozen frame",
};

/** Mantine color per cause kind — drives chip background. */
export const CAUSE_KIND_COLOR: Record<FunnelDropoffCauseKind, string> = {
  crash: "red",
  anr: "orange",
  non_fatal: "yellow",
  http_5xx: "red",
  http_4xx: "orange",
  frozen_frame: "grape",
};

/** Lift threshold above which a cause is treated as "significantly higher for droppers". */
export const SIGNIFICANT_LIFT_THRESHOLD = 1.5;
