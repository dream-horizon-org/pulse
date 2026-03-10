import React, { createContext, useContext, useState, useCallback, ReactNode, useEffect, useMemo } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ProjectSummary } from '../helpers/getUserProjects/getUserProjects.interface';
import { useUserProjects } from '../hooks';
import { TenantRole } from '../constants/Roles';
import { TIERS, TierType } from '../constants/Tiers';
import { TenantInfo, TenantContextType, StoredTenantData } from './TenantContext.interface';
import { API_ROUTES } from '../constants';

const TenantContext = createContext<TenantContextType | undefined>(undefined);

const STORAGE_KEY = 'pulse_tenant_context';

export function TenantProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [tenantId, setTenantId] = useState<string | null>(null);
  const [tenantName, setTenantName] = useState<string | null>(null);
  const [userRole, setUserRole] = useState<TenantRole | null>(null);
  const [tier, setTier] = useState<TierType | null>(null);
  const [shouldFetchProjects, setShouldFetchProjects] = useState(false);

  // Use React Query hook for fetching projects - THIS IS OUR SINGLE SOURCE OF TRUTH
  const { data: projectsData, isLoading, refetch } = useUserProjects(shouldFetchProjects);
  
  // Derive projects directly from React Query (no local state copying!)
  const projects = useMemo(() => projectsData?.data?.projects || [], [projectsData?.data?.projects]);
  const hasLoadedProjects = !!projectsData?.data;
  
  // Hydrate from sessionStorage on mount
  useEffect(() => {
    const stored = sessionStorage.getItem(STORAGE_KEY);
    if (stored) {
      try {
        const data: StoredTenantData = JSON.parse(stored);
        // Check if data is less than 1 hour old
        const ONE_HOUR = 60 * 60 * 1000;
        if (Date.now() - data.timestamp < ONE_HOUR) {
          setTenantId(data.tenantId);
          setTenantName(data.tenantName);
          setUserRole(data.userRole);
          setTier(data.tier);
          // No need to set projects - React Query will handle it!
          
          // Enable project fetching and refetch in background for fresh data
          setShouldFetchProjects(true);
          setTimeout(() => {
            refetch();
          }, 100);
        } else {
          sessionStorage.removeItem(STORAGE_KEY);
        }
      } catch (error) {
        sessionStorage.removeItem(STORAGE_KEY);
      }
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Persist to sessionStorage whenever state changes
  useEffect(() => {
    if (tenantId && userRole) {
      const data: StoredTenantData = {
        tenantId,
        tenantName: tenantName || '',
        userRole,
        tier: tier || TIERS.FREE,
        projects,
        timestamp: Date.now(),
      };
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    }
  }, [tenantId, tenantName, userRole, tier, projects]);

  // Update tenant info when React Query returns data
  useEffect(() => {
    if (projectsData?.data) {
      // Update tenant information from API response
      if (projectsData.data.tenantName) {
        setTenantName(projectsData.data.tenantName);
      }
      if (projectsData.data.tenantId) {
        setTenantId(projectsData.data.tenantId);
      }
    }
  }, [projectsData?.data]);

  const refreshProjects = useCallback(async () => {
    // Enable fetching if not already enabled
    setShouldFetchProjects(true);
    
    // Invalidate React Query cache to trigger fresh fetch
    await queryClient.invalidateQueries({ 
      queryKey: [API_ROUTES.GET_USER_PROJECTS.key] 
    });
  }, [queryClient]);

  const setTenantInfo = useCallback((tenant: TenantInfo) => {
    setTenantId(tenant.tenantId);
    setTenantName(tenant.tenantName);
    setUserRole(tenant.userRole);
    setTier(tenant.tier);
    
    // Auto-fetch projects when tenant is set
    if (tenant.tenantId) {
      setShouldFetchProjects(true);
      setTimeout(() => refetch(), 0);
    }
  }, [refetch]);

  const addProject = useCallback((project: ProjectSummary) => {
    // After adding project to backend, just refetch to update React Query cache
    refetch();
  }, [refetch]);

  const clearTenant = useCallback(() => {
    setTenantId(null);
    setTenantName(null);
    setUserRole(null);
    setTier(null);
    setShouldFetchProjects(false); // Disable fetching
    sessionStorage.removeItem(STORAGE_KEY);
  }, []);

  const value: TenantContextType = {
    tenantId,
    tenantName,
    userRole,
    tier,
    projects,
    isLoading,
    hasLoadedProjects,
    setTenantInfo,
    refreshProjects,
    addProject,
    clearTenant,
  };

  return (
    <TenantContext.Provider value={value}>
      {children}
    </TenantContext.Provider>
  );
}

export function useTenantContext(): TenantContextType {
  const context = useContext(TenantContext);
  if (context === undefined) {
    throw new Error('useTenantContext must be used within a TenantProvider');
  }
  return context;
}
