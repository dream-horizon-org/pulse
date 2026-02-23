import { TenantLookupResponse } from "./tenantLookup.interface";
import { makeRequest } from "../makeRequest";
import { API_ROUTES, API_BASE_URL } from "../../constants";

/**
 * Looks up the tenant information based on the current hostname.
 * The backend extracts the subdomain from the Host header to find the matching tenant.
 *
 * @returns Promise with tenant information including gcpTenantId
 */
export const lookupTenant = () => {
  return makeRequest<TenantLookupResponse>({
    url: `${API_BASE_URL}${API_ROUTES.TENANT_LOOKUP.apiPath}`,
    init: {
      method: API_ROUTES.TENANT_LOOKUP.method,
    },
  });
};


