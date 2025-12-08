/**
 * Infrastructure Configuration Component (Read-Only)
 * Displays signals and interaction configuration
 */

import { Box, Text, Group, Badge, Paper, Stack, CopyButton, ActionIcon, Tooltip, Alert } from '@mantine/core';
import { IconServer, IconCopy, IconCheck, IconLock, IconInfoCircle } from '@tabler/icons-react';
import { SignalsConfig, InteractionConfig } from '../../SamplingConfig.interface';
import { UI_CONSTANTS } from '../../SamplingConfig.constants';
import classes from '../../SamplingConfig.module.css';

interface InfraConfigProps {
  signals: SignalsConfig;
  interaction: InteractionConfig;
}

export function InfraConfig({ signals, interaction }: InfraConfigProps) {
  const ConfigRow = ({ label, value, copyable = false }: { label: string; value: string | number; copyable?: boolean }) => (
    <Group justify="space-between" py="xs" style={{ borderBottom: '1px solid var(--mantine-color-gray-2)' }}>
      <Text size="sm" c="dimmed">{label}</Text>
      <Group gap="xs">
        <Text size="sm" fw={500} ff={typeof value === 'string' && value.includes('://') ? 'monospace' : undefined}>
          {value}
        </Text>
        {copyable && (
          <CopyButton value={String(value)}>
            {({ copied, copy }) => (
              <Tooltip label={copied ? 'Copied!' : 'Copy'}>
                <ActionIcon size="xs" variant="subtle" onClick={copy}>
                  {copied ? <IconCheck size={12} /> : <IconCopy size={12} />}
                </ActionIcon>
              </Tooltip>
            )}
          </CopyButton>
        )}
      </Group>
    </Group>
  );

  return (
    <Box className={classes.card} style={{ opacity: 0.9 }}>
      <Box className={classes.cardHeader}>
        <Box className={classes.cardHeaderLeft}>
          <Box className={classes.cardIcon} style={{ background: 'linear-gradient(135deg, #f3f4f6 0%, #e5e7eb 100%)', color: '#6b7280' }}>
            <IconServer size={20} />
          </Box>
          <Box>
            <Group gap="xs">
              <Text className={classes.cardTitle}>Infrastructure Settings</Text>
              <Badge size="xs" variant="light" color="gray" leftSection={<IconLock size={10} />}>
                Read-only
              </Badge>
            </Group>
            <Text className={classes.cardDescription}>
              These settings are managed by your infrastructure team
            </Text>
          </Box>
        </Box>
      </Box>
      
      <Box className={classes.cardContent}>
        {/* Explanation */}
        <Alert 
          icon={<IconInfoCircle size={18} />} 
          color="gray" 
          variant="light" 
          mb="lg"
          title="Infrastructure Settings"
        >
          <Text size="xs">
            These settings control where telemetry data is sent and how it's processed. 
            They are managed by your platform/infrastructure team and are shown here for reference only.
          </Text>
          <Text size="xs" mt="xs" c="dimmed">
            <strong>Collector URLs</strong> are the endpoints that receive your telemetry data. 
            <strong>Attributes to Drop</strong> are sensitive fields automatically removed before sending.
          </Text>
        </Alert>

        <Stack gap="lg">
          {/* Signals Configuration */}
          <Paper withBorder p="md">
            <Group gap="xs" mb="md">
              <Text fw={600}>{UI_CONSTANTS.SECTIONS.SIGNALS.TITLE}</Text>
              <Tooltip label="Signals include traces, logs, and metrics sent via OpenTelemetry protocol" withArrow>
                <IconInfoCircle size={14} style={{ color: '#868e96', cursor: 'help' }} />
              </Tooltip>
            </Group>
            <Stack gap={0}>
              <ConfigRow label="Schedule Duration" value={`${signals.scheduleDurationMs} ms`} />
              <ConfigRow label="Collector URL" value={signals.collectorUrl} copyable />
              <Group justify="space-between" py="xs">
                <Text size="sm" c="dimmed">Attributes to Drop</Text>
                <Group gap="xs">
                  {signals.attributesToDrop.map(attr => (
                    <Badge key={attr} size="xs" variant="outline" color="red">
                      {attr}
                    </Badge>
                  ))}
                </Group>
              </Group>
            </Stack>
          </Paper>

          {/* Interaction Configuration */}
          <Paper withBorder p="md">
            <Group gap="xs" mb="md">
              <Text fw={600}>{UI_CONSTANTS.SECTIONS.INTERACTION.TITLE}</Text>
              <Tooltip label="Interactions are user journey events tracked for performance monitoring" withArrow>
                <IconInfoCircle size={14} style={{ color: '#868e96', cursor: 'help' }} />
              </Tooltip>
            </Group>
            <Stack gap={0}>
              <ConfigRow label="Collector URL" value={interaction.collectorUrl} copyable />
              <ConfigRow label="Config URL" value={interaction.configUrl} copyable />
              <ConfigRow label="Before Init Queue Size" value={interaction.beforeInitQueueSize} />
            </Stack>
          </Paper>
        </Stack>
      </Box>
    </Box>
  );
}

