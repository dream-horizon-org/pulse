export interface RootCauseProps {
  interactionName: string | null;
  /** Optional date YYYY-MM-DD; when omitted backend uses today (UTC) */
  date?: string | null;
}
