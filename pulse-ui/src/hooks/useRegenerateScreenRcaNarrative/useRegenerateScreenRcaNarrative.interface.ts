export type UseRegenerateScreenRcaNarrativeParams = {
  screenName: string;
  /** Anchor `yyyy-MM-dd` (same as screen root-cause). */
  anchorDate: string;
  /** Exclusive RCA window end — same query param as GET screen root-cause `asOf`. */
  asOfIso: string;
  windowStartIso: string;
  windowEndIso: string;
  projectId: string;
};
