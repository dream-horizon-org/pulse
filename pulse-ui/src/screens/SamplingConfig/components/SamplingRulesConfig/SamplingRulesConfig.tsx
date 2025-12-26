/**
 * Sampling Rules Configuration Component
 * Manages default sample rate and device-params-based sampling rules
 * 
 * Uses dynamic data from backend:
 * - GET /v1/configs/rules-features for available rule types
 * - GET /v1/configs/scopes-sdks for available SDKs
 */

import { useState, useMemo } from 'react';
import {
  Box,
  Text,
  Button,
  TextInput,
  ActionIcon,
  Group,
  Badge,
  Modal,
  Select,
  MultiSelect,
  Stack,
  Paper,
  Slider,
  Divider,
  Tooltip,
  Alert,
  Loader,
} from '@mantine/core';
import {
  IconPercentage,
  IconPlus,
  IconTrash,
  IconEdit,
  IconInfoCircle,
  IconBulb,
} from '@tabler/icons-react';
import {
  SamplingRule,
  SdkEnum,
  SamplingRuleName,
  SamplingConfigProps,
} from '../../SamplingConfig.interface';
import {
  toSdkOptions,
  toRuleOptions,
  SDK_DISPLAY_INFO,
  RULE_DISPLAY_INFO,
  generateId,
  UI_CONSTANTS,
} from '../../SamplingConfig.constants';
import { useGetSdkRulesAndFeatures, useGetSdkScopesAndSdks } from '../../../../hooks/useSdkConfig';
import classes from '../../SamplingConfig.module.css';

