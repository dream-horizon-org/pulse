import axios, {
  AxiosInstance,
  AxiosRequestConfig,
  InternalAxiosRequestConfig,
} from "axios";
import {
  Credentials,
  exchangeApiKeyForTokens,
  loadCredentials,
  saveCredentials,
} from "./auth.js";
import { decodeAccessTokenEmail } from "./jwtEmail.js";

export class PulseClient {
  private http: AxiosInstance;
  private creds: Credentials;
  private baseUrl: string;
  private apiKey: string;
  private refreshPromise: Promise<void> | null = null;

  constructor(baseUrl: string, apiKey: string) {
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.creds = loadCredentials();

    this.http = axios.create({ baseURL: baseUrl });

    this.http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
      config.headers = config.headers ?? {};
      config.headers["Authorization"] = `Bearer ${this.creds.accessToken}`;
      config.headers["Accept"] = "application/json";
      config.headers["Content-Type"] = "application/json";
      return config;
    });

    this.http.interceptors.response.use(
      (r) => r,
      async (error) => {
        if (error.response?.status === 401) {
          if (!this.refreshPromise) {
            this.refreshPromise = exchangeApiKeyForTokens(
              this.baseUrl,
              this.apiKey,
            )
              .then((creds) => {
                this.creds = creds;
                saveCredentials(creds);
              })
              .finally(() => {
                this.refreshPromise = null;
              });
          }
          await this.refreshPromise;
          error.config.headers["Authorization"] =
            `Bearer ${this.creds.accessToken}`;
          return this.http.request(error.config);
        }
        return Promise.reject(error);
      },
    );
  }

  /** Email claim from the current access token (Pulse JWT). Used e.g. for session listing `user-email` when present. */
  requireUserEmailFromToken(): string {
    const email = decodeAccessTokenEmail(this.creds.accessToken);
    if (!email) {
      throw new Error(
        "Access token has no `email` claim. Re-exchange API key or use a token that includes email.",
      );
    }
    return email;
  }

  async get<T>(
    path: string,
    projectId?: string,
    params?: Record<string, unknown>,
    raw?: boolean,
    extraHeaders?: Record<string, string>,
  ): Promise<T> {
    const config: AxiosRequestConfig = { params };
    const headers: Record<string, string> = { ...(extraHeaders ?? {}) };
    if (projectId) headers["X-Project-ID"] = projectId;
    if (Object.keys(headers).length > 0) {
      config.headers = headers;
    }
    if (raw) {
      const resp = await this.http.get<T>(path, config);
      return resp.data;
    }
    const resp = await this.http.get<{ data: T }>(path, config);
    return resp.data.data;
  }

  async post<T>(
    path: string,
    body: unknown,
    projectId?: string,
    extraHeaders?: Record<string, string>,
    timeoutMs?: number,
  ): Promise<T> {
    const config: AxiosRequestConfig = {};
    const headers: Record<string, string> = { ...(extraHeaders ?? {}) };
    if (projectId) headers["X-Project-ID"] = projectId;
    if (Object.keys(headers).length > 0) {
      config.headers = headers;
    }
    if (timeoutMs !== undefined) {
      config.timeout = timeoutMs;
    }
    const resp = await this.http.post<{ data: T }>(path, body, config);
    return resp.data.data;
  }
}

let _client: PulseClient | null = null;

export function getClient(): PulseClient {
  if (!_client) {
    const baseUrl = process.env.PULSE_BASE_URL;
    if (!baseUrl) throw new Error("PULSE_BASE_URL env var is required");
    const apiKey = process.env.PULSE_API_KEY;
    if (!apiKey) throw new Error("PULSE_API_KEY env var is required");
    _client = new PulseClient(baseUrl, apiKey);
  }
  return _client;
}
