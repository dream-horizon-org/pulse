/**
 * Configuration Editor Component
 * 
 * Full editor for creating/viewing SDK configurations
 * Supports view mode (read-only) and edit mode (create new)
 * Uses real API via useCreateSdkConfig hook
 */

import { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Box,
  Text,
  Button,
  LoadingOverlay,
  Badge,
  useMantineTheme,
  Modal,
  Code,
  ScrollArea,
  CopyButton,
  ActionIcon,
  Tooltip,
  Group,
  TextInput,
  Paper,
  Alert,
} from '@mantine/core';
import {
  IconDeviceFloppy,
  IconRefresh,
  IconSquareRoundedX,
  IconCode,
  IconCheck,
  IconCopy,
  IconArrowLeft,
  IconEdit,
  IconInfoCircle,
  IconEye,
  IconCircleCheckFilled,
} from '@tabler/icons-react';
import {
  PulseConfig,
  ConfigEditorProps,
} from './SamplingConfig.interface';
import {
  DEFAULT_PULSE_CONFIG,
  UI_CONSTANTS,
  stripUIFields,
  addUIIds,
} from './SamplingConfig.constants';
import { FiltersConfig } from './components/FiltersConfig';
import { SamplingRulesConfig } from './components/SamplingRulesConfig';
import { CriticalEventsConfig } from './components/CriticalEventsConfig';
import { FeatureToggles } from './components/FeatureToggles';
import { InfraConfig } from './components/InfraConfig';
import { AttributesToDropConfig } from './components/AttributesToDropConfig';
import { AttributesToAddConfig } from './components/AttributesToAddConfig';
import { useCreateSdkConfig, useGetActiveSdkConfig } from '../../hooks/useSdkConfig';
import { showNotification } from '../../helpers/showNotification';
import classes from './SamplingConfig.module.css';

