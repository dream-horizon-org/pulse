import { IconExclamationCircle } from "@tabler/icons-react";
import { useNavigate, useParams } from "react-router-dom";
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
  screenName?: string;
}

export const NonFatalList: React.FC<NonFatalListProps> = ({
  startTime,
  endTime,
  appVersion = "all",
  osVersion = "all",
  device = "all",
  screenName,
}) => {
  const navigate = useNavigate();
  const { projectId } = useParams<{ projectId: string }>();
  const { exceptions, queryState } = useExceptionListData({
    startTime,
    endTime,
    appVersion,
    osVersion,
    device,
    screenName,
    exceptionType: "nonfatal",
  });

  const handleRowClick = (groupId: string) => {
    navigate(`/projects/${projectId}/app-vitals/${groupId}`);
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
