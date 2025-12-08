/**
 * Feature Toggles Component
 * Enable/disable features with individual sample rates and SDK selection
 */

import { useState } from 'react';
import {
  Box,
  Text,
  Switch,
  Slider,
  Button,
  TextInput,
  ActionIcon,
  Group,
  Badge,
  Modal,
  MultiSelect,
  Stack,
  Paper,
  Alert,
  Tooltip,
} from '@mantine/core';
import {
  IconSettings,
  IconBug,
  IconNetwork,
  IconGauge,
  IconClick,
  IconPlus,
  IconTrash,
  IconEdit,
  IconInfoCircle,
} from '@tabler/icons-react';
import { FeatureConfig, SdkEnum } from '../../SamplingConfig.interface';
import { SDK_OPTIONS, FEATURE_DISPLAY_INFO, generateId, UI_CONSTANTS } from '../../SamplingConfig.constants';
import classes from '../../SamplingConfig.module.css';

const FEATURE_ICONS: Record<string, React.ReactNode> = {
  crash_reporting: <IconBug size={22} />,
  network_monitoring: <IconNetwork size={22} />,
  performance_monitoring: <IconGauge size={22} />,
  user_interaction_tracking: <IconClick size={22} />,
};

const FEATURE_COLORS: Record<string, string> = {
  crash_reporting: '#ef4444',
  network_monitoring: '#3b82f6',
  performance_monitoring: '#8b5cf6',
  user_interaction_tracking: '#f59e0b',
};

interface FeatureTogglesProps {
  configs: FeatureConfig[];
  onChange: (configs: FeatureConfig[]) => void;
}

