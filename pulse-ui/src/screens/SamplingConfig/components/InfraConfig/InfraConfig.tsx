/**
 * Infrastructure Configuration Component
 * Configures signals and interaction endpoints
 * Updated to be editable with default values
 */

import { Box, Text, Group, Paper, Stack, TextInput, Alert, Tooltip } from '@mantine/core';
import { IconServer, IconInfoCircle, IconLink } from '@tabler/icons-react';
import { SignalsConfig, InteractionConfig } from '../../SamplingConfig.interface';
import { UI_CONSTANTS } from '../../SamplingConfig.constants';
import classes from '../../SamplingConfig.module.css';

interface InfraConfigProps {
  signals: SignalsConfig;
  interaction: InteractionConfig;
  onSignalsChange?: (signals: SignalsConfig) => void;
  onInteractionChange?: (interaction: InteractionConfig) => void;
  disabled?: boolean;
}

export function InfraConfig({ 
  signals, 
  interaction, 
  onSignalsChange,
  onInteractionChange,
  disabled = false,
}: InfraConfigProps) {
  
  const handleSignalChange = (field: keyof SignalsConfig, value: string | number) => {
    if (onSignalsChange) {
      onSignalsChange({ ...signals, [field]: value });
    }
  };

  const handleInteractionChange = (field: keyof InteractionConfig, value: string | number) => {
    if (onInteractionChange) {
      onInteractionChange({ ...interaction, [field]: value });
    }
  };

  return (
    <Box className={classes.card}>
      <Box className={classes.cardHeader}>
        <Box className={classes.cardHeaderLeft}>
          <Box className={classes.cardIcon} style={{ background: 'linear-gradient(135deg, #dbeafe 0%, #bfdbfe 100%)', color: '#2563eb' }}>
            <IconServer size={20} />
          </Box>
          <Box>
            <Text className={classes.cardTitle}>Infrastructure Settings</Text>
            <Text className={classes.cardDescription}>
              Configure collector endpoints and SDK connection settings
            </Text>
          </Box>
        </Box>
      </Box>
      
      <Box className={classes.cardContent}>
        {/* Explanation */}
        <Alert 
          icon={<IconInfoCircle size={18} />} 
          color="blue" 
          variant="light" 
          mb="lg"
          title="Collector Configuration"
        >
          <Text size="xs">
            Configure where the SDK sends telemetry data. These URLs should point to your 
            OpenTelemetry collector endpoints. Leave empty to use backend-configured defaults.
          </Text>
          <Text size="xs" mt="xs" c="dimmed">
            💡 <strong>Tip:</strong> For Android emulator, use <code>http://10.0.2.2:4318</code>. 
            For production, use your infrastructure's collector endpoints.
          </Text>
        </Alert>

        <Stack gap="lg">
          {/* Signals Configuration */}
          <Paper withBorder p="md">
            <Group gap="xs" mb="md">
              <IconLink size={18} style={{ color: '#2563eb' }} />
              <Text fw={600}>{UI_CONSTANTS.SECTIONS.SIGNALS.TITLE}</Text>
              <Tooltip label="OpenTelemetry Protocol (OTLP) endpoints for traces, logs, and metrics" withArrow>
                <IconInfoCircle size={14} style={{ color: '#868e96', cursor: 'help' }} />
              </Tooltip>
            </Group>
            
            <Stack gap="md">
              <TextInput
                label="Logs Collector URL"
                description="OTLP endpoint for log records"
                placeholder="http://10.0.2.2:4318/v1/logs"
                value={signals.logsCollectorUrl || ''}
                onChange={(e) => handleSignalChange('logsCollectorUrl', e.currentTarget.value)}
                disabled={disabled}
                leftSection={<IconLink size={16} />}
              />

              <TextInput
                label="Metrics Collector URL"
                description="OTLP endpoint for metric data"
                placeholder="http://10.0.2.2:4318/v1/metrics"
                value={signals.metricCollectorUrl || ''}
                onChange={(e) => handleSignalChange('metricCollectorUrl', e.currentTarget.value)}
                disabled={disabled}
                leftSection={<IconLink size={16} />}
              />

              <TextInput
                label="Spans Collector URL"
                description="OTLP endpoint for trace spans"
                placeholder="http://10.0.2.2:4318/v1/traces"
                value={signals.spanCollectorUrl || ''}
                onChange={(e) => handleSignalChange('spanCollectorUrl', e.currentTarget.value)}
                disabled={disabled}
                leftSection={<IconLink size={16} />}
              />
            </Stack>
          </Paper>

          {/* Interaction Configuration */}
          <Paper withBorder p="md">
            <Group gap="xs" mb="md">
              <IconLink size={18} style={{ color: '#7c3aed' }} />
              <Text fw={600}>{UI_CONSTANTS.SECTIONS.INTERACTION.TITLE}</Text>
              <Tooltip label="User interaction tracking endpoints for performance monitoring" withArrow>
                <IconInfoCircle size={14} style={{ color: '#868e96', cursor: 'help' }} />
              </Tooltip>
            </Group>
            
            <Stack gap="md">
              <TextInput
                label="Config URL"
                description="Endpoint where SDK fetches its remote configuration"
                placeholder="http://10.0.2.2:8080/v1/interaction-configs/"
                value={interaction.configUrl || ''}
                onChange={(e) => handleInteractionChange('configUrl', e.currentTarget.value)}
                disabled={disabled}
                leftSection={<IconLink size={16} />}
              />
            </Stack>
          </Paper>
        </Stack>
      </Box>
    </Box>
  );
}
