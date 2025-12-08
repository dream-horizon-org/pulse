/**
 * Signals Configuration Component
 * Manages telemetry collection settings and sensitive data handling
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
  ActionIcon,
  Badge,
} from '@mantine/core';
import {
  IconAntenna,
  IconChevronDown,
  IconChevronRight,
  IconX,
  IconPlus,
} from '@tabler/icons-react';
import { SectionProps } from '../../SamplingConfig.interface';
import { SAMPLING_CONFIG_CONSTANTS } from '../../SamplingConfig.constants';
import classes from '../../SamplingConfig.module.css';

export function SignalsConfig({ config, onUpdate }: SectionProps) {
  const [expanded, setExpanded] = useState(true);
  const [newAttribute, setNewAttribute] = useState('');

  const { signalsConfig } = config;

  const handleDurationChange = (value: number | string) => {
    onUpdate({
      signalsConfig: {
        ...signalsConfig,
        scheduleDurationMs: Number(value) || 5000,
      },
    });
  };

  const handleCollectorUrlChange = (value: string) => {
    onUpdate({
      signalsConfig: {
        ...signalsConfig,
        collectorUrl: value,
      },
    });
  };

  const handleAddAttribute = () => {
    if (newAttribute && !signalsConfig.attributesToDrop.includes(newAttribute)) {
      onUpdate({
        signalsConfig: {
          ...signalsConfig,
          attributesToDrop: [...signalsConfig.attributesToDrop, newAttribute],
        },
      });
      setNewAttribute('');
    }
  };

  const handleRemoveAttribute = (attr: string) => {
    onUpdate({
      signalsConfig: {
        ...signalsConfig,
        attributesToDrop: signalsConfig.attributesToDrop.filter(a => a !== attr),
      },
    });
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleAddAttribute();
    }
  };

  return (
    <Box className={classes.sectionCard}>
      <Box 
        className={classes.sectionHeader}
        onClick={() => setExpanded(!expanded)}
      >
        <Group className={classes.sectionTitleGroup}>
          <Box className={classes.sectionIcon}>
            <IconAntenna size={20} />
          </Box>
          <Box>
            <Text className={classes.sectionTitle}>Signals Configuration</Text>
            <Text className={classes.sectionDescription}>
              {SAMPLING_CONFIG_CONSTANTS.DESCRIPTIONS.SIGNALS}
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
            {/* Schedule Duration */}
            <Box>
              <NumberInput
                label="Schedule Duration (ms)"
                description="Interval at which telemetry data is batched and sent"
                value={signalsConfig.scheduleDurationMs}
                onChange={handleDurationChange}
                min={1000}
                max={60000}
                step={1000}
                suffix=" ms"
                styles={{
                  input: {
                    fontWeight: 600,
                  },
                }}
              />
            </Box>

            {/* Collector URL */}
            <Box>
              <TextInput
                label="Collector URL"
                description="Endpoint for receiving telemetry data"
                placeholder="http://localhost:4318/v1/traces"
                value={signalsConfig.collectorUrl}
                onChange={(e) => handleCollectorUrlChange(e.target.value)}
              />
            </Box>

            {/* Attributes to Drop */}
            <Box>
              <Text size="sm" fw={600} mb="xs">
                Attributes to Drop
              </Text>
              <Text size="xs" c="dimmed" mb="sm">
                Sensitive attributes that should be stripped from telemetry before sending
              </Text>

              <Box className={classes.attributeTagsContainer}>
                {signalsConfig.attributesToDrop.map((attr) => (
                  <Badge
                    key={attr}
                    className={classes.attributeTag}
                    variant="light"
                    rightSection={
                      <ActionIcon
                        size="xs"
                        variant="transparent"
                        onClick={() => handleRemoveAttribute(attr)}
                        className={classes.attributeTagRemove}
                      >
                        <IconX size={12} />
                      </ActionIcon>
                    }
                  >
                    {attr}
                  </Badge>
                ))}

                <Group gap="xs" style={{ flex: 1, minWidth: 150 }}>
                  <TextInput
                    placeholder="Add attribute..."
                    value={newAttribute}
                    onChange={(e) => setNewAttribute(e.target.value)}
                    onKeyDown={handleKeyDown}
                    size="xs"
                    style={{ flex: 1 }}
                    styles={{
                      input: {
                        border: 'none',
                        background: 'transparent',
                        padding: '4px 8px',
                        minWidth: 120,
                      },
                    }}
                  />
                  <ActionIcon
                    size="sm"
                    variant="light"
                    color="teal"
                    onClick={handleAddAttribute}
                    disabled={!newAttribute}
                  >
                    <IconPlus size={14} />
                  </ActionIcon>
                </Group>
              </Box>

              {signalsConfig.attributesToDrop.length === 0 && (
                <Text size="xs" c="dimmed" mt="xs" fs="italic">
                  No attributes configured. Add attributes like "password", "credit_card", etc.
                </Text>
              )}
            </Box>
          </Stack>
        </Box>
      </Collapse>
    </Box>
  );
}

