import { Box, Text } from "@mantine/core";
import type { ConsoleLog } from "../../../services/sessionReplay/mockSessionDetail";
import classes from "./ConsoleLogList.module.css";

function levelClass(level: ConsoleLog["level"]) {
  if (level === "error") return classes.levelErr;
  if (level === "warn") return classes.levelWarn;
  return classes.levelLog;
}

function levelLabel(level: ConsoleLog["level"]) {
  if (level === "error") return "ERR";
  if (level === "warn") return "WRN";
  return "LOG";
}

function formatOffset(ms: number): string {
  if (!Number.isFinite(ms)) return "+0.00s";
  const s = ms / 1000;
  const sign = s >= 0 ? "+" : "";
  return `${sign}${s.toFixed(2)}s`;
}

interface ConsoleLogListProps {
  logs: ConsoleLog[];
}

export function ConsoleLogList({ logs }: ConsoleLogListProps) {
  return (
    <Box
      className={classes.terminal}
      component="section"
      aria-label="Console log output"
    >
      <div className={classes.rowList}>
        {logs.map((log, i) => (
          <div
            key={`${log.timestamp}-${i}`}
            className={classes.row}
          >
            <span className={classes.time}>{formatOffset(log.timestamp)}</span>
            <span
              className={`${classes.levelPill} ${levelClass(log.level)}`}
            >
              {levelLabel(log.level)}
            </span>
            <span className={classes.message}>{log.message}</span>
          </div>
        ))}
      </div>
      {logs.some((l) => l.stackTrace) ? (
        <Box mt="sm" pt="sm" className={classes.stackSection}>
          {logs
            .filter((l) => l.stackTrace)
            .map((log, i) => (
              <Box key={`stack-${i}`} mb="sm">
                <Text size="xs" c="dimmed" mb={4} ff="monospace">
                  Stack ({formatOffset(log.timestamp)})
                </Text>
                <pre className={classes.stack}>{log.stackTrace}</pre>
              </Box>
            ))}
        </Box>
      ) : null}
    </Box>
  );
}
