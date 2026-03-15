import { useState } from "react";
import { Box, Table, Text, Select, Badge, Group, Loader } from "@mantine/core";
import { IconArrowUp, IconArrowDown } from "@tabler/icons-react";
import {
  FunnelStepResult,
  FunnelStep,
  FunnelGroupedRow,
  useGetFunnelGrouped,
} from "../../../hooks/useGetFunnelData";
import { formatDuration, GROUP_BY_OPTIONS } from "../mockData";
import classes from "../FunnelAnalysis.module.css";

interface FunnelDataTableProps {
  steps: FunnelStepResult[];
  timeRange: { start: string; end: string };
  apiSteps: FunnelStep[];
}

type SortField = "stepName" | "count" | "conversionRate" | "dropoffRate";

export function FunnelDataTable({ steps, timeRange, apiSteps }: FunnelDataTableProps) {
  const [sortField, setSortField] = useState<SortField>("count");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const [groupBy, setGroupBy] = useState("none");

  const { data: groupedData, isLoading: groupedLoading } = useGetFunnelGrouped({
    requestBody: {
      steps: apiSteps,
      timeRange,
      mode: "UNIQUE_USERS",
      groupBy,
    },
    enabled: groupBy !== "none",
  });

  const groupedRows: FunnelGroupedRow[] = groupedData?.data?.groups ?? [];

  const toggleSort = (field: SortField) => {
    if (sortField === field) {
      setSortDir(sortDir === "asc" ? "desc" : "asc");
    } else {
      setSortField(field);
      setSortDir("desc");
    }
  };

  const SortIcon = ({ field }: { field: SortField }) => {
    if (sortField !== field) return null;
    return sortDir === "asc" ? <IconArrowUp size={12} /> : <IconArrowDown size={12} />;
  };

  const sortSteps = <T extends { stepName?: string; count?: number; conversionRate?: number; dropoffRate?: number }>(list: T[]): T[] => {
    return [...list].sort((a, b) => {
      const aVal = (a as any)[sortField] ?? 0;
      const bVal = (b as any)[sortField] ?? 0;
      if (aVal < bVal) return sortDir === "asc" ? -1 : 1;
      if (aVal > bVal) return sortDir === "asc" ? 1 : -1;
      return 0;
    });
  };

  const renderHeader = () => (
    <Table.Thead>
      <Table.Tr>
        <Table.Th style={{ width: 40 }}>#</Table.Th>
        <Table.Th style={{ cursor: "pointer" }} onClick={() => toggleSort("stepName")}>
          <Group gap={4}>Step Name <SortIcon field="stepName" /></Group>
        </Table.Th>
        <Table.Th style={{ cursor: "pointer", textAlign: "right" }} onClick={() => toggleSort("count")}>
          <Group gap={4} justify="flex-end">Completed <SortIcon field="count" /></Group>
        </Table.Th>
        <Table.Th style={{ cursor: "pointer", textAlign: "right" }} onClick={() => toggleSort("conversionRate")}>
          <Group gap={4} justify="flex-end">Conversion % <SortIcon field="conversionRate" /></Group>
        </Table.Th>
        <Table.Th style={{ cursor: "pointer", textAlign: "right" }} onClick={() => toggleSort("dropoffRate")}>
          <Group gap={4} justify="flex-end">Drop-off % <SortIcon field="dropoffRate" /></Group>
        </Table.Th>
        {groupBy !== "none" && (
          <Table.Th style={{ textAlign: "right" }}>Median Time</Table.Th>
        )}
      </Table.Tr>
    </Table.Thead>
  );

  const renderRows = (list: any[], offset = 0) =>
    sortSteps(list).map((step: any, i: number) => (
      <Table.Tr key={`${step.stepName}-${i}`}>
        <Table.Td><Text size="xs" c="dimmed">{offset + i + 1}</Text></Table.Td>
        <Table.Td><Text size="sm" fw={500}>{step.stepName}</Text></Table.Td>
        <Table.Td style={{ textAlign: "right" }}>
          <Text size="sm" fw={600}>{step.count.toLocaleString()}</Text>
        </Table.Td>
        <Table.Td style={{ textAlign: "right" }}>
          <Badge variant="light" color={step.conversionRate >= 50 ? "teal" : "orange"} size="sm">
            {step.conversionRate}%
          </Badge>
        </Table.Td>
        <Table.Td style={{ textAlign: "right" }}>
          {step.dropoffRate > 0 ? (
            <Badge variant="light" color={step.dropoffRate > 30 ? "red" : "yellow"} size="sm">
              {step.dropoffRate}%
            </Badge>
          ) : (
            <Text size="xs" c="dimmed">—</Text>
          )}
        </Table.Td>
        {groupBy !== "none" && (
          <Table.Td style={{ textAlign: "right" }}>
            <Text size="sm">
              {step.medianTimeToStep != null ? formatDuration(step.medianTimeToStep) : "—"}
            </Text>
          </Table.Td>
        )}
      </Table.Tr>
    ));

  return (
    <Box className={classes.tableSection}>
      <Box className={classes.tableSectionHeader}>
        <Text className={classes.tableSectionTitle}>Step Breakdown</Text>
        <Select
          data={GROUP_BY_OPTIONS}
          value={groupBy}
          onChange={(val) => setGroupBy(val || "none")}
          size="xs"
          style={{ width: 180 }}
          allowDeselect={false}
        />
      </Box>

      {groupBy === "none" ? (
        <Table striped highlightOnHover fz="sm">
          {renderHeader()}
          <Table.Tbody>{renderRows(steps)}</Table.Tbody>
        </Table>
      ) : groupedLoading ? (
        <Box py="xl" style={{ display: "flex", justifyContent: "center" }}>
          <Loader color="teal" size="sm" />
        </Box>
      ) : (
        groupedRows.map((group) => (
          <Box key={group.groupValue}>
            <Box px="lg" py="xs" bg="gray.0" style={{ borderBottom: "1px solid #f1f3f5" }}>
              <Text size="xs" fw={700} c="dark.5" tt="uppercase">
                {groupBy}: {group.groupValue}
              </Text>
            </Box>
            <Table striped highlightOnHover fz="sm">
              {renderHeader()}
              <Table.Tbody>{renderRows(group.steps)}</Table.Tbody>
            </Table>
          </Box>
        ))
      )}
    </Box>
  );
}
