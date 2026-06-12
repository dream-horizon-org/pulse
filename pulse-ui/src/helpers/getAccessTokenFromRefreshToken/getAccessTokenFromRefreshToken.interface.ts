import { SYSTEM_ROLES } from "../../constants";

export type GetAccessTokenFromRefreshTokenSuccessResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  systemRole?: typeof SYSTEM_ROLES[keyof typeof SYSTEM_ROLES];
};
