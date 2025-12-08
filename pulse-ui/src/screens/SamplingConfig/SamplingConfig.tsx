/**
 * Sampling Configuration Screen
 * 
 * Main panel for managing SDK configuration including:
 * - Event filtering (whitelist/blacklist)
 * - Sampling rules and critical event policies
 * - Signal collection settings
 * - Feature configurations
 */

import { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Text,
  Button,
  Tabs,
  LoadingOverlay,
  Badge,
  useMantineTheme,
} from '@mantine/core';
import {
  IconFilter,
  IconPercentage,
  IconAntenna,
  IconClick,
  IconSettings,
  IconDeviceFloppy,
  IconX,
  IconCircleCheckFilled,
  IconSquareRoundedX,
  IconChartPie,
} from '@tabler/icons-react';
import { SDKConfig, ConfigTab } from './SamplingConfig.interface';
import { SAMPLING_CONFIG_CONSTANTS, DEFAULT_SDK_CONFIG } from './SamplingConfig.constants';
import { FiltersConfig } from './components/FiltersConfig';
import { SamplingRules } from './components/SamplingRules';
import { SignalsConfig } from './components/SignalsConfig';
import { InteractionConfig } from './components/InteractionConfig';
import { FeatureConfigs } from './components/FeatureConfigs';
import { makeRequest } from '../../helpers/makeRequest';
import { API_BASE_URL, API_METHODS } from '../../constants';
import { showNotification } from '../../helpers/showNotification';
import classes from './SamplingConfig.module.css';

// API endpoints
const SDK_CONFIG_API = '/v1/sdk-config';

