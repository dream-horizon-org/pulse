import type { CSSProperties } from "react";
import { Text, Badge, Box } from "@mantine/core";
import type { ExceptionRow } from "./ExceptionTable.interface";
import {
  formatAppVersionRange,
  formatExceptionTimestamp,
} from "./exceptionTableUtils";
import type { ExceptionListColumnWidths } from "./exceptionList.constants";
import { EXCEPTION_LIST_ROW_HEIGHT_PX } from "./exceptionList.constants";
import classes from "../../AppVitals.module.css";

const cellStyle: CSSProperties = {
  padding: "16px 16px",
  display: "flex",
  alignItems: "center",
  overflow: "hidden",
  flexShrink: 0,
};

export interface ExceptionVirtualRowProps {
  exception: ExceptionRow;
  badgeColor: string;
  columnWidths: ExceptionListColumnWidths;
  showTypeColumn?: boolean;
  onRowClick: (groupId: string) => void;
}

export function ExceptionVirtualRow({
  exception,
  badgeColor,
  columnWidths,
  showTypeColumn = false,
  onRowClick,
}: ExceptionVirtualRowProps) {
  return (
    <div
      role="row"
      tabIndex={0}
      onClick={() => onRowClick(exception.id)}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onRowClick(exception.id);
        }
      }}
      style={{
        display: "flex",
        alignItems: "center",
        height: EXCEPTION_LIST_ROW_HEIGHT_PX,
        width: "100%",
        cursor: "pointer",
        borderBottom: "1px solid rgba(14, 201, 194, 0.06)",
        boxSizing: "border-box",
      }}
      className={classes.exceptionVirtualRow}
    >
      <div style={{ ...cellStyle, width: columnWidths.title }}>
        <Text fw={500} size="sm" lineClamp={2}>
          {exception.title || "Untitled Exception"}
        </Text>
      </div>
      {showTypeColumn && "type" in columnWidths && (
        <div style={{ ...cellStyle, width: columnWidths.type }}>
          <Badge size="sm" variant="light" color={badgeColor}>
            {exception.issueType || "Unknown"}
          </Badge>
        </div>
      )}
      <div style={{ ...cellStyle, width: columnWidths.appVersions }}>
        <span className={classes.appVersionCell}>
          {formatAppVersionRange(exception.appVersions)}
        </span>
      </div>
      <div style={{ ...cellStyle, width: columnWidths.occurrences }}>
        <Box className={classes.badgeCell}>
          <Badge size="sm" variant="light" color={badgeColor}>
            {exception.occurrences.toLocaleString()}
          </Badge>
        </Box>
      </div>
      <div style={{ ...cellStyle, width: columnWidths.affectedUsers }}>
        <Text size="sm" c="dimmed">
          {exception.affectedUsers.toLocaleString()}
        </Text>
      </div>
      <div style={{ ...cellStyle, width: columnWidths.firstSeen }}>
        <Text className={classes.dateCell}>
          {formatExceptionTimestamp(exception.firstSeen)}
        </Text>
      </div>
      <div style={{ ...cellStyle, width: columnWidths.lastSeen }}>
        <Text className={classes.dateCell}>
          {formatExceptionTimestamp(exception.lastSeen)}
        </Text>
      </div>
    </div>
  );
}
