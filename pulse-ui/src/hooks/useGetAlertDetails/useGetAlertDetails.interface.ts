import { AlertListItem } from "../useGetAlertList/useGetAlertList.interface";

export type GetAlertDetailsParams = {
  queryParams: {
    alert_id: string | null;
  } | null;
};

export type AlertDetailsResponse = AlertListItem;



