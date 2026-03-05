export interface UseCreateUserAiSessionResponse {
  session_id: string;
  user_id: string;
}

export interface CreateSessionInput {
  sessionId: string;
}

export type OnSettled = (
  data: UseCreateUserAiSessionResponse | undefined,
  error: unknown,
) => void;
