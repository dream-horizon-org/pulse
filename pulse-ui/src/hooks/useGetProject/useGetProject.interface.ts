export interface ProjectDetailsResponse {
  projectId: string;
  name: string;
  description: string;
  tenantId: string;
  apiKey?: string;
  isEventFlowStarted: boolean;
  createdAt: string;
  createdBy: string;
}
