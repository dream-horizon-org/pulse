import { useMemo, useState } from "react";
import { Box, Table, Text } from "@mantine/core";
import { IconTable, IconArrowUp, IconArrowDown, IconArrowsSort } from "@tabler/icons-react";
import { AiTableCardProps } from "./AiTableCard.interface";
import { TableErrorBoundary } from "./TableErrorBoundary";
import classes from "./AiTableCard.module.css";

type SortDir = "asc" | "desc" | null;

export const AiTableCard = ({ table }: AiTableCardProps) => {
  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortDir, setSortDir] = useState<SortDir>(null);

  const handleSort = (key: string) => {
    if (sortKey === key) {
      setSortDir((prev) => (prev === "asc" ? "desc" : prev === "desc" ? null : "asc"));
      if (sortDir === "desc") setSortKey(null);
    } else {
      setSortKey(key);
      setSortDir("asc");
    }
  };

  const sortedRows = useMemo(() => {
    if (!sortKey || !sortDir) return table.rows;
    const col = table.columns.find((c) => c.key === sortKey);
    const isNumber = col?.type === "number";
    return [...table.rows].sort((a, b) => {
      const va = a[sortKey];
      const vb = b[sortKey];
      let cmp: number;
      if (isNumber) {
        cmp = (Number(va) || 0) - (Number(vb) || 0);
      } else {
        cmp = String(va ?? "").localeCompare(String(vb ?? ""));
      }
      return sortDir === "desc" ? -cmp : cmp;
    });
  }, [table.rows, table.columns, sortKey, sortDir]);

  if (!table.columns?.length) return null;

  const SortIndicator = ({ colKey }: { colKey: string }) => {
    if (sortKey !== colKey) return <IconArrowsSort size={10} className={classes.sortIcon} />;
    if (sortDir === "asc") return <IconArrowUp size={10} className={classes.sortIcon} />;
    return <IconArrowDown size={10} className={classes.sortIcon} />;
  };

  return (
    <TableErrorBoundary tableConfig={table}>
      <Box className={classes.container}>
        <div className={classes.header}>
          <Text size="xs" fw={600} c="teal.7">
            <IconTable size={12} className={classes.headerIcon} />
            {table.title}
          </Text>
          <Text size="xs" c="dimmed">{table.rows.length} rows</Text>
        </div>
        <div className={classes.tableWrapper}>
          <Table className={classes.table} striped highlightOnHover withTableBorder={false}>
            <Table.Thead>
              <Table.Tr>
                {table.columns.map((col) => (
                  <Table.Th
                    key={col.key}
                    className={classes.sortableHeader}
                    onClick={() => handleSort(col.key)}
                  >
                    {col.label}
                    <SortIndicator colKey={col.key} />
                  </Table.Th>
                ))}
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {sortedRows.map((row, rowIdx) => (
                <Table.Tr key={rowIdx}>
                  {table.columns.map((col) => (
                    <Table.Td
                      key={col.key}
                      className={col.type === "number" ? classes.numberCell : undefined}
                    >
                      {String(row[col.key] ?? "")}
                    </Table.Td>
                  ))}
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </div>
      </Box>
    </TableErrorBoundary>
  );
};
