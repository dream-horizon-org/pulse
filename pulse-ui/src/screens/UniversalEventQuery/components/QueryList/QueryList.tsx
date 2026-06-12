import { useCallback, useState } from "react";
import { Pagination, Box } from "@mantine/core";
import { QueryListItem, QueryListItemProps } from "../QueryListItem";
import classes from "./QueryList.module.css";
import { LoaderWithMessage } from "../../../../components/LoaderWithMessage";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import { ApiResponse } from "../../../../helpers/makeRequest";
import { GetQueryHistoryResponse } from "../../../../hooks/useGetQueryHistory/useGetQueryHistory.interface";
import { GetSuggestedQueriesResponse } from "../../../../hooks/useGetSuggestedQueries/useGetSuggestedQueries.interface";

interface QueryListProps {
  data: QueryListItemProps[];
  response?: ApiResponse<GetQueryHistoryResponse | GetSuggestedQueriesResponse>;
  isLoading: boolean;
  loadMessage?: string;
}

export const QueryList: React.FC<QueryListProps> = ({
  data,
  response,
  isLoading,
  loadMessage,
}) => {
  const renderLoader = useCallback(
    () => (
      <Box className={classes.loader}>
        <LoaderWithMessage loadingMessage={loadMessage} />
      </Box>
    ),
    [loadMessage],
  );

  const renderError = useCallback(
    () => (
      <ErrorAndEmptyState
        classes={[classes.error]}
        message={
          response?.error?.cause ||
          response?.error?.message ||
          "Something went wrong"
        }
      />
    ),
    [response],
  );

  const itemsPerPage = 10;
  const [activePage, setActivePage] = useState<number>(1);

  const startIndex = (activePage - 1) * itemsPerPage;
  const paginatedData = data.slice(startIndex, startIndex + itemsPerPage);

  const rows = paginatedData.map((query, key) => (
    <QueryListItem key={startIndex + key} {...query} />
  ));

  if (isLoading) return renderLoader();
  if (response?.error) return renderError();

  return (
    <div className={classes.queryListContainer}>
      <div className={classes.tableScrollable}>{rows}</div>

      <Pagination
        total={Math.ceil(data.length / itemsPerPage)}
        value={activePage}
        onChange={setActivePage}
        size="sm"
      />
    </div>
  );
};
