export type PaginationType = {
  page: number;
  size: number;
};

export type FiltersType = {
  users: string;
  status: string;
};

export const defaultPageSize = 18;
export const loadMoreDelayMs = 500;
