export type UserApiKeyListItem = {
  id: number;
  displayName: string;
  keyPrefix: string;
  isActive: boolean;
  createdAt: string;
};

export type CreateUserApiKeyResponse = {
  id: number;
  displayName: string;
  rawApiKey: string;
  keyPrefix: string;
  createdAt: string;
};
