export type EventAttribute = {
  id?: number;
  attributeName: string;
  description: string;
  dataType: string;
  isRequired: boolean;
  isArchived?: boolean;
};

export type EventDefinition = {
  id: number;
  eventName: string;
  displayName: string;
  description: string;
  category: string;
  isArchived: boolean;
  attributes: EventAttribute[];
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
};

export type EventDefinitionListResponse = {
  eventDefinitions: EventDefinition[];
  totalCount: number;
};

export type BulkUploadResponse = {
  created: number;
  updated: number;
  skipped: number;
  errors: Array<{
    line: number;
    eventName: string;
    message: string;
  }>;
};
