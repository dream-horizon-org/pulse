/**
 * Feature Configurations Component
 * Manages individual feature toggles and their sampling rates
 */

import { useState } from 'react';
import {
  Box,
  Text,
  Group,
  ActionIcon,
  Button,
  Modal,
  TextInput,
  MultiSelect,
  Switch,
  Stack,
  Tooltip,
  Collapse,
  SimpleGrid,
  Slider,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconSettings,
  IconPlus,
  IconTrash,
  IconEdit,
  IconChevronDown,
  IconChevronRight,
  IconNetwork,
  IconBug,
  IconChartLine,
  IconClick,
  IconDeviceDesktop,
  IconAlertTriangle,
  IconCpu,
  IconBattery,
  IconSparkles,
} from '@tabler/icons-react';
import { v4 as uuid } from 'uuid';
import {
  FeatureConfig,
  SectionProps,
  SDK_OPTIONS,
  SDKType,
  FEATURE_NAME_OPTIONS,
} from '../../SamplingConfig.interface';
import { SAMPLING_CONFIG_CONSTANTS } from '../../SamplingConfig.constants';
import { SdkTag, EmptyState } from '../shared';
import classes from '../../SamplingConfig.module.css';

interface FeatureFormData {
  id?: string;
  featureName: string;
  enabled: boolean;
  sampleRate: number;
  sdks: string[];
}

const defaultFormData: FeatureFormData = {
  featureName: '',
  enabled: true,
  sampleRate: 0.5,
  sdks: ['ANDROID', 'IOS'],
};

const featureIconMap: Record<string, React.ReactNode> = {
  network_monitoring: <IconNetwork size={24} />,
  crash_reporting: <IconBug size={24} />,
  performance_monitoring: <IconChartLine size={24} />,
  user_interaction_tracking: <IconClick size={24} />,
  screen_tracking: <IconDeviceDesktop size={24} />,
  anr_detection: <IconAlertTriangle size={24} />,
  memory_monitoring: <IconCpu size={24} />,
  battery_monitoring: <IconBattery size={24} />,
  custom_events: <IconSparkles size={24} />,
};

const featureColorMap: Record<string, string> = {
  network_monitoring: '#3b82f6',
  crash_reporting: '#ef4444',
  performance_monitoring: '#8b5cf6',
  user_interaction_tracking: '#0ea5e9',
  screen_tracking: '#14b8a6',
  anr_detection: '#f59e0b',
  memory_monitoring: '#6366f1',
  battery_monitoring: '#22c55e',
  custom_events: '#ec4899',
};

