export type GetScreeNameToEvenQueryMappingParams = {
  queryParams: {
    search_string: string;
    limit?: string;
  } | null;
};

export type GetScreeNameToEvenQueryMappingResponse = {
  eventList: Array<EventListData>;
  recordCount: number;
};

export type EventListData = {
  properties?: Array<Property>;
  metadata?: Metadata;
};

export type Property = {
  propertyName: string;
  description: string;
  archived: boolean;
};

export type Metadata = {
  eventName: string;
  description: string;
  screenNames: Array<string>;
  archived: boolean;
  isActive: boolean;
};
