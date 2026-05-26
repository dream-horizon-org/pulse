import { getCookies } from "../helpers/cookies";
import { getProjectIdFromPath } from "../helpers/projectContext/projectContext";
import { PULSE_RUM_COOKIE_KEYS } from "./pulseRumConstants";

export type PulseEventAttributes = Record<
  string,
  string | number | boolean | null | undefined
>;

function readCookieTenantId(): string | undefined {
  const tenantId = getCookies(PULSE_RUM_COOKIE_KEYS.TENANT_ID);
  return tenantId && tenantId !== "undefined" ? tenantId : undefined;
}

function readSessionTenantId(): string | undefined {
  if (typeof window === "undefined") return undefined;
  try {
    const stored = sessionStorage.getItem("pulse_tenant_context");
    if (!stored) return undefined;
    const data = JSON.parse(stored) as { tenantId?: string };
    return data.tenantId && data.tenantId !== "undefined"
      ? data.tenantId
      : undefined;
  } catch {
    return undefined;
  }
}

function readSessionProjectId(): string | undefined {
  if (typeof window === "undefined") return undefined;
  try {
    const stored = sessionStorage.getItem("pulse_project_context");
    if (!stored) return undefined;
    const data = JSON.parse(stored) as { projectId?: string };
    return data.projectId && data.projectId !== "undefined"
      ? data.projectId
      : undefined;
  } catch {
    return undefined;
  }
}

/** Best-effort tenant / project scope for every custom event. */
export function readPulseEventContext(): Pick<
  PulseEventAttributes,
  "tenant_id" | "project_id"
> {
  return {
    tenant_id: readSessionTenantId() ?? readCookieTenantId(),
    project_id:
      readSessionProjectId() ??
      (typeof window !== "undefined"
        ? (getProjectIdFromPath(window.location.pathname) ?? undefined)
        : undefined),
  };
}

/** Defaults first; explicit caller attrs win (e.g. project_selected target id). */
export function withPulseEventContext(
  attrs?: PulseEventAttributes,
): PulseEventAttributes {
  return {
    ...readPulseEventContext(),
    ...attrs,
  };
}