export function ConfigEditor({ 
  initialConfig, 
  mode, 
  onSave, 
  onCancel,
  onEdit,
  viewingVersion,
}: ConfigEditorProps) {
  const theme = useMantineTheme();
  
  // Use hook to get active config if no initial config provided
  const { data: activeConfigData, isLoading: isLoadingActive } = useGetActiveSdkConfig({
    enabled: !initialConfig && mode === 'create',
  });
  
  // Check if this is a "no config exists" scenario (first time setup)
  const isFirstTimeSetup = mode === 'create' && !initialConfig && !isLoadingActive && !activeConfigData?.data;

  // Create config mutation
  const createConfigMutation = useCreateSdkConfig((data, error) => {
    if (error) {
      showNotification(
        'Error',
        UI_CONSTANTS.NOTIFICATIONS.SAVE_ERROR,
        <IconSquareRoundedX />,
        theme.colors.red[6],
      );
    } else if (data) {
      showNotification(
        'Success',
        `Configuration v${data.version} created successfully`,
        <IconCircleCheckFilled />,
        theme.colors.teal[6],
      );
      // Create a minimal config with the new version for callback
      const savedConfig: PulseConfig = {
        ...config,
        version: data.version,
      };
      onSave?.(savedConfig);
    }
  });

  const [config, setConfig] = useState<PulseConfig>(initialConfig || DEFAULT_PULSE_CONFIG);
  const [originalConfig, setOriginalConfig] = useState<PulseConfig>(initialConfig || DEFAULT_PULSE_CONFIG);
  const [showConfigModal, setShowConfigModal] = useState(false);
  const [description, setDescription] = useState('');

  const isViewMode = mode === 'view';
  const isLoading = !initialConfig && isLoadingActive;
  const isSaving = createConfigMutation.isPending;

  // Initialize config from active config when loaded
  useEffect(() => {
    if (!initialConfig && activeConfigData?.data) {
      const configWithIds = addUIIds(activeConfigData.data);
      setConfig(configWithIds);
      setOriginalConfig(configWithIds);
    }
  }, [initialConfig, activeConfigData]);

  // Initialize from initialConfig when provided
  useEffect(() => {
    if (initialConfig) {
      setConfig(initialConfig);
      setOriginalConfig(initialConfig);
    }
  }, [initialConfig]);

  // Check if configuration has unsaved changes
  const isDirty = useMemo(() => {
    if (isViewMode) return false;
    try {
      return JSON.stringify(stripUIFields(config)) !== JSON.stringify(stripUIFields(originalConfig));
    } catch {
      return false;
    }
  }, [config, originalConfig, isViewMode]);

  // Formatted JSON for display (without UI fields)
  const formattedConfig = useMemo(() => {
    try {
      return JSON.stringify(stripUIFields(config), null, 2);
    } catch {
      return '{}';
    }
  }, [config]);

  // Save configuration using mutation
  const handleSave = useCallback(async () => {
    if (isViewMode) return;

    // Update config with description before saving
    const configToSave: PulseConfig = {
      ...config,
      description: description || config.description || 'SDK Configuration',
    };

    createConfigMutation.mutate({ config: configToSave });
  }, [config, description, isViewMode, createConfigMutation]);

  // Reset to original
  const handleReset = useCallback(() => {
    setConfig(originalConfig);
    setDescription('');
    showNotification(
      'Reset',
      'Configuration reset to original',
      <IconRefresh />,
      theme.colors.blue[6],
    );
  }, [originalConfig, theme.colors.blue]);

  return (
    <Box className={classes.pageContainer}>
      <LoadingOverlay visible={isLoading} />
      
      {/* First-time setup notice */}
      {isFirstTimeSetup && (
        <Alert
          icon={<IconInfoCircle size={18} />}
          title="First Time Setup"
          color="blue"
          mb="md"
          variant="light"
        >
          No existing configuration found. You're creating the first SDK configuration. 
          Fill in the settings below and save to activate it.
        </Alert>
      )}
      
      {/* Header */}
      <Box className={classes.header}>
        <Box className={classes.headerTop}>
          {/* Left section: Back button + Title */}
          <Box className={classes.headerLeft}>
            <Button
              variant="subtle"
              leftSection={<IconArrowLeft size={16} />}
              onClick={onCancel}
              size="sm"
              style={{ flexShrink: 0 }}
            >
              Back
            </Button>
            
            <Box className={classes.headerTitleSection}>
              <Group gap="xs" wrap="wrap">
                <Text className={classes.pageTitle}>
                  {isViewMode ? 'View Configuration' : 'New Configuration'}
                </Text>
                {viewingVersion && (
                  <Badge size="md" variant="light" color={isViewMode ? 'blue' : 'teal'}>
                    {isViewMode ? `v${viewingVersion}` : `Based on v${viewingVersion}`}
                  </Badge>
                )}
                {isViewMode && (
                  <Badge size="sm" variant="light" color="gray" leftSection={<IconEye size={10} />}>
                    Read-only
                  </Badge>
                )}
              </Group>
              <Text className={classes.pageSubtitle}>
                {isViewMode ? (
                  <>Viewing saved configuration. Click <Text span fw={600} c="blue.6">"Edit"</Text> to create a new version.</>
                ) : (
                  <>Configure SDK behavior and <Text span fw={600} c="teal.6">save</Text> to create a new version.</>
                )}
              </Text>
            </Box>
          </Box>

          {/* Right section: Actions */}
          <Box className={classes.headerActions}>
            {isDirty && !isViewMode && (
              <Badge className={classes.unsavedBadge} size="sm">
                Unsaved
              </Badge>
            )}
            
            <Group gap="xs" className={classes.headerActionsGroup}>
              <Button
                variant="subtle"
                leftSection={<IconCode size={16} />}
                onClick={() => setShowConfigModal(true)}
                size="sm"
              >
                JSON
              </Button>
              
              {isViewMode ? (
                <Button
                  leftSection={<IconEdit size={16} />}
                  onClick={onEdit}
                  size="sm"
                >
                  Edit
                </Button>
              ) : (
                <>
                  <Button
                    variant="subtle"
                    leftSection={<IconRefresh size={16} />}
                    onClick={handleReset}
                    disabled={isSaving || !isDirty}
                    size="sm"
                  >
                    Reset
                  </Button>
                  <Button
                    leftSection={<IconDeviceFloppy size={16} />}
                    onClick={handleSave}
                    loading={isSaving}
                    disabled={!isDirty && !description}
                    size="sm"
                  >
                    {isSaving ? 'Saving...' : 'Save'}
                  </Button>
                </>
              )}
            </Group>
          </Box>
        </Box>
      </Box>

      {/* View Mode Banner */}
      {isViewMode && (
        <Alert 
          icon={<IconInfoCircle size={18} />} 
          color="blue" 
          variant="light" 
          mb="lg"
          title="View Mode"
        >
          <Text size="sm">
            You are viewing an existing configuration. To make changes, click "Create from this" 
            to create a new version based on this configuration.
          </Text>
        </Alert>
      )}

      {/* Description Input (only in edit mode) */}
      {!isViewMode && (
        <Paper p="md" mb="lg" withBorder radius="md">
          <TextInput
            label="Version Description"
            description="Briefly describe what changes you're making in this version"
            placeholder="e.g., Increased crash reporting sample rate, Added payment_error filter"
            value={description}
            onChange={(e) => setDescription(e.currentTarget.value)}
            maxLength={200}
            required
          />
        </Paper>
      )}

      {/* Configuration Sections - Organized by functionality */}
      <Box className={classes.configPanel}>
        
        {/* ═══════════════════════════════════════════════════════════════════
            SECTION 1: FEATURES - What to Collect
            Start by choosing which SDK features are enabled/disabled
        ═══════════════════════════════════════════════════════════════════ */}
        <FeatureToggles
          configs={config.features}
          onChange={(features) => setConfig({ ...config, features })}
          disabled={isViewMode}
        />

        {/* ═══════════════════════════════════════════════════════════════════
            SECTION 2: SAMPLING - How Much to Collect
            Control what percentage of sessions/events are sampled
        ═══════════════════════════════════════════════════════════════════ */}
        <SamplingRulesConfig
          config={config.sampling}
          onChange={(sampling) => setConfig({ ...config, sampling })}
          disabled={isViewMode}
        />

        {/* Critical Events - Events that bypass sampling rules */}
        <CriticalEventsConfig
          config={config.sampling.criticalEventPolicies}
          onChange={(criticalEventPolicies) => setConfig({
            ...config,
            sampling: { ...config.sampling, criticalEventPolicies },
          })}
          disabled={isViewMode}
        />

        {/* ═══════════════════════════════════════════════════════════════════
            SECTION 3: FILTERING - What Events to Block/Allow
            Blacklist or whitelist specific events
        ═══════════════════════════════════════════════════════════════════ */}
        <FiltersConfig
          config={config.signals.filters}
          onChange={(filters) => setConfig({ 
            ...config, 
            signals: { ...config.signals, filters } 
          })}
          disabled={isViewMode}
        />

        {/* ═══════════════════════════════════════════════════════════════════
            SECTION 4: DATA TRANSFORMATION - Modify Event Data
            Add or remove attributes from events
        ═══════════════════════════════════════════════════════════════════ */}
        <AttributesToDropConfig
          attributes={config.signals.attributesToDrop || []}
          onChange={(attributesToDrop) => setConfig({
            ...config,
            signals: { ...config.signals, attributesToDrop }
          })}
          disabled={isViewMode}
        />

        <AttributesToAddConfig
          attributes={config.signals.attributesToAdd || []}
          onChange={(attributesToAdd) => setConfig({
            ...config,
            signals: { ...config.signals, attributesToAdd }
          })}
          disabled={isViewMode}
        />

        {/* ═══════════════════════════════════════════════════════════════════
            SECTION 5: INFRASTRUCTURE - Where to Send Data
            Collector URLs and connection settings
        ═══════════════════════════════════════════════════════════════════ */}
        <InfraConfig
          signals={config.signals}
          interaction={config.interaction}
          onSignalsChange={(signals) => setConfig({ ...config, signals })}
          onInteractionChange={(interaction) => setConfig({ ...config, interaction })}
          disabled={isViewMode}
        />
      </Box>

      {/* Configuration JSON Modal */}
      <Modal
        opened={showConfigModal}
        onClose={() => setShowConfigModal(false)}
        title={
          <Group gap="sm">
            <IconCode size={20} />
            <Text fw={600}>Configuration JSON</Text>
            {config.version && (
              <Badge size="sm" variant="light" color="gray">v{config.version}</Badge>
            )}
            {isDirty && <Badge color="yellow" size="sm">Unsaved</Badge>}
          </Group>
        }
        size="xl"
        centered
      >
        <Box mb="md">
          <Text size="sm" c="dimmed" mb="sm">
            {isViewMode 
              ? 'This is the configuration for the selected version.'
              : 'This is the configuration that will be saved.'}
          </Text>
          <Group justify="flex-end" mb="sm">
            <CopyButton value={formattedConfig}>
              {({ copied, copy }) => (
                <Tooltip label={copied ? 'Copied!' : 'Copy JSON'}>
                  <ActionIcon 
                    color={copied ? 'teal' : 'gray'} 
                    variant="subtle"
                    onClick={copy}
                  >
                    {copied ? <IconCheck size={18} /> : <IconCopy size={18} />}
                  </ActionIcon>
                </Tooltip>
              )}
            </CopyButton>
          </Group>
        </Box>
        <ScrollArea h={500} type="auto">
          <Code block style={{ 
            fontSize: 12, 
            lineHeight: 1.5,
            backgroundColor: '#1a1a2e',
            color: '#e2e8f0',
            padding: 16,
            borderRadius: 8,
          }}>
            {formattedConfig}
          </Code>
        </ScrollArea>
        <Group justify="flex-end" mt="md">
          <Button variant="subtle" onClick={() => setShowConfigModal(false)}>
            Close
          </Button>
          {isDirty && !isViewMode && (
            <Button 
              leftSection={<IconDeviceFloppy size={16} />}
              onClick={() => {
                handleSave();
                setShowConfigModal(false);
              }}
              loading={isSaving}
            >
              Save as New Version
            </Button>
          )}
        </Group>
      </Modal>
    </Box>
  );
}
