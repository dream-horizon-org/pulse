export interface RootCauseProps {
  interactionName: string | null;
  /** Optional date YYYY-MM-DD; when omitted backend uses today (UTC) */
  date?: string | null;
  /** Project ID for query key so requests refetch when project is synced from URL */
  projectId?: string | null;
}
