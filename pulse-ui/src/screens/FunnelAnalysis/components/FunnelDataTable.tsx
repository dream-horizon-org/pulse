import { useState } from "react";
import { Box, Table, Text, Select, Badge, Group } from "@mantine/core";
import { IconArrowUp, IconArrowDown } from "@tabler/icons-react";
import {
  MockFunnelStep,
  MockFunnelGroupedRow,
  MOCK_GROUPED_DATA,
  formatDuration,
} from "../mockData";
import classes from "../FunnelAnalysis.module.css";

interface FunnelDataTableProps {
  steps: MockFunnelStep[];
}

type SortField =
  | "stepName"
  | "completed"
  | "conversionRate"
  | "dropoffRate"
  | "medianTimeToStep";

const GROUP_OPTIONS = [
  { value: "none", label: "No grouping" },
  { value: "OS", label: "Group by OS" },
];

export function FunnelDataTable({ steps }: FunnelDataTableProps) {
  const [sortField, setSortField] = useState<SortField>("completed");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const [groupBy, setGroupBy] = useState("none");

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
    return sortDir === "asc" ? (
      <IconArrowUp size={12} />
    ) : (
      <IconArrowDown size={12} />
    );
  };

  const sortSteps = (list: MockFunnelStep[]) => {
    return [...list].sort((a, b) => {
      let aVal: number | string;
      let bVal: number | string;
      switch (sortField) {
        case "stepName":
          aVal = a.eventName;
          bVal = b.eventName;
          break;
        case "completed":
          aVal = a.completed;
          bVal = b.completed;
          break;
        case "conversionRate":
          aVal = a.conversionRate;
          bVal = b.conversionRate;
          break;
        case "dropoffRate":
          aVal = a.dropoffRate;
          bVal = b.dropoffRate;
          break;
        case "medianTimeToStep":
          aVal = a.medianTimeToStep ?? 0;
          bVal = b.medianTimeToStep ?? 0;
          break;
        default:
          return 0;
      }
      if (aVal < bVal) return sortDir === "asc" ? -1 : 1;
      if (aVal > bVal) return sortDir === "asc" ? 1 : -1;
      return 0;
    });
  };

  const renderHeader = () => (
    <Table.Thead>
      <Table.Tr>
        <Table.Th style={{ width: 40 }}>#</Table.Th>
        <Table.Th
          style={{ cursor: "pointer" }}
          onClick={() => toggleSort("stepName")}
        >
          <Group gap={4}>
            Step Name <SortIcon field="stepName" />
          </Group>
        </Table.Th>
        <Table.Th
          style={{ cursor: "pointer", textAlign: "right" }}
          onClick={() => toggleSort("completed")}
        >
          <Group gap={4} justify="flex-end">
            Completed <SortIcon field="completed" />
          </Group>
        </Table.Th>
        <Table.Th
          style={{ cursor: "pointer", textAlign: "right" }}
          onClick={() => toggleSort("conversionRate")}
        >
          <Group gap={4} justify="flex-end">
            Conversion % <SortIcon field="conversionRate" />
          </Group>
        </Table.Th>
        <Table.Th
          style={{ cursor: "pointer", textAlign: "right" }}
          onClick={() => toggleSort("dropoffRate")}
        >
          <Group gap={4} justify="flex-end">
            Drop-off % <SortIcon field="dropoffRate" />
          </Group>
        </Table.Th>
        <Table.Th
          style={{ cursor: "pointer", textAlign: "right" }}
          onClick={() => toggleSort("medianTimeToStep")}
        >
          <Group gap={4} justify="flex-end">
            Median Time <SortIcon field="medianTimeToStep" />
          </Group>
        </Table.Th>
      </Table.Tr>
    </Table.Thead>
  );

  const renderRows = (list: MockFunnelStep[], offset = 0) =>
    sortSteps(list).map((step, i) => (
      <Table.Tr key={step.id}>
        <Table.Td>
          <Text size="xs" c="dimmed">
            {offset + i + 1}
          </Text>
        </Table.Td>
        <Table.Td>
          <Text size="sm" fw={500}>
            {step.eventName}
          </Text>
        </Table.Td>
        <Table.Td style={{ textAlign: "right" }}>
          <Text size="sm" fw={600}>
            {step.completed.toLocaleString()}
          </Text>
        </Table.Td>
        <Table.Td style={{ textAlign: "right" }}>
          <Badge
            variant="light"
            color={step.conversionRate >= 50 ? "teal" : "orange"}
            size="sm"
          >
            {step.conversionRate}%
          </Badge>
        </Table.Td>
        <Table.Td style={{ textAlign: "right" }}>
          {step.dropoffRate > 0 ? (
            <Badge
              variant="light"
              color={step.dropoffRate > 30 ? "red" : "yellow"}
              size="sm"
            >
              {step.dropoffRate}%
            </Badge>
          ) : (
            <Text size="xs" c="dimmed">
              —
            </Text>
          )}
        </Table.Td>
        <Table.Td style={{ textAlign: "right" }}>
          <Text size="sm">
            {step.medianTimeToStep !== null
              ? formatDuration(step.medianTimeToStep)
              : "—"}
          </Text>
        </Table.Td>
      </Table.Tr>
    ));

  const groupedData: MockFunnelGroupedRow[] | null =
    groupBy !== "none" ? MOCK_GROUPED_DATA[groupBy] || null : null;

  return (
    <Box className={classes.tableSection}>
      <Box className={classes.tableSectionHeader}>
        <Text className={classes.tableSectionTitle}>Step Breakdown</Text>
        <Select
          data={GROUP_OPTIONS}
          value={groupBy}
          onChange={(val) => setGroupBy(val || "none")}
          size="xs"
          style={{ width: 180 }}
          allowDeselect={false}
        />
      </Box>

      {!groupedData ? (
        <Table striped highlightOnHover fz="sm">
          {renderHeader()}
          <Table.Tbody>{renderRows(steps)}</Table.Tbody>
        </Table>
      ) : (
        groupedData.map((group) => (
          <Box key={group.groupValue}>
            <Box
              px="lg"
              py="xs"
              bg="gray.0"
              style={{ borderBottom: "1px solid #f1f3f5" }}
            >
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
