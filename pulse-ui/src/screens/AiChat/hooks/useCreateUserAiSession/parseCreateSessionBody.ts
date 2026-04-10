import type { CreateSessionBody } from "./useCreateUserAiSession.interface";

const INVALID_CREATE_SESSION_BODY_MESSAGE =
  "Invalid create session response: missing session_id" as const;

export const parseCreateSessionBody = (json: unknown): CreateSessionBody => {
  const jsonIsNotObject = typeof json !== "object" || json === null;
  if (jsonIsNotObject) {
    throw new Error(INVALID_CREATE_SESSION_BODY_MESSAGE);
  }
  const hasSessionIdKey = "session_id" in json;
  const sessionIdValue = (json as { session_id: unknown }).session_id;
  const sessionIdIsNonEmptyString =
    typeof sessionIdValue === "string" && sessionIdValue.length > 0;
  const isInvalidPayload = !hasSessionIdKey || !sessionIdIsNonEmptyString;
  if (isInvalidPayload) {
    throw new Error(INVALID_CREATE_SESSION_BODY_MESSAGE);
  }
  return json as CreateSessionBody;
};
