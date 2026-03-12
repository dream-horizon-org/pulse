export interface AcceptTncParams {
  versionId: number;
}

export interface AcceptTncResponse {
  status: string;
  tenantId: string;
  version: string;
  acceptedBy: string;
  acceptedAt: string;
}

export interface UseAcceptTncOptions {
  onSettled?: (data: any, error: any, variables: any, context: any) => void;
}
