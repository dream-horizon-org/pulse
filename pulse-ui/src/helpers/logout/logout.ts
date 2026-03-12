import { removeAllCookies } from '../cookies';
import { googleLogout } from '@react-oauth/google';
import { signOutFirebase, isGcpMultiTenantEnabled } from '../gcpAuth';

/**
 * Central logout utility that handles all cleanup
 * 
 * This function:
 * 1. Signs out from Google/Firebase
 * 2. Clears all cookies
 * 3. Clears sessionStorage (which includes context data)
 * 
 * Note: React context clearing (clearProject, clearTenant) should be called
 * by the component that invokes this, as this helper doesn't have access to contexts.
 */
export const performLogout = async (): Promise<void> => {
  try {
    // Sign out from authentication provider
    if (isGcpMultiTenantEnabled()) {
      await signOutFirebase();
    } else {
      googleLogout();
    }
  } catch (error) {
    console.error('[Logout] Error signing out from auth provider:', error);
  }

  // Clear all cookies
  removeAllCookies();

  // Clear all sessionStorage (includes context data)
  sessionStorage.clear();
};

/**
 * Clears only the context-related storage without full logout
 * Useful for testing or when you want to clear state without signing out
 */
export const clearContextStorage = (): void => {
  sessionStorage.removeItem('pulse_project_context');
  sessionStorage.removeItem('pulse_tenant_context');
  sessionStorage.removeItem('pulse_last_project_id');
};
