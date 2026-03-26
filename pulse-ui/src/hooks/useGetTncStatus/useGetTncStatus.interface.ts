export interface TncDocumentUrls {
  tos: string;
  aup: string;
  privacy_policy: string;
}

export interface TncStatusResponse {
  accepted: boolean;
  version: string;
  versionId: number;
  documents: TncDocumentUrls;
  acceptedBy: string | null;
  acceptedAt: string | null;
}
