import { AlertListItem } from "../useGetAlertList/useGetAlertList.interface";

export type GetAlertDetailsParams = {
  queryParams: {
    alert_id: string | null;
  } | null;
  /** Polling interval in milliseconds. Set to false to disable polling. */
  refetchInterval?: number | false;
};

export type AlertDetailsResponse = AlertListItem;



