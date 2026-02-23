export interface RequestStartContext {
  url: string;
  method: string;
  type: 'fetch' | 'xmlhttprequest';
  baseUrl?: string;
  requestHeaders?: Record<string, string>;
}

export interface RequestEndContextSuccess {
  status: number;
  state: 'success';
  responseHeaders?: Record<string, string>;
}

export interface RequestEndContextError {
  state: 'error';
  status?: number;
  error?: Error;
  responseHeaders?: Record<string, string>;
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
