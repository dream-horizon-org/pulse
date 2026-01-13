import { useGetQueryHistory, QueryHistoryItem } from "../../../../hooks/useGetQueryHistory";
import { QueryList } from "../QueryList";

export const QueryHistoryContent: React.FC = () => {
  const { data: response, isLoading } = useGetQueryHistory();

  const queries: QueryHistoryItem[] = response?.data?.queries || [];

  // Transform QueryHistoryItem[] to the format expected by QueryList
  const resolvedData = queries.map((item) => ({
    query: item.query,
    queryId: item.queryId,
    status: item.status,
    submittedAt: item.submittedAt,
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
