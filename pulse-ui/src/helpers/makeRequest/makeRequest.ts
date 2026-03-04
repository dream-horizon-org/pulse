import { COMMON_CONSTANTS, ROUTES } from "../../constants";
import { ApiResponse, MakeRequestConfig } from "./makeRequest.interface";
import { withTimeout } from "../withTimeout";
import { makeRequestToServer } from "../makeRequestToServer";
import { getAndSetAccessTokenFromRefreshToken } from "../getAccessTokenFromRefreshToken";
import { processServerResponse } from "./processServerResponse";
import { removeAllCookies } from "../cookies";

export const makeRequest = async <D>(
  requestConfig: MakeRequestConfig,
): Promise<ApiResponse<D>> => {
  try {
    return await withTimeout(async () => {
      let response = await makeRequestToServer(requestConfig);

      if (response.status === 401) {
        const isTokenUpdated = await getAndSetAccessTokenFromRefreshToken();
        if (!isTokenUpdated) {
          removeAllCookies();
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
