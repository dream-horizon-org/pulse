import React, { ReactNode, useEffect } from 'react';
import { TenantProvider, useTenantContext } from './TenantContext';
import { ProjectProvider, useProjectContext } from './ProjectContext';
import { LOGOUT_EVENT } from '../helpers/logout';

/**
 * AppContextProvider - Technical wrapper that composes domain contexts
 * 
 * This is NOT a domain entity - it's a composition utility that:
 * - Wraps TenantProvider (organization-level state)
 * - Wraps ProjectProvider (project-level state)
 * - Keeps App.tsx clean and organized
 * - Listens for global logout events to clear contexts
 * 
 * Think of it like a "power strip" that connects multiple contexts to the React tree.
 */
export function AppContextProvider({ children }: { children: ReactNode }) {
  return (
    <TenantProvider>
      <ProjectProvider>
        <LogoutListener>
          {children}
        </LogoutListener>
      </ProjectProvider>
    </TenantProvider>
  );
}

/**
 * Internal component that listens for logout events
 * Must be inside both providers to access their clear methods
 */
function LogoutListener({ children }: { children: ReactNode }) {
  const { clearProject } = useProjectContext();
  const { clearTenant } = useTenantContext();

  useEffect(() => {
    const handleLogout = () => {
      clearProject();
      clearTenant();
    };

    window.addEventListener(LOGOUT_EVENT, handleLogout);

    return () => {
      window.removeEventListener(LOGOUT_EVENT, handleLogout);
    };
  }, [clearProject, clearTenant]);

  return <>{children}</>;
}
