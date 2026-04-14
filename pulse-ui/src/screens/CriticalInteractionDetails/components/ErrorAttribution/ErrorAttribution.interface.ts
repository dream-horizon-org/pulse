export interface ErrorAttributionProps {
  interactionName: string;
  /** Same `yyyy-MM-dd` as Root Cause / CriticalInteractionDetails */
  date: string | null;
  projectId: string | null;
}
