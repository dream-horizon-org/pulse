import { Table, Badge, Stack, Text, Box } from "@mantine/core";
import { VitalsByScreenTableProps } from "./VitalsByScreenTable.interface";
import { ChartSkeleton } from "../../../../components/Skeletons/ChartSkeleton";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState/ErrorAndEmptyState";
import { getVitalRating } from "../../WebVitals.constants";
import { normalizeScreenVital } from "../../normalizeWebVitalsApi";
import { ratingToColorName } from "../utils/ratingToColor";
import classes from "./VitalsByScreenTable.module.css";

function formatCount(value: number): string {
  const safe = Number.isFinite(value) ? Math.trunc(value) : 0;
  return safe.toLocaleString();
}

export function VitalsByScreenTable({
  data,
  isLoading,
  error,
}: VitalsByScreenTableProps) {
  if (isLoading) {
    return <ChartSkeleton height={300} title="By Screen" />;
  }

  if (error) {
    return (
      <ErrorAndEmptyState
        message="Error loading table data"
        description={error.message}
      />
    );
  }

  if (!data || data.length === 0) {
    return (
      <ErrorAndEmptyState
        message="No data available"
        description="No screen data for this vital"
      />
    );
  }

  const normalizedRows = data.map((row) => normalizeScreenVital(row));

  if (normalizedRows.length === 0) {
    return (
      <ErrorAndEmptyState
        message="No data available"
        description="No screen data for this vital"
      />
    );
  }

  const sortedData = [...normalizedRows]
    .sort((a, b) => b.totalCount - a.totalCount)
    .slice(0, 20);

  const rows = sortedData.map((item, index) => {
    const rating = getVitalRating(item.p75, "LCP");
    const colorName = ratingToColorName(rating);
    const rowKey = item.screenName !== "" ? item.screenName : `row-${index}`;

    return (
      <Table.Tr key={rowKey}>
        <Table.Td>{item.screenName}</Table.Td>
        <Table.Td>{Math.round(item.p75)} ms</Table.Td>
        <Table.Td>{formatCount(item.totalCount)}</Table.Td>
        <Table.Td>
          <Badge color={colorName} variant="light" size="sm">
            {Math.round(item.goodPct)}%
          </Badge>
        </Table.Td>
      </Table.Tr>
    );
  });

  return (
    <Stack gap="md">
      <Text fw={600} size="md">
        By Screen
      </Text>
      <Box className={classes.tableWrapper}>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Screen Name</Table.Th>
              <Table.Th>P75</Table.Th>
              <Table.Th>Count</Table.Th>
              <Table.Th>Good %</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>{rows}</Table.Tbody>
        </Table>
      </Box>
    </Stack>
  );
}
