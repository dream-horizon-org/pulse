import { TenantRole } from "../constants/Roles";
import { ProjectRole } from "../constants/Roles";

// Tenant/Organization member types
export interface TenantMember {
  userId: string;
  email: string;
  name: string;
  role: TenantRole;
  status: string;
  lastLoginAt?: string;
}

export interface TenantMemberListResponse {
  members: TenantMember[];
  totalCount: number;
}

export interface InviteTenantMemberParams {
  tenantId: string;
  emails: string[]; // Email addresses (single or multiple)
  role: TenantRole;
}

export interface RemoveTenantMemberParams {
  tenantId: string;
  userId: string;
}

export interface UpdateTenantMemberRoleParams {
  tenantId: string;
  userId: string;
  newRole: TenantRole;
}

// Project member types
export interface ProjectMember {
  userId: string;
  email: string;
  name: string;
  role: ProjectRole;
  status: string;
  lastLoginAt?: string;
}

export interface ProjectMemberListResponse {
  members: ProjectMember[];
  totalCount: number;
}

export interface InviteProjectMemberParams {
  projectId: string;
  emails: string[]; // Email addresses (single or multiple)
  role: ProjectRole;
}

export interface RemoveProjectMemberParams {
  projectId: string;
  userId: string;
}

export interface UpdateProjectMemberRoleParams {
  projectId: string;
  userId: string;
  newRole: ProjectRole;
}

// Bulk invite result
export interface BulkInviteResult {
  successCount: number;
  failureCount: number;
  skippedCount: number;
  successEmails: string[];
  failedEmails: string[];
  skippedEmails: string[];
}
