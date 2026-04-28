import { useTenantContext } from '../contexts/TenantContext';
import { useProjectContext } from '../contexts/ProjectContext';
import { COOKIES_KEY } from '../constants';
import { TENANT_ROLES, PROJECT_ROLES } from '../constants/Roles';
import { getCookies } from '../helpers/cookies';

/**
 * Hook for checking user permissions based on their roles in tenant and project contexts.
 * 
 * Tenant-level permissions control organization-wide actions (invite members, create projects).
 * Project-level permissions control project-specific actions (edit settings, invite collaborators).
 */
export function usePermissions() {
  const { userRole: tenantRole } = useTenantContext();
  const { userRole: projectRole } = useProjectContext();
  const isSuperadminSession = getCookies(COOKIES_KEY.SYSTEM_ROLE) === 'superadmin';

  return {
    // Tenant-level permissions (organization)
    canInviteTenantMembers: tenantRole === TENANT_ROLES.ADMIN,
    canRemoveTenantMembers: tenantRole === TENANT_ROLES.ADMIN,
    canUpdateTenantRoles: tenantRole === TENANT_ROLES.ADMIN,
    canCreateProjects: tenantRole === TENANT_ROLES.ADMIN,
    canManageOrgSettings: tenantRole === TENANT_ROLES.ADMIN,
    canViewOrgMembers: !!tenantRole, // Any tenant member can view
    
    // Project-level permissions
    canInviteProjectMembers:
      projectRole === PROJECT_ROLES.ADMIN || isSuperadminSession,
    canRemoveProjectMembers:
      projectRole === PROJECT_ROLES.ADMIN || isSuperadminSession,
    canEditProject: projectRole === PROJECT_ROLES.ADMIN || projectRole === PROJECT_ROLES.EDITOR,
    canDeleteProject: projectRole === PROJECT_ROLES.ADMIN,
    canViewProject: !!projectRole,
    canManageProjectSettings:
      projectRole === PROJECT_ROLES.ADMIN || isSuperadminSession,
    
    // Role info (for display purposes)
    tenantRole,
    projectRole,
  };
}
