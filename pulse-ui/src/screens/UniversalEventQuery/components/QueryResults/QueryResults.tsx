import { Text, useMantineTheme } from "@mantine/core";
import { DataTable } from "mantine-datatable";
import {
  RunUniversalQueryRow,
  RunUniversalQuerySuccessResponseBody,
} from "../../../../hooks/useQueryResultFromQueryId/useQueryResultFromQueryId.interface";
import { UNIVERSAL_QUERY_TEXTS } from "../../UniversalEventQuery.constants";
import classes from "./QueryResults.module.css";
import { useEffect, useRef } from "react";

type QueryResultsProps = {
  data: RunUniversalQuerySuccessResponseBody | undefined;
  isLoadingData: boolean;
  fetchMoreData: () => void;
};

const formatData = (columns: string[], rows: RunUniversalQueryRow[]) => {
  return rows.map((row) =>
    row.f.reduce(
      (acc, item, index) => ({
        ...acc,
        [`${columns[index]}`]: item.v
          ? Array.isArray(item.v)
            ? item.v.map((v) => v.v.toString()).join(", ")
            : item.v.toString()
          : "",
      }),
      {} as Record<string, string>,
    ),
  );
};

export const QueryResults = ({
  data,
  isLoadingData,
  fetchMoreData,
}: QueryResultsProps) => {
  const theme = useMantineTheme();
  // const columnsData = data?.schema.fields.map((field) => ({ accessor: field.name })) as Columns;

  const scrollViewportRef = useRef<HTMLDivElement>(null);

  let timeout: ReturnType<typeof setTimeout> | undefined;

  const loadMoreRecords = () => {
    if (data?.pageToken) {
      if (timeout) {
        clearTimeout(timeout);
      }
      timeout = setTimeout(() => {
        fetchMoreData();
      }, 1000);
    }
  };

  // Clear timeout on unmount
  useEffect(() => {
    return () => {
      if (timeout) clearTimeout(timeout);
    };
  }, [timeout]);

  return (
    <div className={classes.dataTableContainer}>
      <DataTable
        withTableBorder
        highlightOnHover
        fetching={isLoadingData}
        loaderType="bars"
        columns={
          data?.schema.fields
            ? data.schema.fields.map((field) => ({ accessor: field.name }))
            : []
        }
        records={
          data
            ? formatData(
                data?.schema.fields.map((field) => field.name),
                data.rows,
              )
            : []
        }
        // height={400}
        classNames={{
          header: classes.stickyHeader,
          root: classes.dataTableRoot,
        }}
        onScrollToBottom={loadMoreRecords}
        scrollViewportRef={scrollViewportRef}
      />
      {data && (
        <Text c={theme.colors.gray[6]} mt={theme.spacing.md}>
          {UNIVERSAL_QUERY_TEXTS.MAX_RECORDS_DISPLAY} Showing{" "}
          {data?.rows.length} of {data?.totalRows} records
        </Text>
      )}
    </div>
  );
  // return (
  //   <Paper shadow="xs" p="md">
  //     {queryResult ? (
  //       <>
  //         <DataTable
  //           withTableBorder
  //           striped
  //           highlightOnHover
  //           fetching={isRunningQuery}
  //           loaderType="bars"
  //           columns={data.columns}
  //           records={data.rows}
  //           height={400}
  //           classNames={{ header: classes.stickyHeader }}
  //         />
  //         <Text c={theme.colors.gray[6]} mt={theme.spacing.md}>
  //           {UNIVERSAL_QUERY_TEXTS.MAX_RECORDS_DISPLAY} Showing
  //           {data.rows.length} of {queryResult.totalRows} records
  //         </Text>
  //       </>
  //     ) : (
  //       <Text>{UNIVERSAL_QUERY_TEXTS.EMPTY_QUERY_RESULTS}</Text>
  //     )}
  //   </Paper>
  // );
};
