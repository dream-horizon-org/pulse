import { ActionIcon, Box, Code, Collapse, Flex, Text } from "@mantine/core";
import {
  IconChevronDown,
  IconChevronRight,
  IconPlus,
} from "@tabler/icons-react";
import { useState } from "react";
import { queryClient } from "../../../../clients/react-query";
import { API_ROUTES } from "../../../../constants";
import { ApiResponse } from "../../../../helpers/makeRequest";
import { useUniversalQueryTableColumns } from "../../../../hooks/useUniversalQueryTableColumns";
import { UniversalQueryTablesSuccessResponseBody } from "../../../../hooks/useUniversalQueryTables";
import { UNIVERSAL_QUERY_TEXTS } from "../../UniversalEventQuery.constants";
import classes from "./TableItem.module.css";

type TableItemProps = {
  tableName: string;
  onInsert: (text: string) => void;
};
export const TableItem = ({ tableName, onInsert }: TableItemProps) => {
  const result = queryClient.getQueryData([
    API_ROUTES.GET_UNIVERSAL_QUERY_COLUMNS.key,
    tableName,
  ]) as ApiResponse<UniversalQueryTablesSuccessResponseBody>;

  const [expanded, setExpanded] = useState(false);

  const { data, isFetching: isFetchingColumns } = useUniversalQueryTableColumns(
    tableName,
    !result?.data && expanded,
  );

  return (
    <Box className={classes.tableItem}>
      <Flex align="center" justify="space-between">
        <Flex align="center">
          <ActionIcon
            onClick={() => {
              setExpanded((prev) => !prev);
            }}
            variant="white"
          >
            {expanded ? (
              <IconChevronDown size={18} />
            ) : (
              <IconChevronRight size={18} />
            )}
          </ActionIcon>
          <Text className={classes.tableName}>{tableName}</Text>
        </Flex>
        <ActionIcon onClick={() => onInsert(tableName)} variant="white">
          <IconPlus size={20} />
        </ActionIcon>
      </Flex>

      <Collapse in={expanded} pl={"lg"}>
        <Box pl={"lg"}>
          {isFetchingColumns ? (
            <Text size="sm" c="gray">
              {UNIVERSAL_QUERY_TEXTS.LOADING_COLUMNS}
            </Text>
          ) : (
            data?.data?.map((col) => (
              <Flex key={col.name} justify="space-between" align="center">
                <Code className={classes.tableColumn}>{col.name}</Code>
                <Flex justify={"center"} align={"center"}>
                  <Text className={classes.tableColumnType}>
                    {col.type.toUpperCase()}
                  </Text>
                  <ActionIcon
                    onClick={() => onInsert(col.name)}
                    variant="white"
                  >
                    <IconPlus size={20} />
                  </ActionIcon>
                </Flex>
              </Flex>
            ))
          )}
        </Box>
      </Collapse>
    </Box>
  );
};