export function SamplingConfig() {
  const theme = useMantineTheme();
  const [config, setConfig] = useState<SDKConfig>(DEFAULT_SDK_CONFIG);
  const [originalConfig, setOriginalConfig] = useState<SDKConfig>(DEFAULT_SDK_CONFIG);
  const [activeTab, setActiveTab] = useState<ConfigTab>('overview');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isDirty, setIsDirty] = useState(false);

  const loadConfig = useCallback(async () => {
    setIsLoading(true);
    try {
      const response = await makeRequest<SDKConfig>({
        url: `${API_BASE_URL}${SDK_CONFIG_API}`,
        init: {
          method: API_METHODS.GET,
        },
      });

      if (response.error) {
        showNotification(
          'Error',
          response.error.message || SAMPLING_CONFIG_CONSTANTS.NOTIFICATIONS.LOAD_ERROR,
          <IconSquareRoundedX />,
          theme.colors.red[6],
        );
        // Use default config on error
        setConfig(DEFAULT_SDK_CONFIG);
        setOriginalConfig(DEFAULT_SDK_CONFIG);
      } else if (response.data) {
        setConfig(response.data);
        setOriginalConfig(response.data);
      }
    } catch {
      showNotification(
        'Error',
        SAMPLING_CONFIG_CONSTANTS.NOTIFICATIONS.LOAD_ERROR,
        <IconSquareRoundedX />,
        theme.colors.red[6],
      );
      setConfig(DEFAULT_SDK_CONFIG);
      setOriginalConfig(DEFAULT_SDK_CONFIG);
    } finally {
      setIsLoading(false);
    }
  }, [theme.colors.red]);

  // Load configuration on mount
  useEffect(() => {
    loadConfig();
  }, [loadConfig]);

  // Track dirty state
  useEffect(() => {
    setIsDirty(JSON.stringify(config) !== JSON.stringify(originalConfig));
  }, [config, originalConfig]);

  const handleSave = async () => {
    setIsSaving(true);
    try {
      const response = await makeRequest<SDKConfig>({
        url: `${API_BASE_URL}${SDK_CONFIG_API}`,
        init: {
          method: API_METHODS.PUT,
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify(config),
        },
      });

      if (response.error) {
        showNotification(
          'Error',
          response.error.message || SAMPLING_CONFIG_CONSTANTS.NOTIFICATIONS.SAVE_ERROR,
          <IconSquareRoundedX />,
          theme.colors.red[6],
        );
      } else {
        showNotification(
          'Success',
          SAMPLING_CONFIG_CONSTANTS.NOTIFICATIONS.SAVE_SUCCESS,
          <IconCircleCheckFilled />,
          theme.colors.teal[6],
        );
        setOriginalConfig(config);
        setIsDirty(false);
      }
    } catch {
      showNotification(
        'Error',
        SAMPLING_CONFIG_CONSTANTS.NOTIFICATIONS.SAVE_ERROR,
        <IconSquareRoundedX />,
        theme.colors.red[6],
      );
    } finally {
      setIsSaving(false);
    }
  };

  const handleCancel = () => {
    setConfig(originalConfig);
    setIsDirty(false);
  };

  const handleUpdate = useCallback((updates: Partial<SDKConfig>) => {
    setConfig(prev => ({ ...prev, ...updates }));
  }, []);

  // Calculate overview stats
  const stats = {
    filterRules: config.filtersConfig.mode === 'WHITELIST' 
      ? config.filtersConfig.whitelist.length 
      : config.filtersConfig.blacklist.length,
    samplingRules: config.samplingConfig.rules.length,
    criticalPolicies: config.samplingConfig.criticalEventPolicies.alwaysSend.length,
    activeFeatures: config.featureConfigs.filter(f => f.enabled).length,
    totalFeatures: config.featureConfigs.length,
    droppedAttributes: config.signalsConfig.attributesToDrop.length,
    defaultSampleRate: Math.round(config.samplingConfig.default.session_sample_rate * 100),
  };

  return (
    <Box className={classes.pageContainer}>
      <LoadingOverlay visible={isLoading} />

      {/* Header */}
      <Box className={classes.pageHeader}>
        <Box className={classes.headerContent}>
          <Box className={classes.titleSection}>
            <Box className={classes.titleRow}>
              <Text className={classes.pageTitle}>
                {SAMPLING_CONFIG_CONSTANTS.PAGE_TITLE}
              </Text>
              {config.version && (
                <Badge className={classes.versionBadge}>
                  v{config.version}
                </Badge>
              )}
              {isDirty && (
                <Badge color="yellow" variant="light">
                  Unsaved changes
                </Badge>
              )}
            </Box>
            <Text className={classes.pageSubtitle}>
              {SAMPLING_CONFIG_CONSTANTS.PAGE_SUBTITLE}
            </Text>
          </Box>

          <Box className={classes.headerActions}>
            {isDirty && (
              <Button
                variant="light"
                className={classes.cancelButton}
                leftSection={<IconX size={16} />}
                onClick={handleCancel}
              >
                Cancel
              </Button>
            )}
            <Button
              className={classes.saveButton}
              leftSection={<IconDeviceFloppy size={16} />}
              loading={isSaving}
              disabled={!isDirty}
              onClick={handleSave}
            >
              Save Configuration
            </Button>
          </Box>
        </Box>
      </Box>

      {/* Tabs */}
      <Box className={classes.tabsContainer}>
        <Tabs
          value={activeTab}
          onChange={(value) => setActiveTab(value as ConfigTab)}
          className={classes.tabs}
        >
          <Tabs.List>
            <Tabs.Tab value="overview" leftSection={<IconChartPie size={16} />}>
              {SAMPLING_CONFIG_CONSTANTS.TABS.OVERVIEW}
            </Tabs.Tab>
            <Tabs.Tab value="filters" leftSection={<IconFilter size={16} />}>
              {SAMPLING_CONFIG_CONSTANTS.TABS.FILTERS}
            </Tabs.Tab>
            <Tabs.Tab value="sampling" leftSection={<IconPercentage size={16} />}>
              {SAMPLING_CONFIG_CONSTANTS.TABS.SAMPLING}
            </Tabs.Tab>
            <Tabs.Tab value="signals" leftSection={<IconAntenna size={16} />}>
              {SAMPLING_CONFIG_CONSTANTS.TABS.SIGNALS}
            </Tabs.Tab>
            <Tabs.Tab value="interaction" leftSection={<IconClick size={16} />}>
              {SAMPLING_CONFIG_CONSTANTS.TABS.INTERACTION}
            </Tabs.Tab>
            <Tabs.Tab value="features" leftSection={<IconSettings size={16} />}>
              {SAMPLING_CONFIG_CONSTANTS.TABS.FEATURES}
            </Tabs.Tab>
          </Tabs.List>

          {/* Overview Tab */}
          <Tabs.Panel value="overview" className={classes.tabPanel}>
            <Box className={classes.overviewGrid}>
              <Box className={classes.overviewCard}>
                <Text className={classes.overviewValue}>{stats.defaultSampleRate}%</Text>
                <Text className={classes.overviewLabel}>Default Sample Rate</Text>
              </Box>
              <Box className={classes.overviewCard}>
                <Text className={classes.overviewValue}>{stats.filterRules}</Text>
                <Text className={classes.overviewLabel}>
                  {config.filtersConfig.mode.toLowerCase()} rules
                </Text>
              </Box>
              <Box className={classes.overviewCard}>
                <Text className={classes.overviewValue}>{stats.samplingRules}</Text>
                <Text className={classes.overviewLabel}>Sampling Rules</Text>
              </Box>
              <Box className={classes.overviewCard}>
                <Text className={classes.overviewValue}>{stats.criticalPolicies}</Text>
                <Text className={classes.overviewLabel}>Critical Policies</Text>
              </Box>
              <Box className={classes.overviewCard}>
                <Text className={classes.overviewValue}>
                  {stats.activeFeatures}/{stats.totalFeatures}
                </Text>
                <Text className={classes.overviewLabel}>Active Features</Text>
              </Box>
              <Box className={classes.overviewCard}>
                <Text className={classes.overviewValue}>{stats.droppedAttributes}</Text>
                <Text className={classes.overviewLabel}>Dropped Attributes</Text>
              </Box>
            </Box>

            <Text size="sm" c="dimmed" ta="center" mt="xl">
              Use the tabs above to configure each section, or click on a card to jump directly to that configuration.
            </Text>
          </Tabs.Panel>

          {/* Filters Tab */}
          <Tabs.Panel value="filters" className={classes.tabPanel}>
            <FiltersConfig config={config} onUpdate={handleUpdate} />
          </Tabs.Panel>

          {/* Sampling Tab */}
          <Tabs.Panel value="sampling" className={classes.tabPanel}>
            <SamplingRules config={config} onUpdate={handleUpdate} />
          </Tabs.Panel>

          {/* Signals Tab */}
          <Tabs.Panel value="signals" className={classes.tabPanel}>
            <SignalsConfig config={config} onUpdate={handleUpdate} />
          </Tabs.Panel>

          {/* Interaction Tab */}
          <Tabs.Panel value="interaction" className={classes.tabPanel}>
            <InteractionConfig config={config} onUpdate={handleUpdate} />
          </Tabs.Panel>

          {/* Features Tab */}
          <Tabs.Panel value="features" className={classes.tabPanel}>
            <FeatureConfigs config={config} onUpdate={handleUpdate} />
          </Tabs.Panel>
        </Tabs>
      </Box>
    </Box>
  );
}