export function FeatureConfigs({ config, onUpdate }: SectionProps) {
  const [modalOpened, { open: openModal, close: closeModal }] = useDisclosure(false);
  const [expanded, setExpanded] = useState(true);
  const [editingFeature, setEditingFeature] = useState<FeatureFormData | null>(null);
  const [formData, setFormData] = useState<FeatureFormData>(defaultFormData);

  const { featureConfigs } = config;

  const handleAddFeature = () => {
    setEditingFeature(null);
    setFormData(defaultFormData);
    openModal();
  };

  const handleEditFeature = (feature: FeatureConfig) => {
    setEditingFeature({
      id: feature.id,
      featureName: feature.featureName,
      enabled: feature.enabled,
      sampleRate: feature.session_sample_rate,
      sdks: feature.sdks,
    });
    setFormData({
      id: feature.id,
      featureName: feature.featureName,
      enabled: feature.enabled,
      sampleRate: feature.session_sample_rate,
      sdks: feature.sdks,
    });
    openModal();
  };

  const handleDeleteFeature = (featureId: string) => {
    onUpdate({
      featureConfigs: featureConfigs.filter(f => f.id !== featureId),
    });
  };

  const handleToggleFeature = (featureId: string, enabled: boolean) => {
    onUpdate({
      featureConfigs: featureConfigs.map(f => 
        f.id === featureId ? { ...f, enabled } : f
      ),
    });
  };

  const handleSampleRateChange = (featureId: string, rate: number) => {
    onUpdate({
      featureConfigs: featureConfigs.map(f => 
        f.id === featureId ? { ...f, session_sample_rate: rate } : f
      ),
    });
  };

  const handleSaveFeature = () => {
    const newFeature: FeatureConfig = {
      id: editingFeature?.id || uuid(),
      featureName: formData.featureName,
      enabled: formData.enabled,
      session_sample_rate: formData.sampleRate,
      sdks: formData.sdks as SDKType[],
    };

    if (editingFeature?.id) {
      onUpdate({
        featureConfigs: featureConfigs.map(f => 
          f.id === editingFeature.id ? newFeature : f
        ),
      });
    } else {
      onUpdate({
        featureConfigs: [...featureConfigs, newFeature],
      });
    }

    closeModal();
  };

  const getFeatureIcon = (name: string) => {
    return featureIconMap[name] || <IconSettings size={24} />;
  };

  const getFeatureColor = (name: string) => {
    return featureColorMap[name] || '#6b7280';
  };

  const formatFeatureName = (name: string) => {
    return name
      .split('_')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  };

  // Filter out already configured features from options
  const availableFeatures = FEATURE_NAME_OPTIONS.filter(
    name => !featureConfigs.some(f => f.featureName === name) || editingFeature?.featureName === name
  );

  return (
    <Box className={classes.sectionCard}>
      <Box 
        className={classes.sectionHeader}
        onClick={() => setExpanded(!expanded)}
      >
        <Group className={classes.sectionTitleGroup}>
          <Box className={classes.sectionIcon}>
            <IconSettings size={20} />
          </Box>
          <Box>
            <Text className={classes.sectionTitle}>Feature Configurations</Text>
            <Text className={classes.sectionDescription}>
              {SAMPLING_CONFIG_CONSTANTS.DESCRIPTIONS.FEATURES}
            </Text>
          </Box>
        </Group>
        <Group gap="sm">
          <Text className={classes.sectionBadge} c="teal" bg="teal.0">
            {featureConfigs.filter(f => f.enabled).length}/{featureConfigs.length} active
          </Text>
          {expanded ? <IconChevronDown size={18} /> : <IconChevronRight size={18} />}
        </Group>
      </Box>

      <Collapse in={expanded}>
        <Box className={classes.sectionContent}>
          {featureConfigs.length === 0 ? (
            <EmptyState
              icon={<IconSettings size={28} />}
              title="No features configured"
              description="Add feature configurations to control which SDK features are active and their sampling rates"
              actionLabel="Add Feature"
              onAction={handleAddFeature}
            />
          ) : (
            <>
              <SimpleGrid cols={{ base: 1, md: 2 }} spacing="md" mb="md">
                {featureConfigs.map((feature) => {
                  const color = getFeatureColor(feature.featureName);
                  return (
                    <Box 
                      key={feature.id || feature.featureName} 
                      className={`${classes.featureCard} ${!feature.enabled ? classes.featureCardDisabled : ''} ${classes.fadeIn}`}
                    >
                      <Box 
                        className={classes.featureIcon}
                        style={{ 
                          backgroundColor: `${color}15`,
                          color: color,
                        }}
                      >
                        {getFeatureIcon(feature.featureName)}
                      </Box>

                      <Box className={classes.featureContent}>
                        <Box className={classes.featureHeader}>
                          <Text className={classes.featureName}>
                            {formatFeatureName(feature.featureName)}
                          </Text>
                          <Group gap={4}>
                            <Tooltip label="Edit">
                              <ActionIcon 
                                variant="subtle" 
                                size="sm"
                                onClick={() => handleEditFeature(feature)}
                              >
                                <IconEdit size={14} />
                              </ActionIcon>
                            </Tooltip>
                            <Tooltip label="Delete">
                              <ActionIcon 
                                variant="subtle" 
                                size="sm" 
                                color="red"
                                onClick={() => handleDeleteFeature(feature.id || '')}
                              >
                                <IconTrash size={14} />
                              </ActionIcon>
                            </Tooltip>
                            <Switch
                              size="sm"
                              checked={feature.enabled}
                              onChange={(e) => handleToggleFeature(feature.id || '', e.currentTarget.checked)}
                              color="teal"
                            />
                          </Group>
                        </Box>

                        <Group gap="xs" mb="sm">
                          {feature.sdks.map((sdk) => (
                            <SdkTag key={sdk} sdk={sdk} />
                          ))}
                        </Group>

                        <Box className={classes.featureRate}>
                          <Text size="xs" fw={500} c="dark.5" style={{ minWidth: 80 }}>
                            Sample Rate
                          </Text>
                          <Slider
                            value={feature.session_sample_rate * 100}
                            onChange={(val) => handleSampleRateChange(feature.id || '', val / 100)}
                            min={0}
                            max={100}
                            step={5}
                            disabled={!feature.enabled}
                            style={{ flex: 1 }}
                            styles={{
                              track: {
                                backgroundColor: 'rgba(14, 201, 194, 0.15)',
                              },
                              bar: {
                                background: `linear-gradient(90deg, ${color}80, ${color})`,
                              },
                              thumb: {
                                borderColor: color,
                              },
                            }}
                          />
                          <Text size="sm" fw={700} c={feature.enabled ? 'teal' : 'dimmed'} style={{ minWidth: 45, textAlign: 'right' }}>
                            {Math.round(feature.session_sample_rate * 100)}%
                          </Text>
                        </Box>
                      </Box>
                    </Box>
                  );
                })}
              </SimpleGrid>

              <Button
                className={classes.addButton}
                leftSection={<IconPlus size={16} />}
                variant="default"
                onClick={handleAddFeature}
              >
                Add Feature Configuration
              </Button>
            </>
          )}
        </Box>
      </Collapse>

      {/* Add/Edit Feature Modal */}
      <Modal
        opened={modalOpened}
        onClose={closeModal}
        title={editingFeature ? 'Edit Feature Configuration' : 'Add Feature Configuration'}
        size="lg"
      >
        <Stack gap="md">
          {editingFeature ? (
            <TextInput
              label="Feature Name"
              value={formData.featureName}
              disabled
            />
          ) : (
            <MultiSelect
              label="Feature Name"
              placeholder="Select a feature"
              data={availableFeatures.map(name => ({
                value: name,
                label: formatFeatureName(name),
              }))}
              value={formData.featureName ? [formData.featureName] : []}
              onChange={(values) => setFormData({ ...formData, featureName: values[0] || '' })}
              maxValues={1}
              searchable
            />
          )}

          <MultiSelect
            label="SDKs"
            placeholder="Select SDKs"
            data={SDK_OPTIONS}
            value={formData.sdks}
            onChange={(sdks) => setFormData({ ...formData, sdks })}
          />

          <Switch
            label="Enabled"
            description="Toggle this feature on or off"
            checked={formData.enabled}
            onChange={(e) => setFormData({ ...formData, enabled: e.currentTarget.checked })}
            color="teal"
          />

          <Box>
            <Text size="sm" fw={500} mb="xs">Session Sample Rate: {Math.round(formData.sampleRate * 100)}%</Text>
            <Slider
              value={formData.sampleRate * 100}
              onChange={(val) => setFormData({ ...formData, sampleRate: val / 100 })}
              min={0}
              max={100}
              step={5}
              marks={[
                { value: 0, label: '0%' },
                { value: 50, label: '50%' },
                { value: 100, label: '100%' },
              ]}
              styles={{
                track: {
                  backgroundColor: 'rgba(14, 201, 194, 0.15)',
                },
                bar: {
                  background: 'linear-gradient(90deg, #0ec9c2 0%, #0ba09a 100%)',
                },
                thumb: {
                  borderColor: '#0ba09a',
                },
              }}
            />
          </Box>

          <Group justify="flex-end" mt="md">
            <Button variant="light" onClick={closeModal}>
              Cancel
            </Button>
            <Button 
              onClick={handleSaveFeature}
              disabled={!formData.featureName || formData.sdks.length === 0}
            >
              {editingFeature ? 'Update Feature' : 'Add Feature'}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Box>
  );
}

