/**
 * Feature Toggles Component
 * Enable/disable SDK features per platform
 * 
 * Uses dynamic data from backend:
 * - GET /v1/configs/rules-features for available features
 * - GET /v1/configs/scopes-sdks for available SDKs
 * 
 * Note: Uses sessionSampleRate (0 = off, 1 = on) internally, but shows as toggle in UI
 */

import { useState, useMemo } from 'react';
import {
  Box,
  Text,
  Switch,
  Button,
  ActionIcon,
  Group,
  Badge,
  Modal,
  MultiSelect,
  Select,
  Stack,
  Paper,
  Alert,
  Tooltip,
  Loader,
} from '@mantine/core';
import {
  IconSettings,
  IconBug,
  IconNetwork,
  IconClick,
  IconPlus,
  IconTrash,
  IconEdit,
  IconInfoCircle,
  IconAlertTriangle,
  IconWifi,
  IconDeviceMobile,
  IconTag,
} from '@tabler/icons-react';
import { FeatureConfig, FeatureName, SdkEnum, FeatureConfigsProps } from '../../SamplingConfig.interface';
import { 
  toSdkOptions,
  toFeatureOptions,
  SDK_DISPLAY_INFO,
  FEATURE_DISPLAY_INFO,
  generateId, 
  UI_CONSTANTS,
} from '../../SamplingConfig.constants';
import { useGetSdkRulesAndFeatures, useGetSdkScopesAndSdks } from '../../../../hooks/useSdkConfig';
import classes from '../../SamplingConfig.module.css';

const FEATURE_ICONS: Record<string, React.ReactNode> = {
  interaction: <IconClick size={22} />,
  java_crash: <IconBug size={22} />,
  java_anr: <IconAlertTriangle size={22} />,
  network_change: <IconWifi size={22} />,
  network_instrumentation: <IconNetwork size={22} />,
  screen_session: <IconDeviceMobile size={22} />,
  custom_events: <IconTag size={22} />,
};

const FEATURE_COLORS: Record<string, string> = {
  interaction: '#f59e0b',
  java_crash: '#ef4444',
  java_anr: '#dc2626',
  network_change: '#06b6d4',
  network_instrumentation: '#3b82f6',
  screen_session: '#8b5cf6',
  custom_events: '#10b981',
};

