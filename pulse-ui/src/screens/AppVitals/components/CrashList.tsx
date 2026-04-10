import { IconBug } from "@tabler/icons-react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useExceptionListData } from "./ExceptionTable/hooks";
import { ExceptionTable } from "./ExceptionTable";
import type { ExceptionRow } from "./ExceptionTable/ExceptionTable.interface";
import type { CrashIssue } from "../AppVitals.interface";

interface CrashListProps {
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

export const CrashList: React.FC<CrashListProps> = ({
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
    exceptionType: "crash",
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
  const exceptionRows: ExceptionRow[] = (exceptions as CrashIssue[]).map(
    (crash) => ({
      id: crash.id,
      title: crash.title,
      message: crash.message,
      appVersions: crash.appVersion,
      occurrences: crash.occurrences,
      affectedUsers: crash.affectedUsers,
      firstSeen: crash.firstSeen,
      lastSeen: crash.lastSeen,
    }),
  );

  return (
    <ExceptionTable
      title="Crashes"
      icon={<IconBug size={18} color="#ef4444" />}
      iconColor="#ef4444"
      badgeColor="red"
      emptyIcon="🐛"
      emptyMessage="No crashes reported"
      exceptions={exceptionRows}
      isLoading={queryState.isLoading}
      isError={queryState.isError}
      errorMessage={queryState.errorMessage}
      onRowClick={handleRowClick}
    />
  );
};
