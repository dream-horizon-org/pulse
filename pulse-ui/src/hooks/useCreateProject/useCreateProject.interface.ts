export interface CreateProjectParams {
  name: string;
  description?: string;
}

export interface ProjectResponse {
  projectId: string;
  name: string;
  description: string;
  tenantId: string;
  apiKey: string;
  createdAt: string;
  createdBy: string;
}
