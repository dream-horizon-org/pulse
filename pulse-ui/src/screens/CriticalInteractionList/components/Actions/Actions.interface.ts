import { InteractionDetailsResponse } from "../../../../helpers/getInteractionDetails";

export type ActionProps = {
  status?: string;
  notficationColor?: string;
  successNotificationColor?: string;
  errorNotificationColor?: string;
  iconColor?: string;
  jobId?: number;
  name?: string;
  createdBy?: string;
  setNewJobId?: (jobId: number | undefined) => void;
  refetchJobList?: () => void;
  isLoading?: boolean;
  refetchJobStatus?: () => void;
  refetchJobDetails?: () => void;
  onClick?: () => void;
  interactionDetails?: InteractionDetailsResponse;
};
