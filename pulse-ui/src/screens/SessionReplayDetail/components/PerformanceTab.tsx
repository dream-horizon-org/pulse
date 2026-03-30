import {
  Stack,
  Text,
  Badge,
  Accordion,
  Code,
  Box,
  Paper,
  Center,
  ThemeIcon,
} from "@mantine/core";
import { IconHeartbeat } from "@tabler/icons-react";
import type { SessionDetailData } from "../../../services/sessionReplay/mockSessionDetail";
import { SessionDetailTabPanel } from "./SessionDetailTabPanel";
import { TAB_PANEL_DESCRIPTION, TAB_PANEL_TITLE } from "../constants/strings";

const EXCEPTION_FIELD_INDEX = {
  timestamp: 0,
  eventName: 1,
  title: 2,
  exceptionMessage: 3,
  exceptionType: 4,
  screenName: 5,
  traceId: 6,
  spanId: 7,
  groupId: 8,
  pulseType: 9,
} as const;

function getRowValue(
  row: (string | number | null)[],
  key: keyof typeof EXCEPTION_FIELD_INDEX,
): string {
  const v = row[EXCEPTION_FIELD_INDEX[key]];
  return v != null ? String(v) : "";
}

interface PerformanceTabProps {
  sessionData: SessionDetailData;
}

export function PerformanceTab({ sessionData }: PerformanceTabProps) {
  const { exceptions } = sessionData;
  const hasExceptions =
    exceptions?.rows &&
    Array.isArray(exceptions.rows) &&
    exceptions.rows.length > 0;

  return (
    <SessionDetailTabPanel
      title={TAB_PANEL_TITLE.PERFORMANCE}
      description={TAB_PANEL_DESCRIPTION.PERFORMANCE}
    >
      <Stack gap="md">
        {hasExceptions ? (
          <Accordion variant="contained">
            {exceptions.rows.map((row, index) => {
              const title = getRowValue(row, "title");
              const timestamp = getRowValue(row, "timestamp");
              const pulseType = getRowValue(row, "pulseType");
              const stackTrace = getRowValue(row, "exceptionMessage");
              const traceId = getRowValue(row, "traceId");
              const spanId = getRowValue(row, "spanId");
              return (
                <Accordion.Item key={index} value={`exception-${index}`}>
                  <Accordion.Control>
                    <Box
                      style={{
                        display: "flex",
                        alignItems: "center",
                        gap: 8,
                      }}
                    >
                      <Text size="sm" fw={600}>
                        {title || "Exception"}
                      </Text>
                      {pulseType && (
                        <Badge size="sm" variant="light" color="red">
                          {pulseType}
                        </Badge>
                      )}
                      {timestamp && (
                        <Text size="xs" c="dimmed">
                          {timestamp}
                        </Text>
                      )}
                    </Box>
                  </Accordion.Control>
                  <Accordion.Panel>
                    <Stack gap="xs">
                      {(traceId || spanId) && (
                        <Text size="xs" c="dimmed">
                          traceId: {traceId || "—"} · spanId: {spanId || "—"}
                        </Text>
                      )}
                      {stackTrace && (
                        <Code
                          block
                          style={{ whiteSpace: "pre-wrap", fontSize: 11 }}
                        >
                          {stackTrace}
                        </Code>
                      )}
                    </Stack>
                  </Accordion.Panel>
                </Accordion.Item>
              );
            })}
          </Accordion>
        ) : (
          <Paper withBorder p="xl" radius="md" bg="gray.0">
            <Center>
              <Stack align="center" gap="md" maw={420}>
                <ThemeIcon size={56} radius="md" variant="light" color="teal">
                  <IconHeartbeat size={28} stroke={1.5} />
                </ThemeIcon>
                <Text size="sm" c="dimmed" ta="center" lh={1.6}>
                  No exceptions or crashes were recorded for this session. When
                  issues occur, stack traces and trace IDs will appear here.
                </Text>
              </Stack>
            </Center>
          </Paper>
        )}
      </Stack>
    </SessionDetailTabPanel>
  );
}
