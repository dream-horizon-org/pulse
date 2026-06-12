import { IconExclamationCircle } from "@tabler/icons-react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useExceptionListData } from "./ExceptionTable/hooks";
import { ExceptionTable } from "./ExceptionTable";
import type { ExceptionRow } from "./ExceptionTable/ExceptionTable.interface";
import type { NonFatalIssue } from "../AppVitals.interface";

interface NonFatalListProps {
  startTime: string;
  endTime: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
  platform?: string;
  networkProvider?: string;
  state?: string;
  screenName?: string;
}

export const NonFatalList: React.FC<NonFatalListProps> = ({
  startTime,
  endTime,
  appVersion = "all",
  osVersion = "all",
  device = "all",
  platform = "all",
  networkProvider = "all",
  state = "all",
  screenName,
}) => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { projectId } = useParams<{ projectId: string }>();
  const { exceptions, queryState } = useExceptionListData({
    startTime,
    endTime,
    appVersion,
    osVersion,
    device,
    platform,
    networkProvider,
    state,
    screenName,
    exceptionType: "nonfatal",
  });

  const handleRowClick = (groupId: string) => {
    const qs = searchParams.toString();
    navigate(
      qs
        ? `/projects/${projectId}/app-vitals/${groupId}?${qs}`
        : `/projects/${projectId}/app-vitals/${groupId}`,
    );
  };

  // Transform exceptions to ExceptionRow format
  const exceptionRows: ExceptionRow[] = (exceptions as NonFatalIssue[]).map(
    (issue) => ({
      id: issue.id,
      title: issue.title,
      message: issue.message,
      issueType: issue.issueType,
      appVersions: issue.appVersion,
      occurrences: issue.occurrences,
      affectedUsers: issue.affectedUsers,
      firstSeen: issue.firstSeen,
      lastSeen: issue.lastSeen,
    }),
  );

  return (
    <ExceptionTable
      title="Non-Fatal Issues"
      icon={<IconExclamationCircle size={18} color="#3b82f6" />}
      iconColor="#3b82f6"
      badgeColor="blue"
      emptyIcon="ℹ️"
      emptyMessage="No non-fatal issues reported"
      exceptions={exceptionRows}
      isLoading={queryState.isLoading}
      isError={queryState.isError}
      errorMessage={queryState.errorMessage}
      onRowClick={handleRowClick}
      showTypeColumn={true}
    />
  );
};
