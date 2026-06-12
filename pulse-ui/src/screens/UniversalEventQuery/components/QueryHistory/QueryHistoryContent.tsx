import { useGetQueryHistory, QueryHistoryItem } from "../../../../hooks/useGetQueryHistory";
import { QueryList } from "../QueryList";

export const QueryHistoryContent: React.FC = () => {
  const { data: response, isLoading } = useGetQueryHistory();

  const queries: QueryHistoryItem[] = response?.data?.queries || [];

  // Transform QueryHistoryItem[] to the format expected by QueryList
  const resolvedData = queries.map((item) => ({
    query: item.queryString,
    queryId: item.jobId,
    status: item.status,
    submittedAt: item.createdAt ? new Date(item.createdAt).toISOString() : undefined,
  }));

  return (
    <QueryList
      loadMessage="Fetching History..."
      data={resolvedData}
      response={response}
      isLoading={isLoading}
    />
  );
};
