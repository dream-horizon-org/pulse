import axios, { AxiosInstance, AxiosRequestConfig, InternalAxiosRequestConfig } from "axios";
import { Credentials, loadCredentials, refreshAccessToken } from "./auth.js";

export class PulseClient {
  private http: AxiosInstance;
  private creds: Credentials;
  private baseUrl: string;
  private refreshing = false;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
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
        if (error.response?.status === 401 && !this.refreshing) {
          this.refreshing = true;
          try {
            this.creds = await refreshAccessToken(this.baseUrl, this.creds.refreshToken);
            error.config.headers["Authorization"] = `Bearer ${this.creds.accessToken}`;
            return this.http.request(error.config);
          } finally {
            this.refreshing = false;
          }
        }
        return Promise.reject(error);
      }
    );
  }

  async get<T>(path: string, projectId?: string, params?: Record<string, unknown>, raw?: boolean): Promise<T> {
    const config: AxiosRequestConfig = { params };
    if (projectId) {
      config.headers = { "X-Project-ID": projectId };
    }
    if (raw) {
      const resp = await this.http.get<T>(path, config);
      return resp.data;
    }
    const resp = await this.http.get<{ data: T }>(path, config);
    return resp.data.data;
  }

  async post<T>(path: string, body: unknown, projectId?: string, raw?: boolean): Promise<T> {
    const config: AxiosRequestConfig = {};
    if (projectId) {
      config.headers = { "X-Project-ID": projectId };
    }
    if (raw) {
      const resp = await this.http.post<T>(path, body, config);
      return resp.data;
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
    _client = new PulseClient(baseUrl);
  }
  return _client;
}
