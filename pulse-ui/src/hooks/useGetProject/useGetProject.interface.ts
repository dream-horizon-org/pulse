export interface ProjectDetailsResponse {
  projectId: string;
  name: string;
  description: string;
  tenantId: string;
  apiKey?: string;
  isEventFlowStarted: boolean;
  isActive: boolean;
  userRole: string;
  createdAt: string;
  createdBy: string;
}
