import { ApiResponse } from "../../helpers/makeRequest";

export type GetPulseAiResponseType = {
  event?: "keepalive" | "done" | "complete" | "error";
  data: {
    text: string;
    status: string | null;
    function_call: string | null;
    function_response: string | null;
  };
};

// Type for the final parsed JSON response
export type PulseAiResponseData = {
  status: string | null;
  text: string;
  function_call: any | null;
  function_response: any | null;
};

export type GetPulseAiResponseRequestBody = {
  "user-id": string;
  "session-id": string;
  query: string;
};

export type GetPulseAiResponseOnSettledResponse =
  | ApiResponse<GetPulseAiResponseType>
  | undefined;

export type OnSettled = (response: GetPulseAiResponseOnSettledResponse) => void;
