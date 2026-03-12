import { Table } from "@mantine/core";
import { IconArrowUp, IconArrowDown } from "@tabler/icons-react";
import type { SessionItem } from "../../../services/sessionReplay";
import type { SortField, SortDirection } from "../../../services/sessionReplay";
import {
  TABLE_COLUMN_LABELS,
  ACTIONS_COLUMN_WIDTH,
} from "../constants/sessionList.constants";
import { SessionTableRow } from "./SessionTableRow";
import classes from "../SessionReplaySessions.module.css";

export interface SessionsTableProps {
  sortBy: SortField;
  sortDirection: SortDirection;
  onSort: (field: SortField) => void;
  sessions: SessionItem[];
  onWatchSession: (sessionId: string) => void;
  onOpenSessionInNewTab: (sessionId: string) => void;
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
  if (currentSortBy !== column) return null;
  return sortDirection === "ASC" ? (
    <IconArrowUp size={14} style={{ verticalAlign: "middle", marginLeft: 4 }} />
  ) : (
    <IconArrowDown
      size={14}
      style={{ verticalAlign: "middle", marginLeft: 4 }}
    />
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
  onWatchSession,
  onOpenSessionInNewTab,
}: SessionsTableProps) {
  const thStyle = (column: SortField) =>
    SORTABLE_COLUMNS.includes(column)
      ? {
          cursor: "pointer" as const,
          userSelect: "none" as const,
          fontWeight: sortBy === column ? 600 : undefined,
        }
      : undefined;

  return (
    <Table highlightOnHover horizontalSpacing="md" verticalSpacing="sm">
      <Table.Thead>
        <Table.Tr>
          <Table.Th
            style={thStyle("START_TIME")}
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
            style={thStyle("DURATION")}
            onClick={() => onSort("DURATION")}
          >
            {TABLE_COLUMN_LABELS.duration}
            <SortIcon
              column="DURATION"
              currentSortBy={sortBy}
              sortDirection={sortDirection}
            />
          </Table.Th>
          <Table.Th>{TABLE_COLUMN_LABELS.user}</Table.Th>
          <Table.Th
            style={thStyle("QUALITY_SCORE")}
            onClick={() => onSort("QUALITY_SCORE")}
          >
            {TABLE_COLUMN_LABELS.quality}
            <SortIcon
              column="QUALITY_SCORE"
              currentSortBy={sortBy}
              sortDirection={sortDirection}
            />
          </Table.Th>
          <Table.Th>{TABLE_COLUMN_LABELS.issues}</Table.Th>
          <Table.Th>{TABLE_COLUMN_LABELS.platform}</Table.Th>
          <Table.Th>{TABLE_COLUMN_LABELS.journey}</Table.Th>
          <Table.Th style={{ width: ACTIONS_COLUMN_WIDTH }}>
            {TABLE_COLUMN_LABELS.actions}
          </Table.Th>
        </Table.Tr>
      </Table.Thead>
      <Table.Tbody>
        {sessions.map((session) => (
          <SessionTableRow
            key={session.sessionId}
            session={session}
            onWatch={onWatchSession}
            onOpenInNewTab={onOpenSessionInNewTab}
          />
        ))}
      </Table.Tbody>
    </Table>
  );
}
