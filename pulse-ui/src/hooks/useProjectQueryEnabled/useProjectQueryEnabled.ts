import { useProjectContext } from "../../contexts";

/**
 * Hook that returns whether React Query should be enabled for project-scoped API calls.
 *
 * This hook ensures that all project-scoped API hooks wait for the project context
 * to be fully initialized (including sessionStorage write) before making requests.
 * This prevents "No Project context is set" errors when:
 * - Navigating to project pages with cleared session storage
 * - Using deep links to project sub-routes
 * - Switching projects
 *
 * @param additionalConditions - Optional additional conditions to gate the query
 * @returns boolean - true when both project context is ready AND additional conditions pass
 *
 * @example Basic usage in a hook:
 * ```ts
 * export const useGetAlerts = () => {
 *   const enabled = useProjectQueryEnabled();
 *   return useQuery({
 *     queryKey: ["alerts"],
 *     queryFn: fetchAlerts,
 *     enabled, // Wait for project context
 *   });
 * };
 * ```
 *
 * @example With additional conditions:
 * ```ts
 * export const useGetAlert = (alertId?: string) => {
 *   const enabled = useProjectQueryEnabled(!!alertId);
 *   return useQuery({
 *     queryKey: ["alert", alertId],
 *     queryFn: () => fetchAlert(alertId!),
 *     enabled, // Wait for both context AND alertId
 *   });
 * };
 * ```
 */
export const useProjectQueryEnabled = (
  additionalConditions: boolean = true,
): boolean => {
  const { projectId } = useProjectContext();
  return !!projectId && additionalConditions;
};
