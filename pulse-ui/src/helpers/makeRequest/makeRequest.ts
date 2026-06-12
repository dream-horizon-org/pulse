import { COMMON_CONSTANTS, ROUTES } from "../../constants";
import { HTTP_STATUS } from "../../constants/API";
import { ApiResponse, MakeRequestConfig } from "./makeRequest.interface";
import { withTimeout } from "../withTimeout";
import { makeRequestToServer, streamAiRunSse } from "../makeRequestToServer";
import { getAndSetAccessTokenFromRefreshToken } from "../getAccessTokenFromRefreshToken";
import { processServerResponse } from "./processServerResponse";
import { removeAllCookies } from "../cookies";
import { dispatchLogoutEvent } from "../logout";

export const streamAiRunSseWithAuth = async (
  init?: RequestInit,
): Promise<Response> => {
  return await withTimeout(async () => {
    let response = await streamAiRunSse(init);

    if (response.status !== HTTP_STATUS.UNAUTHORIZED) {
      return response;
    }

    const isTokenUpdated = await getAndSetAccessTokenFromRefreshToken();
    if (!isTokenUpdated) {
      removeAllCookies();
      sessionStorage.clear();
      dispatchLogoutEvent();
      window.location.href = ROUTES.LOGIN.basePath;
      return response;
    }

    return await streamAiRunSse(init);
  });
};

export const makeRequest = async <D>(
  requestConfig: MakeRequestConfig,
): Promise<ApiResponse<D>> => {
  try {
    return await withTimeout(async () => {
      let response = await makeRequestToServer(requestConfig);

      if (response.status === HTTP_STATUS.UNAUTHORIZED) {
        const isTokenUpdated = await getAndSetAccessTokenFromRefreshToken();
        if (!isTokenUpdated) {
          // Clear all authentication data
          removeAllCookies();
          sessionStorage.clear();
          
          // Dispatch logout event to clear contexts
          dispatchLogoutEvent();
          
          // Redirect to login
          window.location.href = ROUTES.LOGIN.basePath;
          return {
            data: null,
            error: {
              code: `${response.status}`,
              message: "Authentication failed. Redirecting to login page",
              cause: "Token expired",
            },
            status: response.status,
          };
        }
        response = await makeRequestToServer(requestConfig);
      }

      return await processServerResponse(response, requestConfig.unwrapped);
    });
  } catch (error: unknown) {
    console.error(error);
    return {
      data: null,
      error: {
        code: "",
        message:
          error instanceof Error
            ? error.message
            : COMMON_CONSTANTS.DEFAULT_ERROR_MESSAGE,
        cause: "",
      },
      status: 0,
    };
  }
};
