import {
  IconArrowUp,
  IconArrowDown,
  IconArrowsSort,
} from "@tabler/icons-react";
import type { SessionItem } from "../../../services/sessionReplay";
import type { SortField, SortDirection } from "../../../services/sessionReplay";
import { TABLE_COLUMN_LABELS } from "../constants/sessionList.constants";
import { SessionTableRow } from "./SessionTableRow";
import classes from "./SessionsTable.module.css";

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
  iconClassName,
}: {
  column: SortField;
  currentSortBy: SortField;
  sortDirection: SortDirection;
  iconClassName: string;
}) {
  const isActive = currentSortBy === column;
  const Icon = isActive
    ? sortDirection === "ASC"
      ? IconArrowUp
      : IconArrowDown
    : IconArrowsSort;

  return (
    <span className={iconClassName}>
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

export function SessionsTable({
  sortBy,
  sortDirection,
  onSort,
  sessions,
  onSessionClick,
}: SessionsTableProps) {
  return (
    <div className={classes.root} role="grid" aria-label="Session list">
      <div className={classes.headerRow} role="row">
        <div
          className={`${classes.cell} ${classes.headerCellSortable}`}
          role="columnheader"
          onClick={() => onSort("START_TIME")}
        >
          <span className={classes.headerLabel}>
            {TABLE_COLUMN_LABELS.startTime}
          </span>
          <SortIcon
            column="START_TIME"
            currentSortBy={sortBy}
            sortDirection={sortDirection}
            iconClassName={classes.sortIcon}
          />
        </div>
        <div
          className={`${classes.cell} ${classes.headerCellSortable}`}
          role="columnheader"
          onClick={() => onSort("DURATION")}
        >
          <span className={classes.headerLabel}>
            {TABLE_COLUMN_LABELS.duration}
          </span>
          <SortIcon
            column="DURATION"
            currentSortBy={sortBy}
            sortDirection={sortDirection}
            iconClassName={classes.sortIcon}
          />
        </div>
        <div className={classes.cell} role="columnheader">
          {TABLE_COLUMN_LABELS.user}
        </div>
        <div
          className={`${classes.cell} ${classes.headerCellSortable}`}
          role="columnheader"
          onClick={() => onSort("QUALITY_SCORE")}
        >
          <span className={classes.headerLabel}>
            {TABLE_COLUMN_LABELS.quality}
          </span>
          <SortIcon
            column="QUALITY_SCORE"
            currentSortBy={sortBy}
            sortDirection={sortDirection}
            iconClassName={classes.sortIcon}
          />
        </div>
        <div className={classes.cell} role="columnheader">
          {TABLE_COLUMN_LABELS.platform}
        </div>
        <div className={classes.cell} role="columnheader">
          {TABLE_COLUMN_LABELS.issues}
        </div>
        <div className={classes.cell} role="columnheader">
          {TABLE_COLUMN_LABELS.impactedScreens}
        </div>
      </div>
      {sessions.map((session) => (
        <SessionTableRow
          key={session.sessionId}
          session={session}
          onSessionClick={onSessionClick}
        />
      ))}
    </div>
  );
}