export function FeatureToggles({ configs, onChange, disabled = false }: FeatureConfigsProps) {
  // Fetch dynamic options from backend
  const { data: rulesAndFeatures, isLoading: isLoadingFeatures } = useGetSdkRulesAndFeatures();
  const { data: scopesAndSdks, isLoading: isLoadingSdks } = useGetSdkScopesAndSdks();
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingFeature, setEditingFeature] = useState<FeatureConfig | null>(null);
  
  // Form state
  const [featureName, setFeatureName] = useState<FeatureName | ''>('');
  const [featureEnabled, setFeatureEnabled] = useState(true); // UI toggle, maps to sessionSampleRate 0/1
  const [featureSdks, setFeatureSdks] = useState<SdkEnum[]>([]);

  // Helper to check if feature is enabled based on sessionSampleRate
  const isFeatureEnabled = (feature: FeatureConfig) => feature.sessionSampleRate === 1;

  // Convert backend data to select options
  const featureOptions = useMemo(() => {
    if (rulesAndFeatures?.data?.features) {
      return toFeatureOptions(rulesAndFeatures.data.features);
    }
    return [];
  }, [rulesAndFeatures]);

  const sdkOptions = useMemo(() => {
    if (scopesAndSdks?.data?.sdks) {
      return toSdkOptions(scopesAndSdks.data.sdks);
    }
    return [];
  }, [scopesAndSdks]);

  const allSdks = useMemo(() => sdkOptions.map(s => s.value), [sdkOptions]);

  // Get features that haven't been configured yet
  const availableFeatures = featureOptions.filter(
    f => !configs.some(c => c.featureName === f.value) || editingFeature?.featureName === f.value
  );

  const resetForm = () => {
    setFeatureName('');
    setFeatureEnabled(true);
    setFeatureSdks([]);
    setEditingFeature(null);
  };

  const openAddModal = () => {
    if (disabled) return;
    resetForm();
    setIsModalOpen(true);
  };

  const openEditModal = (feature: FeatureConfig) => {
    if (disabled) return;
    setEditingFeature(feature);
    setFeatureName(feature.featureName);
    setFeatureEnabled(isFeatureEnabled(feature)); // Convert sessionSampleRate to boolean
    setFeatureSdks(feature.sdks);
    setIsModalOpen(true);
  };

  const handleSaveFeature = () => {
    if (!featureName) return;
    
    const newFeature: FeatureConfig = {
      id: editingFeature?.id || generateId(),
      featureName: featureName,
      sessionSampleRate: featureEnabled ? 1 : 0, // Convert toggle to sessionSampleRate
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
    if (disabled) return;
    onChange(configs.filter(f => f.id !== featureId));
  };

  const handleToggle = (featureId: string, enabled: boolean) => {
    if (disabled) return;
    onChange(configs.map(f => f.id === featureId ? { ...f, sessionSampleRate: enabled ? 1 : 0 } : f));
  };

  const getFeatureDisplay = (name: FeatureName) => {
    return FEATURE_DISPLAY_INFO[name] || {
      label: name.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase()),
      description: 'SDK feature',
    };
  };

  const getSdkLabel = (sdk: SdkEnum) => SDK_DISPLAY_INFO[sdk]?.label || sdk;

  const isLoading = isLoadingFeatures || isLoadingSdks;

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
          {!disabled && availableFeatures.length > 0 && (
            <Button
              size="xs"
              leftSection={<IconPlus size={14} />}
              onClick={openAddModal}
              variant="light"
            >
              Add Feature
            </Button>
          )}
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
              Control which SDK features are enabled per platform. 
              Disabling a feature stops all data collection for that feature.
            </Text>
            <Text size="xs" mt="xs" c="dimmed">
              💡 <strong>Tip:</strong> Enable crash reporting on all platforms, 
              and selectively enable other features based on your needs.
            </Text>
          </Alert>

          {isLoading ? (
            <Box ta="center" py="xl">
              <Loader size="sm" />
              <Text size="sm" c="dimmed" mt="sm">Loading features...</Text>
            </Box>
          ) : configs.length === 0 ? (
            <Box className={classes.emptyState}>
              <IconSettings size={32} style={{ opacity: 0.3 }} />
              <Text size="sm" c="dimmed" mt="xs">No features configured</Text>
              <Text size="xs" c="dimmed">Add features to control SDK behavior</Text>
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
                    style={{ opacity: isFeatureEnabled(feature) ? 1 : 0.6 }}
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
                            color: isFeatureEnabled(feature) ? color : '#9ca3af',
                          }}
                        >
                          {icon}
                        </Box>
                        
                        <Box style={{ flex: 1, minWidth: 0 }}>
                          <Group gap="xs" mb={4}>
                            <Text fw={600}>{display.label}</Text>
                            {!isFeatureEnabled(feature) && (
                              <Badge size="xs" color="gray" variant="light">Disabled</Badge>
                            )}
                          </Group>
                          <Text size="xs" c="dimmed" lineClamp={1}>{display.description}</Text>
                          <Group gap="xs" mt="xs">
                            {feature.sdks.slice(0, 3).map(sdk => (
                              <Badge key={sdk} size="xs" variant="dot">
                                {getSdkLabel(sdk)}
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
                        <Tooltip 
                          label={isFeatureEnabled(feature) ? 'Feature is enabled' : 'Feature is disabled'}
                          withArrow
                        >
                          <Switch
                            checked={isFeatureEnabled(feature)}
                            onChange={(e) => handleToggle(feature.id || '', e.currentTarget.checked)}
                            color="teal"
                            disabled={disabled}
                          />
                        </Tooltip>

                        {!disabled && (
                          <Group gap={4}>
                            <ActionIcon variant="subtle" onClick={() => openEditModal(feature)}>
                              <IconEdit size={16} />
                            </ActionIcon>
                            <ActionIcon variant="subtle" color="red" onClick={() => handleRemoveFeature(feature.id || '')}>
                              <IconTrash size={16} />
                            </ActionIcon>
                          </Group>
                        )}
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
        {isLoading ? (
          <Box ta="center" py="xl">
            <Loader size="sm" />
            <Text size="sm" c="dimmed" mt="sm">Loading options...</Text>
          </Box>
        ) : (
          <Stack gap="md">
            <Select
              label="Feature"
              description="Select an SDK feature to configure"
              placeholder="Select feature"
              data={availableFeatures.map(f => ({ 
                value: f.value, 
                label: f.label,
              }))}
              value={featureName}
              onChange={(v) => setFeatureName(v as FeatureName)}
              required
              disabled={!!editingFeature}
            />

            <Group>
              <Text size="sm" fw={500}>Enabled</Text>
              <Switch
                checked={featureEnabled}
                onChange={(e) => setFeatureEnabled(e.currentTarget.checked)}
                color="teal"
              />
              <Text size="xs" c="dimmed">
                {featureEnabled ? 'Data collection for this feature is active' : 'Data collection for this feature is paused'}
              </Text>
            </Group>

            <Box>
              <Group justify="space-between" mb="xs">
                <Text size="sm" fw={500}>Target SDKs</Text>
                <Button 
                  size="compact-xs" 
                  variant="subtle" 
                  onClick={() => setFeatureSdks(allSdks)}
                  disabled={sdkOptions.length === 0}
                >
                  Select All
                </Button>
              </Group>
              <MultiSelect
                description="Which SDK platforms this feature applies to"
                placeholder="Select SDKs"
                data={sdkOptions.map(s => ({ value: s.value, label: s.label }))}
                value={featureSdks}
                onChange={(v) => setFeatureSdks(v as SdkEnum[])}
                required
              />
            </Box>

            <Group justify="flex-end" mt="md">
              <Button variant="subtle" onClick={() => { setIsModalOpen(false); resetForm(); }}>
                Cancel
              </Button>
              <Button
                onClick={handleSaveFeature}
                disabled={!featureName || featureSdks.length === 0}
              >
                {editingFeature ? 'Update Feature' : 'Add Feature'}
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>
    </>
  );
}
