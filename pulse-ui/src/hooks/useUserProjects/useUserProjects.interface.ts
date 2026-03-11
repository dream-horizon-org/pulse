export interface ProjectSummary {
  projectId: string;
  name: string;
  description?: string;
  isActive: boolean;
  role: string;
}

export interface UserProjectsResponse {
  tenantId: string;
  tenantName: string;
  projects: ProjectSummary[];
  redirectTo?: string;
}
