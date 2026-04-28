export type GetAccessTokenFromRefreshTokenSuccessResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  systemRole?: "superadmin" | "internal_viewer";
};
