/**
 * Configuration Editor Component
 * 
 * Full editor for creating/viewing SDK configurations
 * Supports view mode (read-only) and edit mode (create new)
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
} from '@tabler/icons-react';
import {
  PulseConfig,
  PipelineStats,
  ConfigEditorMode,
} from './SamplingConfig.interface';
import {
  DEFAULT_PULSE_CONFIG,
  calculatePipelineStats,
  UI_CONSTANTS,
} from './SamplingConfig.constants';
import { DataPipeline } from './components/DataPipeline';
import { FiltersConfig } from './components/FiltersConfig';
import { SamplingRulesConfig } from './components/SamplingRulesConfig';
import { CriticalEventsConfig } from './components/CriticalEventsConfig';
import { FeatureToggles } from './components/FeatureToggles';
import { InfraConfig } from './components/InfraConfig';
import { makeRequest } from '../../helpers/makeRequest';
import { API_BASE_URL, API_METHODS } from '../../constants';
import { showNotification } from '../../helpers/showNotification';
import classes from './SamplingConfig.module.css';

interface ConfigEditorProps {
  initialConfig?: PulseConfig;
  mode: ConfigEditorMode;
  onSave?: (config: PulseConfig) => void;
  onCancel?: () => void;
  onEdit?: () => void;
  viewingVersion?: number | null;
}

// Helper to strip UI-only fields for API payload
const stripUIFields = (config: PulseConfig): PulseConfig => {
  const cleanConfig = JSON.parse(JSON.stringify(config));
  
  // Remove id fields from nested objects (used only for UI tracking)
  cleanConfig.filtersConfig.blacklist.forEach((f: any) => delete f.id);
  cleanConfig.filtersConfig.whitelist.forEach((f: any) => delete f.id);
  cleanConfig.samplingConfig.rules.forEach((r: any) => delete r.id);
  cleanConfig.samplingConfig.criticalEventPolicies.alwaysSend.forEach((e: any) => delete e.id);
  cleanConfig.featureConfigs.forEach((f: any) => delete f.id);
  
  return cleanConfig;
};

export function ConfigEditor({ 
  initialConfig, 
  mode, 
  onSave, 
  onCancel,
  onEdit,
  viewingVersion,
}: ConfigEditorProps) {
  const theme = useMantineTheme();
  const [config, setConfig] = useState<PulseConfig>(initialConfig || DEFAULT_PULSE_CONFIG);
  const [originalConfig, setOriginalConfig] = useState<PulseConfig>(initialConfig || DEFAULT_PULSE_CONFIG);
  const [isLoading, setIsLoading] = useState(!initialConfig);
  const [isSaving, setIsSaving] = useState(false);
  const [showConfigModal, setShowConfigModal] = useState(false);
  const [description, setDescription] = useState('');

  const isViewMode = mode === 'view';

  // Check if configuration has unsaved changes
  const isDirty = useMemo(() => {
    if (isViewMode) return false;
    return JSON.stringify(stripUIFields(config)) !== JSON.stringify(stripUIFields(originalConfig));
  }, [config, originalConfig, isViewMode]);

  // Calculate pipeline stats
  const pipelineStats = useMemo<PipelineStats>(() => {
    return calculatePipelineStats(config);
  }, [config]);

  // Formatted JSON for display (without UI fields)
  const formattedConfig = useMemo(() => {
    return JSON.stringify(stripUIFields(config), null, 2);
  }, [config]);

  // Load configuration if not provided
  const loadConfig = useCallback(async () => {
    if (initialConfig) {
      setConfig(initialConfig);
      setOriginalConfig(initialConfig);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    try {
      const response = await makeRequest<PulseConfig>({
        url: `${API_BASE_URL}/v1/sdk-config`,
        init: { method: API_METHODS.GET },
      });

      if (response.data) {
        setConfig(response.data);
        setOriginalConfig(response.data);
      } else {
        setConfig(DEFAULT_PULSE_CONFIG);
        setOriginalConfig(DEFAULT_PULSE_CONFIG);
      }
    } catch {
      showNotification(
        'Error',
        UI_CONSTANTS.NOTIFICATIONS.LOAD_ERROR,
        <IconSquareRoundedX />,
        theme.colors.red[6],
      );
      setConfig(DEFAULT_PULSE_CONFIG);
      setOriginalConfig(DEFAULT_PULSE_CONFIG);
    } finally {
      setIsLoading(false);
    }
  }, [initialConfig, theme.colors.red]);

  useEffect(() => {
    loadConfig();
  }, [loadConfig]);

  // Save configuration
  const handleSave = async () => {
    if (isViewMode) return;

    setIsSaving(true);
    try {
      const configToSave = {
        ...stripUIFields(config),
        description: description || undefined,
      };

      const response = await makeRequest<PulseConfig>({
        url: `${API_BASE_URL}/v1/sdk-config`,
        init: {
          method: API_METHODS.PUT,
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(configToSave),
        },
      });

      if (response.error) {
        showNotification(
          'Error',
          response.error.message || UI_CONSTANTS.NOTIFICATIONS.SAVE_ERROR,
          <IconSquareRoundedX />,
          theme.colors.red[6],
        );
      } else {
        const savedConfig = response.data || { ...configToSave, version: config.version + 1 };
        onSave?.(savedConfig);
      }
    } catch {
      showNotification(
        'Error',
        UI_CONSTANTS.NOTIFICATIONS.SAVE_ERROR,
        <IconSquareRoundedX />,
        theme.colors.red[6],
      );
    } finally {
      setIsSaving(false);
    }
  };

  // Reset to original
  const handleReset = () => {
    setConfig(originalConfig);
    setDescription('');
    showNotification(
      'Reset',
      'Configuration reset to original',
      <IconRefresh />,
      theme.colors.blue[6],
    );
  };

  return (
    <Box className={classes.pageContainer}>
      <LoadingOverlay visible={isLoading} />
      
      {/* Header */}
      <Box className={classes.header}>
        <Box className={classes.headerLeft}>
          <Button
            variant="subtle"
            leftSection={<IconArrowLeft size={18} />}
            onClick={onCancel}
            mr="md"
          >
            Back to List
          </Button>
          <Box>
            <Group gap="sm">
              <Text className={classes.pageTitle}>
                {isViewMode ? 'View Configuration' : 'Create New Configuration'}
              </Text>
              {viewingVersion && (
                <Badge size="lg" variant="light" color={isViewMode ? 'blue' : 'teal'}>
                  {isViewMode ? `Viewing v${viewingVersion}` : `Based on v${viewingVersion}`}
                </Badge>
              )}
              {isViewMode && (
                <Badge size="sm" variant="light" color="gray" leftSection={<IconEye size={12} />}>
                  Read-only
                </Badge>
              )}
            </Group>
            <Text className={classes.pageSubtitle}>
              {isViewMode 
                ? 'Viewing configuration details. Click "Create from this" to make changes.'
                : 'Configure sampling, filters, and features. Save to create a new version.'}
            </Text>
          </Box>
        </Box>
        <Box className={classes.headerActions}>
          {isDirty && !isViewMode && (
            <Badge className={classes.unsavedBadge}>Unsaved changes</Badge>
          )}
          <Button
            variant="subtle"
            leftSection={<IconCode size={18} />}
            onClick={() => setShowConfigModal(true)}
          >
            {UI_CONSTANTS.ACTIONS.VIEW_JSON}
          </Button>
          {isViewMode ? (
            <Button
              leftSection={<IconEdit size={18} />}
              onClick={onEdit}
            >
              Create from this
            </Button>
          ) : (
            <>
              <Button
                variant="subtle"
                leftSection={<IconRefresh size={18} />}
                onClick={handleReset}
                disabled={isSaving || !isDirty}
              >
                {UI_CONSTANTS.ACTIONS.RESET}
              </Button>
              <Button
                leftSection={<IconDeviceFloppy size={18} />}
                onClick={handleSave}
                loading={isSaving}
                disabled={!isDirty}
              >
                {isSaving ? UI_CONSTANTS.ACTIONS.SAVING : 'Save as New Version'}
              </Button>
            </>
          )}
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
          />
        </Paper>
      )}

      {/* Pipeline Visualization */}
      <DataPipeline stats={pipelineStats} isLoading={isLoading} />

      {/* Configuration Sections */}
      <Box className={classes.configPanel} style={{ pointerEvents: isViewMode ? 'none' : 'auto', opacity: isViewMode ? 0.9 : 1 }}>
        {/* Filters Configuration */}
        <FiltersConfig
          config={config.filtersConfig}
          onChange={(filtersConfig) => !isViewMode && setConfig({ ...config, filtersConfig })}
        />

        {/* Sampling Configuration */}
        <SamplingRulesConfig
          config={config.samplingConfig}
          onChange={(samplingConfig) => !isViewMode && setConfig({ ...config, samplingConfig })}
        />

        {/* Critical Events */}
        <CriticalEventsConfig
          config={config.samplingConfig.criticalEventPolicies}
          onChange={(criticalEventPolicies) => !isViewMode && setConfig({
            ...config,
            samplingConfig: { ...config.samplingConfig, criticalEventPolicies },
          })}
        />

        {/* Feature Configurations */}
        <FeatureToggles
          configs={config.featureConfigs}
          onChange={(featureConfigs) => !isViewMode && setConfig({ ...config, featureConfigs })}
        />

        {/* Infrastructure Settings (Read-only) */}
        <InfraConfig
          signals={config.signals}
          interaction={config.interaction}
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
            <Badge size="sm" variant="light" color="gray">v{config.version}</Badge>
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
            >
              Save as New Version
            </Button>
          )}
        </Group>
      </Modal>
    </Box>
  );
}

