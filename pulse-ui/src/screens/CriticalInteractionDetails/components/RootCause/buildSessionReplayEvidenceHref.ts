import { generatePath } from "react-router-dom";
import { ROUTES } from "../../../../constants";

/** Project-scoped session replay detail when `projectId` is set; otherwise global `/session-replay/:id`. */
export function buildSessionReplayEvidenceHref(
  projectId: string | null | undefined,
  sessionId: string,
): string {
  const id = String(sessionId).trim();
  const pid = projectId != null ? String(projectId).trim() : "";
  if (pid !== "") {
    return generatePath(ROUTES.PROJECT_SESSION_REPLAY_DETAIL.path, {
      projectId: pid,
      sessionId: id,
    });
  }
  return generatePath(ROUTES.SESSION_REPLAY_DETAIL.path, { sessionId: id });
}
