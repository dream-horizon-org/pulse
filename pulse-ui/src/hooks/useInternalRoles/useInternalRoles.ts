import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import type { ApiResponse } from "../../helpers/makeRequest/makeRequest.interface";
import { getCookies, removeCookie } from "../../helpers/cookies";
import { API_BASE_URL, API_METHODS, API_ROUTES, COOKIES_KEY } from "../../constants";

export interface InternalRoleMember {
  userId: string;
  email: string | null;
}

export interface InternalRoles {
  superadminMembers: InternalRoleMember[];
  internalViewerMembers: InternalRoleMember[];
}

type SuperAdminsListResponse = {
  userIds: string[];
  members?: InternalRoleMember[];
};

function mapAdminMembers(data: SuperAdminsListResponse | null | undefined): InternalRoleMember[] {
  if (!data) return [];
  if (data.members && data.members.length > 0) {
    return data.members;
  }
  return (data.userIds ?? []).map((userId) => ({ userId, email: null }));
}

function adminPathForRole(role: string): string {
  if (role === "internal_viewer") {
    return API_ROUTES.ADMIN_INTERNAL_VIEWERS.apiPath;
  }
  return API_ROUTES.ADMIN_SUPERADMINS.apiPath;
}

function throwHttpError(status: number, message: string): never {
  const err = new Error(message) as Error & { status: number };
  err.status = status;
  throw err;
}

/** makeRequest resolves with { data, error, status } and does not throw on 4xx — use this so mutations fail and onSuccess does not run. */
function throwIfApiFailed(res: ApiResponse<unknown>): void {
  if (res.error) {
    const err = new Error(res.error.message) as Error & { status: number };
    err.status = res.status;
    throw err;
  }
  if (res.status < 200 || res.status >= 300) {
    throwHttpError(
      res.status || 500,
      res.status === 0 ? "Request failed" : `Request failed (${res.status})`,
    );
  }
}

export const useInternalRoles = () =>
  useQuery<InternalRoles>({
    queryKey: ["internal-roles"],
    queryFn: async () => {
      const [saRes, ivRes] = await Promise.all([
        makeRequest<SuperAdminsListResponse>({
          url: `${API_BASE_URL}${API_ROUTES.ADMIN_SUPERADMINS.apiPath}`,
          init: { method: API_METHODS.GET },
        }),
        makeRequest<SuperAdminsListResponse>({
          url: `${API_BASE_URL}${API_ROUTES.ADMIN_INTERNAL_VIEWERS.apiPath}`,
          init: { method: API_METHODS.GET },
        }),
      ]);
      if (saRes.status === 403 || ivRes.status === 403) {
        throwHttpError(
          403,
          saRes.error?.message ||
            ivRes.error?.message ||
            "You no longer have access to developer settings.",
        );
      }
      if (saRes.error) {
        throw new Error(saRes.error.message);
      }
      if (ivRes.error) {
        throw new Error(ivRes.error.message);
      }
      return {
        superadminMembers: mapAdminMembers(saRes.data),
        internalViewerMembers: mapAdminMembers(ivRes.data),
      };
    },
    staleTime: 30_000,
  });

export const useAssignRole = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      identifier,
      role,
    }: {
      identifier: string;
      role: "superadmin" | "internal_viewer";
    }) => {
      const trimmed = identifier.trim();
      const body =
        trimmed.includes("@") ?
          { email: trimmed.toLowerCase() }
        : { userId: trimmed };
      const res = await makeRequest({
        url: `${API_BASE_URL}${adminPathForRole(role)}`,
        init: {
          method: API_METHODS.POST,
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        },
      });
      throwIfApiFailed(res);
      return res;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ["internal-roles"] }),
  });
};

export type UseRevokeRoleOptions = {
  /** Called after revoking your own superadmin (before refetch would 403). */
  onLostSuperadminAccess?: () => void;
};

export const useRevokeRole = (options?: UseRevokeRoleOptions) => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      userId,
      role,
    }: {
      userId: string;
      role: "superadmin" | "internal_viewer";
    }) => {
      const res = await makeRequest({
        url: `${API_BASE_URL}${adminPathForRole(role)}/${encodeURIComponent(userId)}`,
        init: {
          method: API_METHODS.DELETE,
        },
      });
      throwIfApiFailed(res);
      return res;
    },
    onSuccess: (
      _data: unknown,
      variables: { userId: string; role: "superadmin" | "internal_viewer" },
    ) => {
      const me = getCookies(COOKIES_KEY.USER_ID);
      if (
        variables.role === "superadmin" &&
        me &&
        variables.userId === me
      ) {
        removeCookie(COOKIES_KEY.SYSTEM_ROLE);
        qc.removeQueries({ queryKey: ["internal-roles"] });
        options?.onLostSuperadminAccess?.();
        return;
      }
      qc.invalidateQueries({ queryKey: ["internal-roles"] });
    },
  });
};
