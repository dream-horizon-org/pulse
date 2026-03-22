export interface UseCreateUserAiSessionResponse {
  session_id: string;
  user_id: string;
}

export type CreateSessionInput = Record<string, never>;

export type OnSettled = (
  data: UseCreateUserAiSessionResponse | undefined,
  error: unknown,
) => void;
