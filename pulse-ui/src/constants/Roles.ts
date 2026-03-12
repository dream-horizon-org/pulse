/**
 * Tenant-level role constants
 * Used for organization-wide permissions
 */
export const TENANT_ROLES = {
  ADMIN: 'admin',
  MEMBER: 'member',
} as const;

export type TenantRole = typeof TENANT_ROLES[keyof typeof TENANT_ROLES];

/**
 * Tenant role display labels
 */
export const TENANT_ROLE_LABELS = {
  [TENANT_ROLES.ADMIN]: 'Admin',
  [TENANT_ROLES.MEMBER]: 'Member',
} as const;

/**
 * Project-level role constants
 * Used for project-specific permissions
 */
export const PROJECT_ROLES = {
  ADMIN: 'admin',
  EDITOR: 'editor',
  VIEWER: 'viewer',
} as const;

export type ProjectRole = typeof PROJECT_ROLES[keyof typeof PROJECT_ROLES];

/**
 * Project role display labels
 */
export const PROJECT_ROLE_LABELS = {
  [PROJECT_ROLES.ADMIN]: 'Admin',
  [PROJECT_ROLES.EDITOR]: 'Editor',
  [PROJECT_ROLES.VIEWER]: 'Viewer',
} as const;
