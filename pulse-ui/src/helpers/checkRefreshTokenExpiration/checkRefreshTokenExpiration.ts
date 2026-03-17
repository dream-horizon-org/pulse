import { jwtDecode } from "jwt-decode";
import { DecodedRefreshToken } from "./checkRefreshTokenExpiration.interface";

export const checkRefreshTokenExpiration = (refreshToken: string) => {
  try {
    const { exp }: DecodedRefreshToken = jwtDecode(refreshToken);
    // Get the current Unix timestamp (in seconds)
    const currentTimestamp = Math.floor(Date.now() / 1000);
    if (currentTimestamp > exp) {
      // Return true if refresh token is expired
      return true;
    }
    // Return false if refresh token is not expired
    return false;
  } catch (error) {
    // Token is malformed or invalid, treat as expired
    console.warn("Invalid refresh token format, treating as expired:", error);
    return true;
  }
};
