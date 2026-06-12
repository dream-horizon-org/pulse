import type { ReactNode } from "react";

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

export interface ExceptionTableProps {
  title: string;
  icon: ReactNode;
  iconColor: string;
  badgeColor: string;
  emptyIcon: string;
  emptyMessage: string;
  exceptions: ExceptionRow[];
  isLoading: boolean;
  isError: boolean;
  errorMessage?: string;
  onRowClick: (groupId: string) => void;
  showTypeColumn?: boolean; // For NonFatalList
}
