export type PaginationType = {
  offset: number;
  limit: number;
};

export type FiltersType = {
  created_by: string | null;
  updated_by: string | null;
  scope: string | null;
};

export type AlertState =
  | "FIRING"
  | "NORMAL"
  | "ERRORED"
  | "SILENCED"
  | "NO_DATA";

export type AlertListingPageProps = {
  isInteractionDetailsFlow?: boolean;
  onCreateAlert?: () => void;
  handleEditAlertInInteractionDetails?: (alertId: number) => void;
};



