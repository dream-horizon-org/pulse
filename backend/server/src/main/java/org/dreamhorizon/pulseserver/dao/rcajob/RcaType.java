package org.dreamhorizon.pulseserver.dao.rcajob;

/** RCA report types supported by the async job system. */
public enum RcaType {
  INTERACTION,
  /** Per-interaction health report (InteractionReportV1) — distinct from legacy INTERACTION RCA. */
  INTERACTION_REPORT,
  SESSION,
  SCREEN
  // Future: EVENT, USER_SEGMENT, etc.
}
