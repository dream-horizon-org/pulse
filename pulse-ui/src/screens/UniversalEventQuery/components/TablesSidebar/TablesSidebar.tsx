import { Divider, Text, Title } from "@mantine/core";
import { useUniversalQueryTables } from "../../../../hooks/useUniversalQueryTables";
import { UNIVERSAL_QUERY_TEXTS } from "../../UniversalEventQuery.constants";
import { TableItem } from "../TableItem/TableItem";
import classes from "./TablesSidebar.module.css";

type TablesSidebarProps = {
  onInsert: (text: string) => void;
};
export const TablesSidebar = ({ onInsert }: TablesSidebarProps) => {
  const {
    data: tables,
    isLoading,
    isFetching,
    isError,
  } = useUniversalQueryTables();

  const isFetchingTables = isLoading || isFetching;
  const hasTables = tables?.data?.length && !isFetchingTables && !isError;

  return (
    <div className={classes.tablesSidebar}>
      <Title order={6}>{UNIVERSAL_QUERY_TEXTS.TABLES}</Title>
      <Divider my="sm" />
      {isFetchingTables && (
        <Text className={classes.tableHeader}>
          {UNIVERSAL_QUERY_TEXTS.LOADING_TABLES}
        </Text>
      )}
      {hasTables ? (
        tables?.data?.map((table) => (
          <TableItem
            key={table.name}
            tableName={table.name}
            onInsert={onInsert}
          />
        ))
      ) : (
        <Text>{UNIVERSAL_QUERY_TEXTS.NO_TABLES}</Text>
      )}
    </div>
  );
};
