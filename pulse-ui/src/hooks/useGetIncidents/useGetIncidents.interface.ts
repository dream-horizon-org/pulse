export interface IncidentItem {
  id: number;
  title: string;
  description: string;
  severity: string;
  status: string;
  reporterName: string;
  reporterEmail: string;
  createdAt: string;
  updatedAt: string | null;
}
