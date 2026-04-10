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

export interface ApiKeyListResponse {
  apiKeys: ApiKeyRestResponse[];
  count: number;
}

export interface CreateApiKeyResponse {
  apiKeyId: number;
  projectId: string;
  displayName: string;
  apiKey: string;
  expiresAt: string | null;
  createdAt: string;
}

export interface RegenerateApiKeyParams {
  projectId: string;
  isRegenerate: boolean;
}

export interface ProjectApiKeyResult {
  key: string | null;
  isDummy: boolean;
}
