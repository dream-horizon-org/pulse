import { useState } from "react";
import { Badge, Box, Group, Loader, Table, Text } from "@mantine/core";
import { IconArrowDown, IconArrowUp } from "@tabler/icons-react";
import {
  FunnelGroupedRow,
  FunnelStep,
  FunnelStepResult,
  useGetFunnelGrouped,
} from "../../../hooks/useGetFunnelData";
import { FunnelMode } from "../../../services/funnels.service";
import { formatDuration } from "../FunnelJourneyCreate.util";
import classes from "../FunnelCreate.module.css";

interface FunnelDataTableProps {
  steps: FunnelStepResult[];
  timeRange: { start: string; end: string };
  apiSteps: FunnelStep[];
  /**
   * Analysis grouping key for the parent funnel. Forwarded to the grouped-data
   * request so breakdowns match the saved funnel's denominator. Defaults to
   * UNIQUE_USERS when omitted.
   */
  mode?: FunnelMode;
  /** ISO-4217 currency for revenue formatting. When set, revenue columns render. */
  currency?: string | null;
}

type SortField = "stepName" | "count" | "conversionRate" | "dropoffRate";

/**
 * Formats a numeric value using Intl currency style when an ISO-4217 code is
 * supplied; otherwise falls back to a plain locale-grouped number. Returns "—"
 * for null/undefined to keep table cells visually quiet.
 */
function formatMoney(v: number | null | undefined, currency?: string | null): string {
  if (v === null || v === undefined || Number.isNaN(v)) return "—";
  if (currency) {
    try {
      return new Intl.NumberFormat(undefined, {
        style: "currency",
        currency,
        maximumFractionDigits: 2,
      }).format(v);
    } catch {
      /* fall through to bare number */
    }
  }
  return v.toLocaleString(undefined, { maximumFractionDigits: 2 });
}

export function FunnelDataTable({
  steps,
  timeRange,
  apiSteps,
  mode = FunnelMode.UNIQUE_USERS,
  currency,
}: FunnelDataTableProps) {
  const [sortField, setSortField] = useState<SortField>("count");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const hasRevenue = steps.some(
    (s) => s.revenue != null || s.avgOrderValue != null || s.lostRevenue != null,
  );
  // eslint-disable-next-line
  const [groupBy, setGroupBy] = useState("none");

  const { data: groupedData, isLoading: groupedLoading } = useGetFunnelGrouped({
    requestBody: {
      steps: apiSteps,
      timeRange,
      mode,
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
    return sortDir === "asc" ? (
      <IconArrowUp size={12} />
    ) : (
      <IconArrowDown size={12} />
    );
  };

  const sortSteps = <
    T extends {
      stepName?: string;
      count?: number;
      conversionRate?: number;
      dropoffRate?: number;
    },
  >(
    list: T[],
  ): T[] => {
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
          onClick={() => toggleSort("count")}
        >
          <Group gap={4} justify="flex-end">
            Completed <SortIcon field="count" />
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
        <Table.Th style={{ textAlign: "right" }}>Median Time</Table.Th>
        {hasRevenue && (
          <>
            <Table.Th style={{ textAlign: "right" }}>Revenue</Table.Th>
            <Table.Th style={{ textAlign: "right" }}>AOV</Table.Th>
            <Table.Th style={{ textAlign: "right" }}>Lost Revenue</Table.Th>
          </>
        )}
      </Table.Tr>
    </Table.Thead>
  );

  const renderRows = (list: any[], offset = 0) =>
    sortSteps(list).map((step: any, i: number) => (
      <Table.Tr key={`${step.stepName}-${i}`}>
        <Table.Td>
          <Text size="xs" c="dimmed">
            {offset + i + 1}
          </Text>
        </Table.Td>
        <Table.Td>
          <Text size="sm" fw={500}>
            {step.stepName}
          </Text>
        </Table.Td>
        <Table.Td style={{ textAlign: "right" }}>
          <Text size="sm" fw={600}>
            {step.count.toLocaleString()}
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
            {(step.medianTimeToStep ?? step.medianStepSeconds) != null
              ? formatDuration(step.medianTimeToStep ?? step.medianStepSeconds)
              : "—"}
          </Text>
        </Table.Td>
        {hasRevenue && (
          <>
            <Table.Td style={{ textAlign: "right" }}>
              <Text size="sm" fw={500}>
                {formatMoney(step.revenue, currency)}
              </Text>
            </Table.Td>
            <Table.Td style={{ textAlign: "right" }}>
              <Text size="sm">{formatMoney(step.avgOrderValue, currency)}</Text>
            </Table.Td>
            <Table.Td style={{ textAlign: "right" }}>
              {step.lostRevenue != null && step.lostRevenue > 0 ? (
                <Badge variant="light" color="red" size="sm">
                  {formatMoney(step.lostRevenue, currency)}
                </Badge>
              ) : (
                <Text size="xs" c="dimmed">—</Text>
              )}
            </Table.Td>
          </>
        )}
      </Table.Tr>
    ));

  return (
    <Box className={classes.tableSection}>
      <Box className={classes.tableSectionHeader}>
        <Text className={classes.tableSectionTitle}>Step Breakdown</Text>
        {/*<Select*/}
        {/*  data={GROUP_BY_OPTIONS}*/}
        {/*  value={groupBy}*/}
        {/*  onChange={(val) => setGroupBy(val || "none")}*/}
        {/*  size="xs"*/}
        {/*  style={{ width: 180 }}*/}
        {/*  allowDeselect={false}*/}
        {/*/>*/}
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
