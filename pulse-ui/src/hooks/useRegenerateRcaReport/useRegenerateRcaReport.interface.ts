export type UseRegenerateRcaReportParams = {
  entityKey: string;
  date?: string | null;
  projectId: string;
  rcaType?: string;
  windowStartIso?: string | null;
  windowEndIso?: string | null;
};
