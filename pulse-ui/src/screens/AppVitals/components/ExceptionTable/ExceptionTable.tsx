import { Table, Text, Badge, Box } from "@mantine/core";
import dayjs from "dayjs";
import type { ExceptionTableProps } from "./ExceptionTable.interface";
import { TableSkeleton } from "../../../../components/Skeletons";
import classes from "../../AppVitals.module.css";

/**
 * Formats app versions string into a range (first - last)
 * Example: "2.3.0, 2.3.5, 2.4.0" -> "2.3.0 - 2.4.0"
 */
function formatAppVersionRange(appVersions: string): string {
  if (!appVersions || appVersions.trim() === "") {
    return "-";
  }

  // Split by comma and clean up
  const versions = appVersions
    .split(",")
    .map((v) => v.trim())
    .filter((v) => v.length > 0);

  if (versions.length === 0) {
    return "-";
  }

  if (versions.length === 1) {
    return versions[0];
  }

  // Sort versions (semantic versioning)
  const sortedVersions = versions.sort((a, b) => {
    const aParts = a.split(".").map(Number);
    const bParts = b.split(".").map(Number);

    for (let i = 0; i < Math.max(aParts.length, bParts.length); i++) {
      const aPart = aParts[i] || 0;
      const bPart = bParts[i] || 0;
      if (aPart !== bPart) {
        return aPart - bPart;
      }
    }
    return 0;
  });

  const first = sortedVersions[0];
  const last = sortedVersions[sortedVersions.length - 1];

  return first === last ? first : `${first} - ${last}`;
}

export const ExceptionTable: React.FC<ExceptionTableProps> = ({
  title,
  icon,
  iconColor,
  badgeColor,
  emptyIcon,
  emptyMessage,
  exceptions,
  isLoading,
  isError,
  errorMessage,
  onRowClick,
  showTypeColumn = false,
}) => {
  if (isLoading) {
    return (
      <Box className={classes.issueListTable}>
        <Box className={classes.tableHeader}>
          <Box className={classes.tableHeaderContent}>
            {icon}
            <Text className={classes.tableHeaderTitle}>{title}</Text>
          </Box>
        </Box>
        <Box className={classes.issueTableWrapper}>
          <TableSkeleton columns={showTypeColumn ? 7 : 6} rows={5} />
        </Box>
      </Box>
    );
  }

  if (isError) {
    return (
      <Box className={classes.issueListTable}>
        <Box className={classes.tableHeader}>
          <Box className={classes.tableHeaderContent}>
            {icon}
            <Text className={classes.tableHeaderTitle}>{title}</Text>
          </Box>
        </Box>
        <Box className={classes.issueTableWrapper} style={{ padding: "2rem" }}>
          <Text size="sm" c="red" ta="center">
            {errorMessage || "Failed to load data"}
          </Text>
        </Box>
      </Box>
    );
  }

  if (exceptions.length === 0) {
    return (
      <Box className={classes.issueListTable}>
        <Box className={classes.tableHeader}>
          <Box className={classes.tableHeaderContent}>
            {icon}
            <Text className={classes.tableHeaderTitle}>{title}</Text>
          </Box>
        </Box>
        <Box className={classes.emptyTableState}>
          <Box className={classes.emptyTableIcon}>{emptyIcon}</Box>
          <Text className={classes.emptyTableText}>{emptyMessage}</Text>
        </Box>
      </Box>
    );
  }

  return (
    <Box className={`${classes.issueListTable} ${classes.fadeIn}`}>
      <Box className={classes.tableHeader}>
        <Box className={classes.tableHeaderContent}>
          {icon}
          <Text className={classes.tableHeaderTitle}>{title}</Text>
          <Badge size="sm" variant="light" color={badgeColor} ml="auto">
            {exceptions.length}
          </Badge>
        </Box>
      </Box>
      <Box className={classes.issueTableWrapper}>
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Title</Table.Th>
              {showTypeColumn && <Table.Th>Type</Table.Th>}
              <Table.Th>App Versions</Table.Th>
              <Table.Th>Occurrences</Table.Th>
              <Table.Th>Affected Users</Table.Th>
              <Table.Th>First Seen</Table.Th>
              <Table.Th>Last Seen</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {exceptions.map((exception) => {
              return (
                <Table.Tr
                  key={exception.id}
                  onClick={() => onRowClick(exception.id)}
                  style={{ cursor: "pointer" }}
                >
                  <Table.Td>
                    <Text fw={500} size="sm" lineClamp={1}>
                      {exception.title || "Untitled Exception"}
                    </Text>
                  </Table.Td>
                  {showTypeColumn && (
                    <Table.Td>
                      <Badge size="sm" variant="light" color={badgeColor}>
                        {exception.issueType || "Unknown"}
                      </Badge>
                    </Table.Td>
                  )}
                  <Table.Td>
                    <span className={classes.appVersionCell}>
                      {formatAppVersionRange(exception.appVersions)}
                    </span>
                  </Table.Td>
                  <Table.Td>
                    <Box className={classes.badgeCell}>
                      <Badge size="sm" variant="light" color={badgeColor}>
                        {exception.occurrences.toLocaleString()}
                      </Badge>
                    </Box>
                  </Table.Td>
                  <Table.Td>
                    <Text size="sm" c="dimmed">
                      {exception.affectedUsers.toLocaleString()}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text className={classes.dateCell}>
                      {exception.firstSeen &&
                      exception.firstSeen !== "-" &&
                      dayjs(exception.firstSeen).isValid()
                      // better readable format for date and time
                        ? dayjs(exception.firstSeen).format("MMM D, YYYY HH:mm:ss")
                        : "-"}
                    </Text>
                  </Table.Td>
                  <Table.Td>
                    <Text className={classes.dateCell}>
                      {exception.lastSeen &&
                      exception.lastSeen !== "-" &&
                      dayjs(exception.lastSeen).isValid()
                        ? dayjs(exception.lastSeen).format("MMM D, YYYY HH:mm:ss")
                        : "-"}
                    </Text>
                  </Table.Td>
                </Table.Tr>
              );
            })}
          </Table.Tbody>
        </Table>
      </Box>
    </Box>
  );
};
