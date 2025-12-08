/**
 * Interaction Configuration Component
 * Manages interaction tracking endpoints and queue settings
 */

import { useState } from 'react';
import {
  Box,
  Text,
  Group,
  TextInput,
  NumberInput,
  Collapse,
  Stack,
} from '@mantine/core';
import {
  IconClick,
  IconChevronDown,
  IconChevronRight,
} from '@tabler/icons-react';
import { SectionProps } from '../../SamplingConfig.interface';
import { SAMPLING_CONFIG_CONSTANTS } from '../../SamplingConfig.constants';
import classes from '../../SamplingConfig.module.css';

export function InteractionConfig({ config, onUpdate }: SectionProps) {
  const [expanded, setExpanded] = useState(true);

  const { interaction } = config;

  const handleCollectorUrlChange = (value: string) => {
    onUpdate({
      interaction: {
        ...interaction,
        collectorUrl: value,
      },
    });
  };

  const handleConfigUrlChange = (value: string) => {
    onUpdate({
      interaction: {
        ...interaction,
        configUrl: value,
      },
    });
  };

  const handleQueueSizeChange = (value: number | string) => {
    onUpdate({
      interaction: {
        ...interaction,
        beforeInitQueueSize: Number(value) || 100,
      },
    });
  };

  return (
    <Box className={classes.sectionCard}>
      <Box 
        className={classes.sectionHeader}
        onClick={() => setExpanded(!expanded)}
      >
        <Group className={classes.sectionTitleGroup}>
          <Box className={classes.sectionIcon}>
            <IconClick size={20} />
          </Box>
          <Box>
            <Text className={classes.sectionTitle}>Interaction Configuration</Text>
            <Text className={classes.sectionDescription}>
              {SAMPLING_CONFIG_CONSTANTS.DESCRIPTIONS.INTERACTION}
            </Text>
          </Box>
        </Group>
        <Group gap="sm">
          {expanded ? <IconChevronDown size={18} /> : <IconChevronRight size={18} />}
        </Group>
      </Box>

      <Collapse in={expanded}>
        <Box className={classes.sectionContent}>
          <Stack gap="lg">
            {/* Collector URL */}
            <Box>
              <TextInput
                label="Interaction Collector URL"
                description="Endpoint for receiving user interaction data"
                placeholder="http://localhost:4318/v1/interactions"
                value={interaction.collectorUrl}
                onChange={(e) => handleCollectorUrlChange(e.target.value)}
              />
            </Box>

            {/* Config URL */}
            <Box>
              <TextInput
                label="Configuration URL"
                description="Endpoint for fetching latest SDK configuration"
                placeholder="http://localhost:8080/v1/configs/latest-version"
                value={interaction.configUrl}
                onChange={(e) => handleConfigUrlChange(e.target.value)}
              />
            </Box>

            {/* Queue Size */}
            <Box>
              <NumberInput
                label="Before-Init Queue Size"
                description="Maximum number of events to queue before SDK initialization completes"
                value={interaction.beforeInitQueueSize}
                onChange={handleQueueSizeChange}
                min={10}
                max={1000}
                step={10}
                styles={{
                  input: {
                    fontWeight: 600,
                  },
                }}
              />
            </Box>
          </Stack>
        </Box>
      </Collapse>
    </Box>
  );
}

