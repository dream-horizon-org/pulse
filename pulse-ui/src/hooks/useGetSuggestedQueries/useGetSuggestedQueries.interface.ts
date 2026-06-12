export interface SuggestedQuery {
  description: string;
  query: string;
  queryName: string;
}

export type GetSuggestedQueriesResponse = {
  suggestedQuery: SuggestedQuery[];
};
