import { QueryList } from "../QueryList";
import { useGetSuggestedQueries } from "../../../../hooks/useGetSuggestedQueries";
import { SuggestedQuery } from "../../../../hooks/useGetSuggestedQueries/useGetSuggestedQueries.interface";

export const SuggestedQueriesContent: React.FC = () => {
  const { data: response, isLoading } = useGetSuggestedQueries();

  const data: SuggestedQuery[] = response?.data?.suggestedQuery || [];

  const queries = (data || []).map((item) => ({
    header: item?.queryName,
    subHeader: item?.description,
    query: item?.query,
  }));

  return (
    <QueryList
      loadMessage="Fetching Suggestions..."
      data={queries}
      response={response}
      isLoading={isLoading}
    />
  );
};
