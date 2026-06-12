import { useState } from "react";
import {
  Box,
  Text,
  Paper,
  Table,
  ScrollArea,
  Badge,
  Group,
  SegmentedControl,
  TextInput,
  ActionIcon,
} from "@mantine/core";
import { IconFileText, IconCode, IconSearch, IconX } from "@tabler/icons-react";
import classes from "./LogsAndStackTrace.module.css";

interface Log {
  timestamp: string;
  level: string;
  tag: string;
  message: string;
}

interface StackFrame {
  className: string;
  method: string;
  file: string;
  line: number;
}

interface LogsAndStackTraceProps {
  logs: Log[];
  stackTrace: StackFrame[];
}

export const LogsAndStackTrace: React.FC<LogsAndStackTraceProps> = ({
  logs,
  stackTrace,
}) => {
  const [view, setView] = useState("logs");
  const [searchQuery, setSearchQuery] = useState("");

  const getLevelColor = (level: string) => {
    const colors: Record<string, string> = {
      ERROR: "red",
      WARN: "orange",
      INFO: "blue",
      DEBUG: "gray",
      VERBOSE: "gray",
    };
    return colors[level] || "gray";
  };

  // Filter logs based on search query
  const filteredLogs = logs.filter((log) => {
    if (!searchQuery) return true;
    const query = searchQuery.toLowerCase();
    return (
      log.message.toLowerCase().includes(query) ||
      log.tag.toLowerCase().includes(query) ||
      log.level.toLowerCase().includes(query) ||
      log.timestamp.includes(query)
    );
  });

  // Filter stack trace based on search query
  const filteredStackTrace = stackTrace.filter((trace) => {
    if (!searchQuery) return true;
    const query = searchQuery.toLowerCase();
    return (
      trace.className.toLowerCase().includes(query) ||
      trace.method.toLowerCase().includes(query) ||
      trace.file.toLowerCase().includes(query) ||
      trace.line.toString().includes(query)
    );
  });

  const handleClearSearch = () => {
    setSearchQuery("");
  };

  return (
    <Paper className={classes.logsCard}>
      <Box className={classes.topAccent} />

      <Group justify="space-between" align="center" mb="md">
        <Group gap="sm">
          {view === "logs" ? (
            <IconFileText size={20} color="#0ba09a" />
          ) : (
            <IconCode size={20} color="#0ba09a" />
          )}
          <Text className={classes.cardTitle}>
            {view === "logs" ? "Captured Logs" : "Stack Trace"}
          </Text>
        </Group>
        <SegmentedControl
          value={view}
          onChange={(value) => setView(value)}
          data={[
            { label: "Logs", value: "logs" },
            { label: "Stack Trace", value: "stackTrace" },
          ]}
          size="sm"
          className={classes.viewToggle}
        />
      </Group>

      {/* Search Bar */}
      <TextInput
        placeholder={
          view === "logs"
            ? "Search logs by message, tag, level, or timestamp..."
            : "Search stack trace by class, method, file, or line number..."
        }
        value={searchQuery}
        onChange={(e) => setSearchQuery(e.target.value)}
        leftSection={<IconSearch size={16} />}
        rightSection={
          searchQuery && (
            <ActionIcon
              size="sm"
              variant="subtle"
              color="gray"
              onClick={handleClearSearch}
            >
              <IconX size={16} />
            </ActionIcon>
          )
        }
        mb="md"
        className={classes.searchInput}
      />

      {/* Content */}
      {view === "logs" ? (
        <ScrollArea style={{ height: 500 }}>
          <Table className={classes.logsTable}>
            <Table.Thead className={classes.stickyHeader}>
              <Table.Tr>
                <Table.Th>Timestamp</Table.Th>
                <Table.Th>Level</Table.Th>
                <Table.Th>Tag</Table.Th>
                <Table.Th style={{ minWidth: 400 }}>Message</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {filteredLogs.length === 0 ? (
                <Table.Tr>
                  <Table.Td colSpan={4}>
                    <Text ta="center" py="xl" c="dimmed">
                      No logs found matching "{searchQuery}"
                    </Text>
                  </Table.Td>
                </Table.Tr>
              ) : (
                filteredLogs.map((log, index) => (
                  <Table.Tr
                    key={index}
                    className={log.level === "ERROR" ? classes.errorRow : ""}
                  >
                    <Table.Td className={classes.timestampCell}>
                      {log.timestamp}
                    </Table.Td>
                    <Table.Td>
                      <Badge
                        size="sm"
                        variant="filled"
                        color={getLevelColor(log.level)}
                        style={{ minWidth: 70 }}
                      >
                        {log.level}
                      </Badge>
                    </Table.Td>
                    <Table.Td className={classes.tagCell}>{log.tag}</Table.Td>
                    <Table.Td className={classes.messageCell}>
                      {log.message}
                    </Table.Td>
                  </Table.Tr>
                ))
              )}
            </Table.Tbody>
          </Table>
        </ScrollArea>
      ) : (
        <ScrollArea style={{ height: 500 }}>
          <Table className={classes.stackTable}>
            <Table.Thead className={classes.stickyHeader}>
              <Table.Tr>
                <Table.Th style={{ width: 50 }}>#</Table.Th>
                <Table.Th style={{ minWidth: 300 }}>Class</Table.Th>
                <Table.Th style={{ minWidth: 200 }}>Method</Table.Th>
                <Table.Th style={{ minWidth: 250 }}>File</Table.Th>
                <Table.Th style={{ width: 100 }}>Line</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {filteredStackTrace.length === 0 ? (
                <Table.Tr>
                  <Table.Td colSpan={5}>
                    <Text ta="center" py="xl" c="dimmed">
                      No stack frames found matching "{searchQuery}"
                    </Text>
                  </Table.Td>
                </Table.Tr>
              ) : (
                filteredStackTrace.map((trace, index) => (
                  <Table.Tr
                    key={index}
                    className={index === 0 ? classes.primaryFrame : ""}
                  >
                    <Table.Td className={classes.indexCell}>{index}</Table.Td>
                    <Table.Td className={classes.classCell}>
                      {trace.className}
                    </Table.Td>
                    <Table.Td className={classes.methodCell}>
                      {trace.method}
                    </Table.Td>
                    <Table.Td className={classes.fileCell}>
                      {trace.file}
                    </Table.Td>
                    <Table.Td className={classes.lineCell}>
                      {trace.line}
                    </Table.Td>
                  </Table.Tr>
                ))
              )}
            </Table.Tbody>
          </Table>
        </ScrollArea>
      )}

      {/* Footer with counts */}
      <Group gap="sm" mt="md">
        {view === "logs" ? (
          <>
            <Badge
              size="md"
              variant="outline"
              color={searchQuery ? "teal" : "gray"}
            >
              {filteredLogs.length} of {logs.length} logs
            </Badge>
            {searchQuery && (
              <Badge
                size="md"
                variant="light"
                color="teal"
                rightSection={
                  <ActionIcon
                    size="xs"
                    color="teal"
                    radius="xl"
                    variant="transparent"
                    onClick={handleClearSearch}
                  >
                    <IconX size={12} />
                  </ActionIcon>
                }
              >
                Filtered by: "{searchQuery}"
              </Badge>
            )}
          </>
        ) : (
          <>
            <Badge
              size="md"
              variant="outline"
              color={searchQuery ? "teal" : "gray"}
            >
              {filteredStackTrace.length} of {stackTrace.length} stack frames
            </Badge>
            {searchQuery && (
              <Badge
                size="md"
                variant="light"
                color="teal"
                rightSection={
                  <ActionIcon
                    size="xs"
                    color="teal"
                    radius="xl"
                    variant="transparent"
                    onClick={handleClearSearch}
                  >
                    <IconX size={12} />
                  </ActionIcon>
                }
              >
                Filtered by: "{searchQuery}"
              </Badge>
            )}
          </>
        )}
      </Group>
    </Paper>
  );
};