export function SamplingRulesConfig({ config, onChange, disabled = false }: SamplingConfigProps) {
  // Fetch dynamic options from backend
  const { data: rulesAndFeatures, isLoading: isLoadingRules } = useGetSdkRulesAndFeatures();
  const { data: scopesAndSdks, isLoading: isLoadingSdks } = useGetSdkScopesAndSdks();
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<SamplingRule | null>(null);
  
  // Form state
  const [ruleName, setRuleName] = useState<SamplingRuleName | ''>('');
  const [ruleValue, setRuleValue] = useState('');
  const [ruleSdks, setRuleSdks] = useState<SdkEnum[]>([]);
  const [sampleRate, setSampleRate] = useState(0.5);

  // Convert backend data to select options
  const ruleOptions = useMemo(() => {
    if (rulesAndFeatures?.data?.rules) {
      return toRuleOptions(rulesAndFeatures.data.rules);
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

  const resetForm = () => {
    setRuleName('');
    setRuleValue('');
    setRuleSdks([]);
    setSampleRate(0.5);
    setEditingRule(null);
  };

  const openAddModal = () => {
    if (disabled) return;
    resetForm();
    setIsModalOpen(true);
  };

  const openEditModal = (rule: SamplingRule) => {
    if (disabled) return;
    setEditingRule(rule);
    setRuleName(rule.name);
    setRuleValue(rule.value);
    setRuleSdks(rule.sdks);
    setSampleRate(rule.sessionSampleRate);
    setIsModalOpen(true);
  };

  const handleSaveRule = () => {
    if (!ruleName) return;
    
    const newRule: SamplingRule = {
      id: editingRule?.id || generateId(),
      name: ruleName,
      value: ruleValue.trim(),
      sdks: ruleSdks,
      sessionSampleRate: sampleRate,
    };

    if (editingRule) {
      onChange({
        ...config,
        rules: config.rules.map(r => r.id === editingRule.id ? newRule : r),
      });
    } else {
      onChange({
        ...config,
        rules: [...config.rules, newRule],
      });
    }

    setIsModalOpen(false);
    resetForm();
  };

  const handleRemoveRule = (ruleId: string) => {
    if (disabled) return;
    onChange({
      ...config,
      rules: config.rules.filter(r => r.id !== ruleId),
    });
  };

  const handleDefaultRateChange = (rate: number) => {
    if (disabled) return;
    onChange({
      ...config,
      default: { sessionSampleRate: rate },
    });
  };

  const getRuleDisplay = (name: SamplingRuleName) => {
    return RULE_DISPLAY_INFO[name] || { 
      label: name, 
      description: 'Custom rule' 
    };
  };

  const getSdkLabel = (sdk: SdkEnum) => {
    return SDK_DISPLAY_INFO[sdk]?.label || sdk;
  };

  // Get placeholder text based on rule type
  const getPlaceholder = (rule: SamplingRuleName | '') => {
    switch (rule) {
      case 'app_version': return 'e.g., 2.0.0 or 2\\..*';
      case 'os_version': return 'e.g., 14 or 15\\..*';
      case 'country': return 'e.g., US, IN, GB (ISO country code)';
      case 'state': return 'e.g., CA, NY (ISO region code)';
      case 'device': return 'e.g., Samsung.*, Pixel.*, iPhone.*';
      case 'network': return 'e.g., wifi, cellular, 4g';
      case 'platform': return 'e.g., android, ios';
      default: return 'Enter value or regex pattern';
    }
  };

  const isLoading = isLoadingRules || isLoadingSdks;

  return (
    <>
      <Box className={classes.card}>
        <Box className={classes.cardHeader}>
          <Box className={classes.cardHeaderLeft}>
            <Box className={`${classes.cardIcon} ${classes.volume}`}>
              <IconPercentage size={20} />
            </Box>
            <Box>
              <Text className={classes.cardTitle}>{UI_CONSTANTS.SECTIONS.SAMPLING.TITLE}</Text>
              <Text className={classes.cardDescription}>{UI_CONSTANTS.SECTIONS.SAMPLING.DESCRIPTION}</Text>
            </Box>
          </Box>
        </Box>
        
        <Box className={classes.cardContent}>
          {/* Explanation Alert */}
          <Alert 
            icon={<IconInfoCircle size={18} />} 
            color="cyan" 
            variant="light" 
            mb="lg"
            title="What is Session Sampling?"
          >
            <Text size="xs">
              Session sampling randomly selects a percentage of user sessions to track. 
              For example, at <strong>50%</strong>, half of your users will have their events recorded, 
              while the other half will not generate any telemetry data.
            </Text>
            <Text size="xs" mt="xs" c="dimmed">
              💡 <strong>Tip:</strong> Lower sampling reduces data volume and costs. Start at 50% and 
              adjust based on your debugging needs. Use rules to sample differently based on device parameters.
            </Text>
          </Alert>

          {/* Default Sample Rate */}
          <Box mb="xl">
            <Group justify="space-between" mb="md">
              <Box>
                <Group gap="xs">
                  <Text fw={600}>Default Session Sample Rate</Text>
                  <Tooltip 
                    label="This rate applies to all sessions that don't match any specific rules below."
                    multiline
                    w={280}
                    withArrow
                  >
                    <IconInfoCircle size={14} style={{ color: '#868e96', cursor: 'help' }} />
                  </Tooltip>
                </Group>
                <Text size="xs" c="dimmed">Applied when no specific rules match</Text>
              </Box>
              <Text size="xl" fw={700} style={{ color: '#0ec9c2' }}>
                {Math.round(config.default.sessionSampleRate * 100)}%
              </Text>
            </Group>
            <Slider
              value={config.default.sessionSampleRate}
              onChange={handleDefaultRateChange}
              min={0}
              max={1}
              step={0.05}
              disabled={disabled}
              marks={[
                { value: 0, label: '0%' },
                { value: 0.25, label: '25%' },
                { value: 0.5, label: '50%' },
                { value: 0.75, label: '75%' },
                { value: 1, label: '100%' },
              ]}
              styles={{
                track: { backgroundColor: 'rgba(14, 201, 194, 0.15)', height: 8 },
                bar: { background: 'linear-gradient(90deg, #0ec9c2 0%, #0ba09a 100%)' },
                thumb: { borderColor: '#0ba09a', backgroundColor: '#ffffff', width: 20, height: 20 },
                markLabel: { fontSize: 11, marginTop: 8 },
              }}
            />
            <Text size="xs" c="dimmed" mt="xl" ta="center">
              At {Math.round(config.default.sessionSampleRate * 100)}%, approximately {Math.round(config.default.sessionSampleRate * 1000)} out of 1,000 sessions will be recorded
            </Text>
          </Box>

          <Divider my="lg" label="Conditional Sampling Rules" labelPosition="center" />

          {/* Rules Section */}
          <Group justify="space-between" mb="md">
            <Box>
              <Group gap="xs">
                <Text fw={600}>{UI_CONSTANTS.SECTIONS.SAMPLING_RULES.TITLE}</Text>
                <Tooltip 
                  label="Override the default sample rate based on device parameters. Rules are evaluated in order."
                  multiline
                  w={280}
                  withArrow
                >
                  <IconInfoCircle size={14} style={{ color: '#868e96', cursor: 'help' }} />
                </Tooltip>
              </Group>
              <Text size="xs" c="dimmed">{UI_CONSTANTS.SECTIONS.SAMPLING_RULES.DESCRIPTION}</Text>
            </Box>
            {!disabled && (
              <Button size="xs" leftSection={<IconPlus size={14} />} onClick={openAddModal}>
                Add Rule
              </Button>
            )}
          </Group>

          {config.rules.length === 0 ? (
            <Box className={classes.emptyState}>
              <IconPercentage size={32} style={{ opacity: 0.3 }} />
              <Text size="sm" c="dimmed" mt="xs">No sampling rules configured</Text>
              <Text size="xs" c="dimmed">Default rate will be applied to all sessions</Text>
              <Paper p="sm" mt="md" radius="md" style={{ backgroundColor: '#fffbeb', border: '1px solid #fef3c7', maxWidth: 400 }}>
                <Group gap="xs" wrap="nowrap">
                  <IconBulb size={16} style={{ color: '#d97706', flexShrink: 0 }} />
                  <Text size="xs" c="yellow.8">
                    <strong>Example:</strong> Sample 100% of users on app_version &gt;= 2.0.0 to catch bugs in new releases, 
                    while keeping 10% for older versions to save costs.
                  </Text>
                </Group>
              </Paper>
            </Box>
          ) : (
            <Stack gap="xs">
              {config.rules.map(rule => {
                const display = getRuleDisplay(rule.name);
                return (
                  <Paper key={rule.id} withBorder p="sm">
                    <Group justify="space-between">
                      <Box>
                        <Group gap="xs" mb="xs">
                          <Text fw={600}>{display.label}</Text>
                          <Badge size="sm" color="teal" variant="light">
                            {Math.round(rule.sessionSampleRate * 100)}%
                          </Badge>
                        </Group>
                        <Group gap="xs">
                          <Badge size="xs" variant="outline">
                            {rule.name} = "{rule.value}"
                          </Badge>
                          {rule.sdks.slice(0, 2).map(sdk => (
                            <Badge key={sdk} size="xs" variant="dot">
                              {getSdkLabel(sdk)}
                            </Badge>
                          ))}
                          {rule.sdks.length > 2 && (
                            <Badge size="xs" variant="dot" color="gray">
                              +{rule.sdks.length - 2}
                            </Badge>
                          )}
                        </Group>
                      </Box>
                      {!disabled && (
                        <Group gap="xs">
                          <ActionIcon variant="subtle" onClick={() => openEditModal(rule)}>
                            <IconEdit size={16} />
                          </ActionIcon>
                          <ActionIcon variant="subtle" color="red" onClick={() => handleRemoveRule(rule.id || '')}>
                            <IconTrash size={16} />
                          </ActionIcon>
                        </Group>
                      )}
                    </Group>
                  </Paper>
                );
              })}
            </Stack>
          )}
        </Box>
      </Box>

      {/* Add/Edit Rule Modal */}
      <Modal
        opened={isModalOpen}
        onClose={() => { setIsModalOpen(false); resetForm(); }}
        title={editingRule ? 'Edit Sampling Rule' : 'Add Sampling Rule'}
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
              label="Rule Type"
              description="The device parameter to match against"
              placeholder="Select rule type"
              data={ruleOptions.map(o => ({ 
                value: o.value, 
                label: o.label,
              }))}
              value={ruleName}
              onChange={(v) => {
                setRuleName(v as SamplingRuleName);
                setRuleValue('');
              }}
              required
            />

            <TextInput
              label="Match Value"
              description="Enter the value or regex pattern to match"
              placeholder={getPlaceholder(ruleName)}
              value={ruleValue}
              onChange={(e) => setRuleValue(e.currentTarget.value)}
              required
            />

            <Box>
              <Group justify="space-between" mb="xs">
                <Text size="sm" fw={500}>Target SDKs</Text>
                <Button 
                  size="compact-xs" 
                  variant="subtle" 
                  onClick={() => setRuleSdks(allSdks)}
                  disabled={sdkOptions.length === 0}
                >
                  Select All
                </Button>
              </Group>
              <MultiSelect
                description="Which SDK platforms this rule applies to"
                placeholder="Select SDKs"
                data={sdkOptions.map(s => ({ value: s.value, label: s.label }))}
                value={ruleSdks}
                onChange={(v) => setRuleSdks(v as SdkEnum[])}
                required
              />
            </Box>

            <Box>
              <Group justify="space-between" mb="xs">
                <Text size="sm" fw={500}>Sample Rate</Text>
                <Text size="sm" fw={700} style={{ color: '#0ec9c2' }}>
                  {Math.round(sampleRate * 100)}%
                </Text>
              </Group>
              <Slider
                value={sampleRate}
                onChange={setSampleRate}
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

            <Group justify="flex-end" mt="md">
              <Button variant="subtle" onClick={() => { setIsModalOpen(false); resetForm(); }}>
                Cancel
              </Button>
              <Button
                onClick={handleSaveRule}
                disabled={!ruleName || !ruleValue.trim() || ruleSdks.length === 0}
              >
                {editingRule ? 'Update Rule' : 'Add Rule'}
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>
    </>
  );
}

export default SamplingRulesConfig;
