import { ApiResponse } from "../../helpers/makeRequest";

export interface UseCreateUserAiSessionResponse {
  session_id: string;
  message: string;
}

export type UseCreateUserAiSessionOnSettledResponse =
  | ApiResponse<UseCreateUserAiSessionResponse>
  | undefined;

export type OnSettled = (
  response: UseCreateUserAiSessionOnSettledResponse,
) => void;
