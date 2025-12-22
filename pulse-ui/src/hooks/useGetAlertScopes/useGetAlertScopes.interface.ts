export type AlertScopeItem = {
  id: number;
  name: string;
  label: string;
};

export type GetAlertScopesResponse = {
  scopes: AlertScopeItem[];
};

