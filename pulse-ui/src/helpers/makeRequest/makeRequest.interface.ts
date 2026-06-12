export type MakeRequestConfig = {
  url: RequestInfo | URL;
  init?: RequestInit;
  /** Set to true for endpoints that return raw data without the { data, error } wrapper */
  unwrapped?: boolean;
};

export type DefaultErrorResponse = {
  code: string;
  message: string;
  cause: string;
};

export type ApiResponse<D> = {
  data: D | null | undefined;
  error: DefaultErrorResponse | null | undefined;
  status: number;
};
