import { API_ROUTES, API_BASE_URL, COOKIES_KEY } from "../../constants";
import { getCookies, setCookies } from "../cookies";
import { checkRefreshTokenExpiration } from "../checkRefreshTokenExpiration";
import { ApiResponse } from "../makeRequest";
import { makeRequestToServer } from "../makeRequestToServer";
import { GetAccessTokenFromRefreshTokenSuccessResponse } from "./getAccessTokenFromRefreshToken.interface";

export const getAndSetAccessTokenFromRefreshToken =
  async (): Promise<boolean> => {
    try {
      const refreshToken = getCookies(COOKIES_KEY.REFRESH_TOKEN);

      if (!refreshToken) {
        return false;
      }

      if (checkRefreshTokenExpiration(refreshToken)) {
        return false;
      }

      const response = await makeRequestToServer({
        url: `${API_BASE_URL}${API_ROUTES.REFRESH_TOKEN.apiPath}`,
        init: {
          method: API_ROUTES.REFRESH_TOKEN.method,
          body: JSON.stringify({ refreshToken }),
        },
      });

      if (response.status === 200) {
        const {
          data,
        }: ApiResponse<GetAccessTokenFromRefreshTokenSuccessResponse> =
          await response.json();
        if (data?.accessToken && data?.refreshToken && data?.tokenType) {
          setCookies(COOKIES_KEY.ACCESS_TOKEN, data.accessToken);
          setCookies(COOKIES_KEY.REFRESH_TOKEN, data.refreshToken);
          setCookies(COOKIES_KEY.TOKEN_TYPE, data.tokenType);
          return true;
        }
        return false;
      }

      return false;
    } catch (error) {
      console.error(error);
      return false;
    }
  };
