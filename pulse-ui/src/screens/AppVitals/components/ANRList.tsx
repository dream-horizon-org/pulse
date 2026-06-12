import { IconAlertTriangle } from "@tabler/icons-react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useExceptionListData } from "./ExceptionTable/hooks";
import { ExceptionTable } from "./ExceptionTable";
import type { ExceptionRow } from "./ExceptionTable/ExceptionTable.interface";
import type { ANRIssue } from "../AppVitals.interface";

interface ANRListProps {
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

export const ANRList: React.FC<ANRListProps> = ({
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
    exceptionType: "anr",
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
  const exceptionRows: ExceptionRow[] = (exceptions as ANRIssue[]).map(
    (anr) => ({
      id: anr.id,
      title: anr.title,
      message: anr.message,
      appVersions: anr.appVersion,
      occurrences: anr.occurrences,
      affectedUsers: anr.affectedUsers,
      firstSeen: anr.firstSeen,
      lastSeen: anr.lastSeen,
    }),
  );

  return (
    <ExceptionTable
      title="ANRs (Application Not Responding)"
      icon={<IconAlertTriangle size={18} color="#f59e0b" />}
      iconColor="#f59e0b"
      badgeColor="orange"
      emptyIcon="⚠️"
      emptyMessage="No ANRs reported"
      exceptions={exceptionRows}
      isLoading={queryState.isLoading}
      isError={queryState.isError}
      errorMessage={queryState.errorMessage}
      onRowClick={handleRowClick}
    />
  );
};
