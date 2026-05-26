import { IconBug } from "@tabler/icons-react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  useExceptionListInfiniteData,
  useExceptionListCount,
  useExceptionListSearch,
} from "./ExceptionTable/hooks";
import { ExceptionVirtualTable } from "./ExceptionTable/ExceptionVirtualTable";
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
  const { searchInput, setSearchInput, searchQuery, activeSearchQuery } =
    useExceptionListSearch();

  const listParams = {
    startTime,
    endTime,
    appVersion,
    osVersion,
    device,
    platform,
    networkProvider,
    state,
    screenName,
    searchQuery,
    exceptionType: "crash" as const,
  };

  const { exceptions, queryState, hasMore, fetchNextPage } =
    useExceptionListInfiniteData(listParams);

  const { count: totalCount } = useExceptionListCount(listParams);

  const handleRowClick = (groupId: string) => {
    const qs = searchParams.toString();
    navigate(
      qs
        ? `/projects/${projectId}/app-vitals/${groupId}?${qs}`
        : `/projects/${projectId}/app-vitals/${groupId}`,
    );
  };

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
    <ExceptionVirtualTable
      title="Crashes"
      icon={<IconBug size={18} color="#ef4444" />}
      badgeColor="red"
      emptyIcon="🐛"
      emptyMessage="No crashes reported"
      exceptions={exceptionRows}
      totalCount={totalCount}
      isLoading={queryState.isLoading}
      isError={queryState.isError}
      errorMessage={queryState.errorMessage}
      onRowClick={handleRowClick}
      onLoadMore={fetchNextPage}
      hasMore={hasMore}
      isFetchingMore={queryState.isLoadingMore}
      searchValue={searchInput}
      onSearchChange={setSearchInput}
      activeSearchQuery={activeSearchQuery}
    />
  );
};
