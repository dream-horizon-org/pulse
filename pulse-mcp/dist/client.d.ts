export declare class PulseClient {
    private http;
    private creds;
    private baseUrl;
    private refreshing;
    constructor(baseUrl: string);
    get<T>(path: string, projectId?: string, params?: Record<string, unknown>, raw?: boolean): Promise<T>;
    post<T>(path: string, body: unknown, projectId?: string, raw?: boolean): Promise<T>;
}
export declare function getClient(): PulseClient;
