export interface ApiKeyRestResponse {
  apiKeyId: number;
  projectId: string;
  displayName: string;
  apiKey: string;
  isActive: boolean;
  expiresAt: string | null;
  gracePeriodEndsAt: string | null;
  createdBy: string;
  createdAt: string;
  deactivatedAt: string | null;
  deactivatedBy: string | null;
  deactivationReason: string | null;
}

export interface ApiKeysResponse {
  apiKeys: ApiKeyRestResponse[];
  count: number;
}

export interface ApiKeyResult {
  key: string | null;
  isDummy: boolean;
  error?: string;
}
