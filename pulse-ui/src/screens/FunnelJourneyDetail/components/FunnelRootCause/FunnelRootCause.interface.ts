export type FunnelRootCauseProps = {
  funnelId: string | number;
  focusStepIndex: number;
  /** Display name for the focused funnel step (event / step label). */
  focusStepName?: string | null;
  projectId: string | null | undefined;
  windowStartIso: string;
  windowEndIso: string;
  anchorDate: string | null | undefined;
};
