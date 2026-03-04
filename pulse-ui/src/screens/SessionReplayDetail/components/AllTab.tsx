import { Box, Text, Badge, Group, Stack, Card, ScrollArea } from '@mantine/core';
import { IconCheck, IconX, IconChevronRight } from '@tabler/icons-react';
import type { SessionDetailData } from '../../../services/sessionReplay/mockSessionDetail';

interface AllTabProps {
  sessionData: SessionDetailData;
  onCriticalInteractionClick?: (t0: number, t1: number) => void;
}

export function AllTab({ sessionData, onCriticalInteractionClick }: AllTabProps) {
  const getStatusIcon = (status: "success" | "failed" | "not_attempted") => {
    if (status === 'success') return <IconCheck size={14} color="var(--mantine-color-teal-6)" />;
    if (status === 'failed') return <IconX size={14} color="var(--mantine-color-red-6)" />;
    return null;
  };

  const getStatusColor = (status: "success" | "failed" | "not_attempted") => {
    if (status === 'success') return 'teal';
    if (status === 'failed') return 'red';
    return 'gray';
  };

  const successCount = sessionData.criticalInteractions.filter(i => i.status === 'success').length;

  return (
    <Stack gap="lg">
      {/* User Journey */}
      <Box>
        <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="xs">
          User Journey
        </Text>
        <Card padding="sm" withBorder>
          <ScrollArea>
            <Group gap="xs" wrap="nowrap">
              {sessionData.journey.map((path, idx) => {
                const isError = path.toLowerCase().includes('error');
                const displayPath = path.startsWith('/') ? path.toUpperCase() : path.toUpperCase();
                return (
                  <Group key={idx} gap={4} wrap="nowrap">
                    <Badge
                      variant={isError ? 'filled' : 'light'}
                      size="sm"
                      color={isError ? 'red' : 'blue'}
                    >
                      {displayPath}
                    </Badge>
                    {idx < sessionData.journey.length - 1 && <IconChevronRight size={12} />}
                  </Group>
                );
              })}
            </Group>
          </ScrollArea>
        </Card>
      </Box>

      {/* Critical Interactions */}
      <Box>
        <Group justify="space-between" mb="xs">
          <Text size="xs" tt="uppercase" fw={600} c="dimmed">
            Critical Interactions
          </Text>
          <Badge size="xs" variant="light" color="blue">
            {successCount}/{sessionData.criticalInteractions.length} Successful
          </Badge>
        </Group>
        <Card padding="sm" withBorder>
          <Stack gap="sm">
            {sessionData.criticalInteractions.map((interaction) => {
              const handleClick = () => {
                if (interaction.timestamp !== undefined && interaction.latency !== undefined && onCriticalInteractionClick) {
                  const t0 = interaction.timestamp;
                  const t1 = interaction.timestamp + interaction.latency;
                  onCriticalInteractionClick(t0, t1);
                }
              };

              return (
                <Group 
                  key={interaction.interactionId} 
                  justify="space-between" 
                  wrap="nowrap"
                  style={{ 
                    cursor: interaction.timestamp !== undefined && interaction.latency !== undefined ? 'pointer' : 'default',
                  }}
                  onClick={handleClick}
                >
                  <Group gap="xs" wrap="nowrap">
                    {getStatusIcon(interaction.status)}
                    <Text size="sm" fw={500} style={{ flex: 1 }}>
                      {interaction.displayName}
                    </Text>
                  </Group>
                  <Badge size="sm" color={getStatusColor(interaction.status)} variant="light">
                    {interaction.status === 'success' ? 'SUCCESS' : interaction.status === 'failed' ? 'FAILED' : 'NOT ATTEMPTED'}
                  </Badge>
                </Group>
              );
            })}
          </Stack>
        </Card>
      </Box>

      {/* Network Requests */}
      <Box>
        <Text size="xs" tt="uppercase" fw={600} c="dimmed" mb="xs">
          Network Requests
        </Text>
        <Card padding="sm" withBorder>
          <Stack gap="xs">
            {sessionData.networkRequests.map((req, idx) => (
              <Group key={idx} justify="space-between" wrap="nowrap">
                <Text size="sm" ff="monospace" style={{ flex: 1 }}>
                  {req.method} {req.url}
                </Text>
                <Group gap="xs" wrap="nowrap">
                  <Badge
                    size="sm"
                    color={req.status >= 200 && req.status < 300 ? 'teal' : req.status >= 500 ? 'red' : 'yellow'}
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
    </Stack>
  );
}
