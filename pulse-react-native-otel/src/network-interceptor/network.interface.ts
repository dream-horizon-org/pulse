export interface RequestStartContext {
  url: string;
  method: string;
  type: 'fetch' | 'xmlhttprequest';
  baseUrl?: string;
  requestHeaders?: Record<string, string>;
  requestBodyContentLength?: number;
}

export interface RequestEndContextSuccess {
  status: number;
  state: 'success';
  responseHeaders?: Record<string, string>;
  responseBodyContentLength?: number;
}

export interface RequestEndContextError {
  state: 'error';
  status?: number;
  error?: Error;
  responseHeaders?: Record<string, string>;
  responseBodyContentLength?: number;
}

export type RequestEndContext =
  | RequestEndContextSuccess
  | RequestEndContextError;

export type RequestStartCallback = (
  context: RequestStartContext
) => { onRequestEnd?: RequestEndCallback } | undefined;

export type RequestEndCallback = (context: RequestEndContext) => void;

export interface NetworkRequestInfo {
  url: string;
  method: string;
  type: 'fetch' | 'xmlhttprequest';
  status?: number;
  state: 'success' | 'error';
  error?: Error;
}
