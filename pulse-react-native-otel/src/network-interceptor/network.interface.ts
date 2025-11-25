export interface RequestStartContext {
  url: string;
  method: string;
  type: 'fetch' | 'xmlhttprequest';
  baseUrl?: string;
}

export interface RequestEndContextSuccess {
  status: number;
  state: 'success';
}

export interface RequestEndContextError {
  state: 'error';
  status?: number;
  error?: Error;
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
