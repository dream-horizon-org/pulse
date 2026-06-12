import { ReactNode } from "react";
import { ErrorType } from "../../utils/errorHandling";

export interface QueryStateProps {
  isLoading: boolean;
  isError: boolean;
  errorMessage?: string;
  errorType?: ErrorType;
  children?: ReactNode;
  loadingComponent?: ReactNode;
  emptyComponent?: ReactNode;
  emptyMessage?: string;
  skeletonTitle?: string;
  skeletonHeight?: number;
}