export function FeatureToggles({ configs, onChange }: FeatureTogglesProps) {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingFeature, setEditingFeature] = useState<FeatureConfig | null>(null);
  
  // Form state
  const [featureName, setFeatureName] = useState('');
  const [featureEnabled, setFeatureEnabled] = useState(true);
  const [featureSampleRate, setFeatureSampleRate] = useState(0.5);
  const [featureSdks, setFeatureSdks] = useState<SdkEnum[]>([]);

  const resetForm = () => {
    setFeatureName('');
    setFeatureEnabled(true);
    setFeatureSampleRate(0.5);
    setFeatureSdks([]);
    setEditingFeature(null);
  };

  const openAddModal = () => {
    resetForm();
    setIsModalOpen(true);
  };

  const openEditModal = (feature: FeatureConfig) => {
    setEditingFeature(feature);
    setFeatureName(feature.featureName);
    setFeatureEnabled(feature.enabled);
    setFeatureSampleRate(feature.session_sample_rate);
    setFeatureSdks(feature.sdks);
    setIsModalOpen(true);
  };

  const handleSaveFeature = () => {
    const newFeature: FeatureConfig = {
      id: editingFeature?.id || generateId(),
      featureName: featureName.trim(),
      enabled: featureEnabled,
      session_sample_rate: featureSampleRate,
      sdks: featureSdks,
    };

    if (editingFeature) {
      onChange(configs.map(f => f.id === editingFeature.id ? newFeature : f));
    } else {
      onChange([...configs, newFeature]);
    }

    setIsModalOpen(false);
    resetForm();
  };

  const handleRemoveFeature = (featureId: string) => {
    onChange(configs.filter(f => f.id !== featureId));
  };

  const handleToggle = (featureId: string, enabled: boolean) => {
    onChange(configs.map(f => f.id === featureId ? { ...f, enabled } : f));
  };

  const handleSampleRateChange = (featureId: string, rate: number) => {
    onChange(configs.map(f => f.id === featureId ? { ...f, session_sample_rate: rate } : f));
  };

  const getFeatureDisplay = (featureName: string) => {
    return FEATURE_DISPLAY_INFO[featureName] || {
      label: featureName.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase()),
      description: 'Custom feature configuration',
      icon: 'settings',
    };
  };

  return (
    <>
      <Box className={classes.card}>
        <Box className={classes.cardHeader}>
          <Box className={classes.cardHeaderLeft}>
            <Box className={`${classes.cardIcon} ${classes.features}`}>
              <IconSettings size={20} />
            </Box>
            <Box>
              <Text className={classes.cardTitle}>{UI_CONSTANTS.SECTIONS.FEATURES.TITLE}</Text>
              <Text className={classes.cardDescription}>{UI_CONSTANTS.SECTIONS.FEATURES.DESCRIPTION}</Text>
            </Box>
          </Box>
          <Button
            size="xs"
            leftSection={<IconPlus size={14} />}
            onClick={openAddModal}
            variant="light"
          >
            Add Feature
          </Button>
        </Box>
        
        <Box className={classes.cardContent}>
          {/* Explanation */}
          <Alert 
            icon={<IconInfoCircle size={18} />} 
            color="violet" 
            variant="light" 
            mb="lg"
            title="Feature-Level Control"
          >
            <Text size="xs">
              Each feature operates <strong>independently</strong> with its own sample rate. 
              Disabling a feature stops all data collection for that feature. The sample rate controls 
              what percentage of events from that feature are sent to Pulse.
            </Text>
            <Text size="xs" mt="xs" c="dimmed">
              💡 <strong>Tip:</strong> Keep crash_reporting at 100% (never miss a crash), reduce 
              performance_monitoring to 50-60% (high volume data), and adjust network_monitoring based on your debugging needs.
            </Text>
          </Alert>

          {configs.length === 0 ? (
            <Box className={classes.emptyState}>
              <IconSettings size={32} style={{ opacity: 0.3 }} />
              <Text size="sm" c="dimmed" mt="xs">No features configured</Text>
            </Box>
          ) : (
            <Stack gap="sm">
              {configs.map((feature) => {
                const display = getFeatureDisplay(feature.featureName);
                const icon = FEATURE_ICONS[feature.featureName] || <IconSettings size={22} />;
                const color = FEATURE_COLORS[feature.featureName] || '#6b7280';
                
                return (
                  <Paper 
                    key={feature.id} 
                    withBorder 
                    p="md"
                    style={{ opacity: feature.enabled ? 1 : 0.6 }}
                  >
                    <Group justify="space-between" wrap="nowrap">
                      <Group gap="md" style={{ flex: 1 }}>
                        <Box
                          style={{ 
                            width: 44,
                            height: 44,
                            borderRadius: 10,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            backgroundColor: `${color}15`,
                            color: feature.enabled ? color : '#9ca3af',
                          }}
                        >
                          {icon}
                        </Box>
                        
                        <Box style={{ flex: 1, minWidth: 0 }}>
                          <Group gap="xs" mb={4}>
                            <Text fw={600}>{display.label}</Text>
                            {!feature.enabled && (
                              <Badge size="xs" color="gray" variant="light">Disabled</Badge>
                            )}
                          </Group>
                          <Text size="xs" c="dimmed" lineClamp={1}>{display.description}</Text>
                          <Group gap="xs" mt="xs">
                            {feature.sdks.slice(0, 3).map(sdk => (
                              <Badge key={sdk} size="xs" variant="dot">
                                {SDK_OPTIONS.find(s => s.value === sdk)?.label || sdk}
                              </Badge>
                            ))}
                            {feature.sdks.length > 3 && (
                              <Badge size="xs" variant="dot" color="gray">
                                +{feature.sdks.length - 3}
                              </Badge>
                            )}
                          </Group>
                        </Box>
                      </Group>

                      <Group gap="md" wrap="nowrap">
                        {feature.enabled && (
                          <Tooltip 
                            label={`${Math.round(feature.session_sample_rate * 100)}% of ${display.label} events will be sent to Pulse`}
                            withArrow
                          >
                            <Box style={{ width: 120 }}>
                              <Text size="xs" c="dimmed" mb={4}>Sample Rate</Text>
                              <Group gap="xs">
                                <Slider
                                  value={feature.session_sample_rate}
                                  onChange={(v) => handleSampleRateChange(feature.id || '', v)}
                                  min={0}
                                  max={1}
                                  step={0.1}
                                  size="xs"
                                  style={{ flex: 1 }}
                                  styles={{
                                    bar: { backgroundColor: color },
                                    thumb: { borderColor: color },
                                  }}
                                />
                                <Text size="xs" fw={600} style={{ color, minWidth: 35 }}>
                                  {Math.round(feature.session_sample_rate * 100)}%
                                </Text>
                              </Group>
                            </Box>
                          </Tooltip>
                        )}
                        
                        <Switch
                          checked={feature.enabled}
                          onChange={(e) => handleToggle(feature.id || '', e.currentTarget.checked)}
                          color="teal"
                        />

                        <Group gap={4}>
                          <ActionIcon variant="subtle" onClick={() => openEditModal(feature)}>
                            <IconEdit size={16} />
                          </ActionIcon>
                          <ActionIcon variant="subtle" color="red" onClick={() => handleRemoveFeature(feature.id || '')}>
                            <IconTrash size={16} />
                          </ActionIcon>
                        </Group>
                      </Group>
                    </Group>
                  </Paper>
                );
              })}
            </Stack>
          )}
        </Box>
      </Box>

      {/* Add/Edit Feature Modal */}
      <Modal
        opened={isModalOpen}
        onClose={() => { setIsModalOpen(false); resetForm(); }}
        title={editingFeature ? 'Edit Feature' : 'Add Feature'}
        size="md"
        centered
      >
        <Stack gap="md">
          <TextInput
            label="Feature Name"
            description="Unique identifier for the feature (snake_case)"
            placeholder="e.g., custom_analytics, ab_testing"
            value={featureName}
            onChange={(e) => setFeatureName(e.currentTarget.value)}
            required
          />

          <Group>
            <Text size="sm" fw={500}>Enabled</Text>
            <Switch
              checked={featureEnabled}
              onChange={(e) => setFeatureEnabled(e.currentTarget.checked)}
              color="teal"
            />
          </Group>

          <Box>
            <Group justify="space-between" mb="xs">
              <Text size="sm" fw={500}>Sample Rate</Text>
              <Text size="sm" fw={700} style={{ color: '#0ec9c2' }}>
                {Math.round(featureSampleRate * 100)}%
              </Text>
            </Group>
            <Slider
              value={featureSampleRate}
              onChange={setFeatureSampleRate}
              min={0}
              max={1}
              step={0.05}
              marks={[
                { value: 0, label: '0%' },
                { value: 0.5, label: '50%' },
                { value: 1, label: '100%' },
              ]}
            />
          </Box>

          <MultiSelect
            label="Target SDKs"
            description="Which SDK platforms this feature applies to"
            placeholder="Select SDKs"
            data={SDK_OPTIONS.map(s => ({ value: s.value, label: s.label }))}
            value={featureSdks}
            onChange={(v) => setFeatureSdks(v as SdkEnum[])}
            required
          />

          <Group justify="flex-end" mt="md">
            <Button variant="subtle" onClick={() => { setIsModalOpen(false); resetForm(); }}>
              Cancel
            </Button>
            <Button
              onClick={handleSaveFeature}
              disabled={!featureName.trim() || featureSdks.length === 0}
            >
              {editingFeature ? 'Update Feature' : 'Add Feature'}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
