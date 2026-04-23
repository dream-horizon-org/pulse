import axios from "axios";
import { loadCredentials, refreshAccessToken } from "./auth.js";
export class PulseClient {
    http;
    creds;
    baseUrl;
    refreshing = false;
    constructor(baseUrl) {
        this.baseUrl = baseUrl;
        this.creds = loadCredentials();
        this.http = axios.create({ baseURL: baseUrl });
        this.http.interceptors.request.use((config) => {
            config.headers = config.headers ?? {};
            config.headers["Authorization"] = `Bearer ${this.creds.accessToken}`;
            config.headers["Accept"] = "application/json";
            config.headers["Content-Type"] = "application/json";
            return config;
        });
        this.http.interceptors.response.use((r) => r, async (error) => {
            if (error.response?.status === 401 && !this.refreshing) {
                this.refreshing = true;
                try {
                    this.creds = await refreshAccessToken(this.baseUrl, this.creds.refreshToken);
                    error.config.headers["Authorization"] = `Bearer ${this.creds.accessToken}`;
                    return this.http.request(error.config);
                }
                finally {
                    this.refreshing = false;
                }
            }
            return Promise.reject(error);
        });
    }
    async get(path, projectId, params, raw) {
        const config = { params };
        if (projectId) {
            config.headers = { "X-Project-ID": projectId };
        }
        if (raw) {
            const resp = await this.http.get(path, config);
            return resp.data;
        }
        const resp = await this.http.get(path, config);
        return resp.data.data;
    }
    async post(path, body, projectId, raw) {
        const config = {};
        if (projectId) {
            config.headers = { "X-Project-ID": projectId };
        }
        if (raw) {
            const resp = await this.http.post(path, body, config);
            return resp.data;
        }
        const resp = await this.http.post(path, body, config);
        return resp.data.data;
    }
}
let _client = null;
export function getClient() {
    if (!_client) {
        const baseUrl = process.env.PULSE_BASE_URL;
        if (!baseUrl)
            throw new Error("PULSE_BASE_URL env var is required");
        _client = new PulseClient(baseUrl);
    }
    return _client;
}
