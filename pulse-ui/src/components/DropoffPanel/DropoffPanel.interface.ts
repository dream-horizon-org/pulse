export interface DropoffPanelProps {
  /** Controls drawer visibility. */
  opened: boolean;
  onClose: () => void;
  /** Funnel the selected step belongs to. */
  funnelId: string | undefined;
  /** Zero-based step the user clicked on in the funnel chart. */
  stepIndex: number | undefined;
  /**
   * Optional funnel {@code RunTime} the parent already knows about. When absent
   * the backend picks the latest run for the funnel.
   */
  runTime?: string;
  /** Opens the full async RCA report (e.g. funnel detail root-cause tab). */
  onFullRcaClick?: () => void;
}
