export interface ExceptionRow {
  id: string;
  title?: string;
  message: string;
  errorMessage?: string;
  anrMessage?: string;
  issueType?: string;
  appVersions: string; // Comma-separated string
  occurrences: number;
  affectedUsers: number;
  firstSeen?: string;
  lastSeen?: string;
}
