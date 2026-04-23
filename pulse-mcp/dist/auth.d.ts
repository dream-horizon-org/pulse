export interface Credentials {
    accessToken: string;
    refreshToken: string;
}
export declare function loadCredentials(): Credentials;
export declare function saveCredentials(creds: Credentials): void;
export declare function refreshAccessToken(baseUrl: string, refreshToken: string): Promise<Credentials>;
