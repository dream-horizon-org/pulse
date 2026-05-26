import type { SessionItem } from "../../../services/sessionReplay/types";

/** Resolves session end time from listing row (API field or start + duration). */
export function resolveSessionEndTime(
  session: Pick<SessionItem, "startTime" | "endTime" | "durationMs">,
): string {
  if (session.endTime?.trim()) {
    return session.endTime;
  }
  const startMs = new Date(session.startTime).getTime();
  if (!Number.isFinite(startMs)) {
    return session.startTime;
  }
  return new Date(startMs + session.durationMs).toISOString();
}

export interface SessionTimeBounds {
  startTime: string;
  endTime: string;
  durationMs: number;
}

export function sessionTimeBoundsFromListing(
  session: Pick<SessionItem, "startTime" | "endTime" | "durationMs">,
): SessionTimeBounds {
  return {
    startTime: session.startTime,
    endTime: resolveSessionEndTime(session),
    durationMs: session.durationMs,
  };
}

export function buildSessionDetailSearchParams(
  bounds: SessionTimeBounds,
): URLSearchParams {
  const params = new URLSearchParams();
  params.set("startTime", bounds.startTime);
  params.set("endTime", bounds.endTime);
  params.set("durationMs", String(bounds.durationMs));
  return params;
}
