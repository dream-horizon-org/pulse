import { InteractionDetailsResponse } from "../../helpers/getInteractionDetails";

export type GetInteractionsQueryParams = {
  enabled?: boolean;
  refetchInterval?: number;
  pageIdentifier?: string;
  queryParams: {
    page?: number;
    size?: number;
    userEmail?: string;
    status?: string;
    interactionName?: string;
    tags?: string | null;
  } | null;
};

export type GetInteractionsResponse = {
  interactions: Array<Row>;
  totalInteractions: number;
};

export type Row = InteractionDetailsResponse & {
  refetchJobList?: () => void;
  refetchJobDetails?: () => void;
};

export interface InteractionData {
  interactionName?: string;
  args?: object;
  /** @format date-time */
  createdAt?: number;
  createdBy?: string;
  datadogURL?: string;
  description?: string;
  /** @format int32 */
  id: number;
  isUISupported?: boolean;
  name?: string;
  /** @format int32 */
  resourceId?: number;
  resourceName?: string;
  /** @format date-time */
  running_since?: string;
  slackNotification?: string;
  status?: string;
  tags?: string[];
  trackingURL?: string;
  /** @format date-time */
  updatedAt?: number;
  updatedBy?: string;
  user?: string;
}
