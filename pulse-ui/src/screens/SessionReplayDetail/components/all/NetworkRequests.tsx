import { Box, Text, Card, Group, Badge, Stack } from "@mantine/core";
import type { NetworkRequest } from "../../../../services/sessionReplay/mockSessionDetail";
import { HEADERS } from "../../constants/strings";

interface NetworkRequestsProps {
  networkRequests: NetworkRequest[];
}

export function NetworkRequests({ networkRequests }: NetworkRequestsProps) {
  return (
    <Box>
      <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="xs">
        {HEADERS.NETWORK_REQUESTS}
      </Text>
      <Card padding="sm" withBorder>
        <Stack gap="xs">
          {networkRequests.map((req, idx) => (
            <Group key={idx} justify="space-between" wrap="nowrap">
              <Text size="sm" ff="monospace" style={{ flex: 1 }}>
                {req.method} {req.url}
              </Text>
              <Group gap="xs" wrap="nowrap">
                <Badge
                  size="sm"
                  color={
                    req.status >= 200 && req.status < 300
                      ? "teal"
                      : req.status >= 500
                        ? "red"
                        : "yellow"
                  }
                  variant="light"
                >
                  {req.status}
                </Badge>
                <Text size="sm" c="dimmed">
                  {req.duration}ms
                </Text>
              </Group>
            </Group>
          ))}
        </Stack>
      </Card>
    </Box>
  );
}
