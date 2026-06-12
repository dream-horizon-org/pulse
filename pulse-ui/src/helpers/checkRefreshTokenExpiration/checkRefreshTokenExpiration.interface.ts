export type DecodedRefreshToken = {
  exp: number;
  iat: number;
  iss: string;
  sub: string;
  azp: string;
  rft: string;
};
