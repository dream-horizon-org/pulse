import { Table } from "@mantine/core";
import {
  IconArrowUp,
  IconArrowDown,
  IconArrowsSort,
} from "@tabler/icons-react";
import type { SessionItem } from "../../../services/sessionReplay";
import type { SortField, SortDirection } from "../../../services/sessionReplay";
import { TABLE_COLUMN_LABELS } from "../constants/sessionList.constants";
import { SessionTableRow } from "./SessionTableRow";

export interface SessionsTableProps {
  sortBy: SortField;
  sortDirection: SortDirection;
  onSort: (field: SortField) => void;
  sessions: SessionItem[];
  onSessionClick: (sessionId: string) => void;
}

function SortIcon({
  column,
  currentSortBy,
  sortDirection,
}: {
  column: SortField;
  currentSortBy: SortField;
  sortDirection: SortDirection;
}) {
  const isActive = currentSortBy === column;
  const Icon = isActive
    ? sortDirection === "ASC"
      ? IconArrowUp
      : IconArrowDown
    : IconArrowsSort;

  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        width: 18,
        marginLeft: 2,
        verticalAlign: "middle",
      }}
    >
      <Icon
        size={14}
        style={{
          opacity: isActive ? 1 : 0.35,
          color: isActive ? "var(--mantine-color-teal-6)" : undefined,
        }}
      />
    </span>
  );
}

const SORTABLE_COLUMNS: SortField[] = [
  "START_TIME",
  "DURATION",
  "QUALITY_SCORE",
];

export function SessionsTable({
  sortBy,
  sortDirection,
  onSort,
  sessions,
  onSessionClick,
}: SessionsTableProps) {
  const thStyle = (column: SortField) =>
    SORTABLE_COLUMNS.includes(column)
      ? {
          cursor: "pointer" as const,
          userSelect: "none" as const,
        }
      : undefined;

  return (
    <Table
      highlightOnHover
      horizontalSpacing="md"
      verticalSpacing="sm"
      layout="fixed"
    >
      <Table.Thead>
        <Table.Tr>
          <Table.Th
            style={{ ...thStyle("START_TIME"), width: "16%" }}
            onClick={() => onSort("START_TIME")}
          >
            {TABLE_COLUMN_LABELS.startTime}
            <SortIcon
              column="START_TIME"
              currentSortBy={sortBy}
              sortDirection={sortDirection}
            />
          </Table.Th>
          <Table.Th
            style={{ ...thStyle("DURATION"), width: "11%" }}
            onClick={() => onSort("DURATION")}
          >
            {TABLE_COLUMN_LABELS.duration}
            <SortIcon
              column="DURATION"
              currentSortBy={sortBy}
              sortDirection={sortDirection}
            />
          </Table.Th>
          <Table.Th style={{ width: "11%" }}>
            {TABLE_COLUMN_LABELS.user}
          </Table.Th>
          <Table.Th
            style={{ ...thStyle("QUALITY_SCORE"), width: "10%" }}
            onClick={() => onSort("QUALITY_SCORE")}
          >
            {TABLE_COLUMN_LABELS.quality}
            <SortIcon
              column="QUALITY_SCORE"
              currentSortBy={sortBy}
              sortDirection={sortDirection}
            />
          </Table.Th>
          <Table.Th style={{ width: "12%" }}>
            {TABLE_COLUMN_LABELS.issues}
          </Table.Th>
          <Table.Th style={{ width: "10%" }}>
            {TABLE_COLUMN_LABELS.platform}
          </Table.Th>
          <Table.Th style={{ width: "30%" }}>
            {TABLE_COLUMN_LABELS.impactedScreens}
          </Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {sessions.map((session) => (
          <SessionTableRow
            key={session.sessionId}
            session={session}
            onSessionClick={onSessionClick}
          />
        ))}
      </Table.Tbody>
    </Table>
  );
}
