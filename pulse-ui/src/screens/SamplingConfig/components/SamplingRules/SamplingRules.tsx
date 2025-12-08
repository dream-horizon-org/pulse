/**
 * Sampling Rules Configuration Component
 * Manages session sampling rates and critical event policies
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
  Select,
  Stack,
  Tooltip,
  Collapse,
  Divider,
  NumberInput,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconPercentage,
  IconPlus,
  IconTrash,
  IconEdit,
  IconChevronDown,
  IconChevronRight,
  IconAlertTriangle,
  IconShield,
} from '@tabler/icons-react';
import { v4 as uuid } from 'uuid';
import {
  SamplingRule,
  CriticalEventPolicy,
  SectionProps,
  SDK_OPTIONS,
  SCOPE_OPTIONS,
  MATCH_TYPE_OPTIONS,
  PropertyFilter,
  SDKType,
  ScopeType,
  MatchType,
} from '../../SamplingConfig.interface';
import { SAMPLING_CONFIG_CONSTANTS } from '../../SamplingConfig.constants';
import { SdkTag, ScopeTag, SamplingRateSlider, EmptyState } from '../shared';
import classes from '../../SamplingConfig.module.css';

interface SamplingRuleFormData {
  id?: string;
  name: string;
  matchType: string;
  matchValue: string;
  sdks: string[];
  sampleRate: number;
}

interface CriticalEventFormData {
  id?: string;
  name: string;
  props: PropertyFilter[];
  scope: string[];
}

const defaultRuleFormData: SamplingRuleFormData = {
  name: '',
  matchType: 'APP_VERSION_MIN',
  matchValue: '',
  sdks: ['ANDROID'],
  sampleRate: 0.5,
};

const defaultCriticalFormData: CriticalEventFormData = {
  name: '',
  props: [],
  scope: ['TRACES'],
};

export function SamplingRules({ config, onUpdate }: SectionProps) {
  const [ruleModalOpened, { open: openRuleModal, close: closeRuleModal }] = useDisclosure(false);
  const [criticalModalOpened, { open: openCriticalModal, close: closeCriticalModal }] = useDisclosure(false);
  const [expanded, setExpanded] = useState(true);
  const [editingRule, setEditingRule] = useState<SamplingRuleFormData | null>(null);
  const [editingCritical, setEditingCritical] = useState<CriticalEventFormData | null>(null);
  const [ruleFormData, setRuleFormData] = useState<SamplingRuleFormData>(defaultRuleFormData);
  const [criticalFormData, setCriticalFormData] = useState<CriticalEventFormData>(defaultCriticalFormData);
  const [propName, setPropName] = useState('');
  const [propValue, setPropValue] = useState('');

  const { samplingConfig } = config;

  const handleDefaultRateChange = (rate: number) => {
    onUpdate({
      samplingConfig: {
        ...samplingConfig,
        default: {
          session_sample_rate: rate,
        },
      },
    });
  };

  // Sampling Rules handlers
  const handleAddRule = () => {
    setEditingRule(null);
    setRuleFormData(defaultRuleFormData);
    openRuleModal();
  };

  const handleEditRule = (rule: SamplingRule) => {
    setEditingRule({
      id: rule.id,
      name: rule.name,
      matchType: rule.match.type,
      matchValue: rule.match.value,
      sdks: rule.match.sdks,
      sampleRate: rule.session_sample_rate,
    });
    setRuleFormData({
      id: rule.id,
      name: rule.name,
      matchType: rule.match.type,
      matchValue: rule.match.value,
      sdks: rule.match.sdks,
      sampleRate: rule.session_sample_rate,
    });
    openRuleModal();
  };

  const handleDeleteRule = (ruleId: string) => {
    onUpdate({
      samplingConfig: {
        ...samplingConfig,
        rules: samplingConfig.rules.filter(r => r.id !== ruleId),
      },
    });
  };

  const handleSaveRule = () => {
    const newRule: SamplingRule = {
      id: editingRule?.id || uuid(),
      name: ruleFormData.name,
      match: {
        type: ruleFormData.matchType as MatchType,
        sdks: ruleFormData.sdks as SDKType[],
        value: ruleFormData.matchValue,
      },
      session_sample_rate: ruleFormData.sampleRate,
    };

    if (editingRule?.id) {
      onUpdate({
        samplingConfig: {
          ...samplingConfig,
          rules: samplingConfig.rules.map(r => 
            r.id === editingRule.id ? newRule : r
          ),
        },
      });
    } else {
      onUpdate({
        samplingConfig: {
          ...samplingConfig,
          rules: [...samplingConfig.rules, newRule],
        },
      });
    }

    closeRuleModal();
  };

  // Critical Event Policy handlers
  const handleAddCritical = () => {
    setEditingCritical(null);
    setCriticalFormData(defaultCriticalFormData);
    setPropName('');
    setPropValue('');
    openCriticalModal();
  };

  const handleEditCritical = (policy: CriticalEventPolicy) => {
    setEditingCritical({
      id: policy.id,
      name: policy.name,
      props: policy.props,
      scope: policy.scope,
    });
    setCriticalFormData({
      id: policy.id,
      name: policy.name,
      props: [...policy.props],
      scope: [...policy.scope],
    });
    openCriticalModal();
  };

  const handleDeleteCritical = (policyId: string) => {
    onUpdate({
      samplingConfig: {
        ...samplingConfig,
        criticalEventPolicies: {
          alwaysSend: samplingConfig.criticalEventPolicies.alwaysSend.filter(p => p.id !== policyId),
        },
      },
    });
  };

  const handleAddProp = () => {
    if (propName && propValue) {
      setCriticalFormData({
        ...criticalFormData,
        props: [...criticalFormData.props, { name: propName, value: propValue }],
      });
      setPropName('');
      setPropValue('');
    }
  };

  const handleRemoveProp = (index: number) => {
    setCriticalFormData({
      ...criticalFormData,
      props: criticalFormData.props.filter((_, i) => i !== index),
    });
  };

  const handleSaveCritical = () => {
    const newPolicy: CriticalEventPolicy = {
      id: editingCritical?.id || uuid(),
      name: criticalFormData.name,
      props: criticalFormData.props,
      scope: criticalFormData.scope as ScopeType[],
    };

    if (editingCritical?.id) {
      onUpdate({
        samplingConfig: {
          ...samplingConfig,
          criticalEventPolicies: {
            alwaysSend: samplingConfig.criticalEventPolicies.alwaysSend.map(p => 
              p.id === editingCritical.id ? newPolicy : p
            ),
          },
        },
      });
    } else {
      onUpdate({
        samplingConfig: {
          ...samplingConfig,
          criticalEventPolicies: {
            alwaysSend: [...samplingConfig.criticalEventPolicies.alwaysSend, newPolicy],
          },
        },
      });
    }

    closeCriticalModal();
  };

  const totalRules = samplingConfig.rules.length + samplingConfig.criticalEventPolicies.alwaysSend.length;

  return (
    <Box className={classes.sectionCard}>
      <Box 
        className={classes.sectionHeader}
        onClick={() => setExpanded(!expanded)}
      >
        <Group className={classes.sectionTitleGroup}>
          <Box className={classes.sectionIcon}>
            <IconPercentage size={20} />
          </Box>
          <Box>
            <Text className={classes.sectionTitle}>Sampling Rules</Text>
            <Text className={classes.sectionDescription}>
              {SAMPLING_CONFIG_CONSTANTS.DESCRIPTIONS.SAMPLING}
            </Text>
          </Box>
        </Group>
        <Group gap="sm">
          <Text className={classes.sectionBadge} c="teal" bg="teal.0">
            {totalRules} {totalRules === 1 ? 'rule' : 'rules'}
          </Text>
          {expanded ? <IconChevronDown size={18} /> : <IconChevronRight size={18} />}
        </Group>
      </Box>

      <Collapse in={expanded}>
        <Box className={classes.sectionContent}>
          {/* Default Sampling Rate */}
          <SamplingRateSlider
            label="Default Session Rate"
            value={samplingConfig.default.session_sample_rate}
            onChange={handleDefaultRateChange}
          />

          <Divider my="lg" label="Conditional Sampling Rules" labelPosition="center" />

          {/* Sampling Rules */}
          {samplingConfig.rules.length === 0 ? (
            <EmptyState
              icon={<IconPercentage size={28} />}
              title="No conditional rules"
              description="Add rules to apply different sampling rates based on app version, device, or user segment"
              actionLabel="Add Sampling Rule"
              onAction={handleAddRule}
            />
          ) : (
            <>
              {samplingConfig.rules.map((rule) => (
                <Box key={rule.id || rule.name} className={`${classes.ruleCard} ${classes.fadeIn}`}>
                  <Box className={classes.ruleHeader}>
                    <Box>
                      <Text className={classes.ruleName}>{rule.name}</Text>
                      <Text size="xs" c="dimmed">
                        {MATCH_TYPE_OPTIONS.find(m => m.value === rule.match.type)?.label}: {rule.match.value}
                      </Text>
                    </Box>
                    <Group className={classes.ruleActions} gap={4}>
                      <Text fw={700} c="teal" size="sm" mr="sm">
                        {Math.round(rule.session_sample_rate * 100)}%
                      </Text>
                      <Tooltip label="Edit">
                        <ActionIcon 
                          variant="subtle" 
                          size="sm"
                          onClick={() => handleEditRule(rule)}
                        >
                          <IconEdit size={14} />
                        </ActionIcon>
                      </Tooltip>
                      <Tooltip label="Delete">
                        <ActionIcon 
                          variant="subtle" 
                          size="sm" 
                          color="red"
                          onClick={() => handleDeleteRule(rule.id || '')}
                        >
                          <IconTrash size={14} />
                        </ActionIcon>
                      </Tooltip>
                    </Group>
                  </Box>
                  <Group className={classes.ruleMeta}>
                    {rule.match.sdks.map((sdk) => (
                      <SdkTag key={sdk} sdk={sdk} />
                    ))}
                  </Group>
                </Box>
              ))}

              <Button
                className={classes.addButton}
                leftSection={<IconPlus size={16} />}
                variant="default"
                onClick={handleAddRule}
              >
                Add Sampling Rule
              </Button>
            </>
          )}

          <Divider my="lg" label="Critical Event Policies (Always Send)" labelPosition="center" />

          {/* Critical Event Policies */}
          {samplingConfig.criticalEventPolicies.alwaysSend.length === 0 ? (
            <EmptyState
              icon={<IconShield size={28} />}
              title="No critical event policies"
              description="Define events that should always be sent regardless of sampling rate"
              actionLabel="Add Critical Event"
              onAction={handleAddCritical}
            />
          ) : (
            <>
              {samplingConfig.criticalEventPolicies.alwaysSend.map((policy) => (
                <Box key={policy.id || policy.name} className={`${classes.ruleCard} ${classes.fadeIn}`}>
                  <Box className={classes.ruleHeader}>
                    <Group gap="xs">
                      <IconAlertTriangle size={16} color="#f59e0b" />
                      <Text className={classes.ruleName}>{policy.name}</Text>
                    </Group>
                    <Group className={classes.ruleActions} gap={4}>
                      <Tooltip label="Edit">
                        <ActionIcon 
                          variant="subtle" 
                          size="sm"
                          onClick={() => handleEditCritical(policy)}
                        >
                          <IconEdit size={14} />
                        </ActionIcon>
                      </Tooltip>
                      <Tooltip label="Delete">
                        <ActionIcon 
                          variant="subtle" 
                          size="sm" 
                          color="red"
                          onClick={() => handleDeleteCritical(policy.id || '')}
                        >
                          <IconTrash size={14} />
                        </ActionIcon>
                      </Tooltip>
                    </Group>
                  </Box>

                  {policy.props.length > 0 && (
                    <Box mb="xs">
                      <Text size="xs" c="dimmed" mb={4}>Properties:</Text>
                      {policy.props.map((prop, idx) => (
                        <Text key={idx} size="xs" c="dark.5" ml="sm">
                          <code>{prop.name}</code> = <code>{prop.value}</code>
                        </Text>
                      ))}
                    </Box>
                  )}

                  <Group className={classes.ruleMeta}>
                    {policy.scope.map((scope) => (
                      <ScopeTag key={scope} scope={scope} />
                    ))}
                  </Group>
                </Box>
              ))}

              <Button
                className={classes.addButton}
                leftSection={<IconPlus size={16} />}
                variant="default"
                onClick={handleAddCritical}
              >
                Add Critical Event Policy
              </Button>
            </>
          )}
        </Box>
      </Collapse>

      {/* Add/Edit Sampling Rule Modal */}
      <Modal
        opened={ruleModalOpened}
        onClose={closeRuleModal}
        title={editingRule ? 'Edit Sampling Rule' : 'Add Sampling Rule'}
        size="lg"
      >
        <Stack gap="md">
          <TextInput
            label="Rule Name"
            placeholder="e.g., high_value_users"
            value={ruleFormData.name}
            onChange={(e) => setRuleFormData({ ...ruleFormData, name: e.target.value })}
            required
          />

          <Select
            label="Match Type"
            data={MATCH_TYPE_OPTIONS.map(opt => ({
              value: opt.value,
              label: `${opt.label} - ${opt.description}`,
            }))}
            value={ruleFormData.matchType}
            onChange={(value) => setRuleFormData({ ...ruleFormData, matchType: value || 'APP_VERSION_MIN' })}
          />

          <TextInput
            label="Match Value"
            placeholder="e.g., 1.0.0"
            value={ruleFormData.matchValue}
            onChange={(e) => setRuleFormData({ ...ruleFormData, matchValue: e.target.value })}
            required
          />

          <MultiSelect
            label="SDKs"
            placeholder="Select SDKs"
            data={SDK_OPTIONS}
            value={ruleFormData.sdks}
            onChange={(sdks) => setRuleFormData({ ...ruleFormData, sdks })}
          />

          <NumberInput
            label="Session Sample Rate"
            description="Value between 0 and 1"
            value={ruleFormData.sampleRate}
            onChange={(value) => setRuleFormData({ ...ruleFormData, sampleRate: Number(value) || 0 })}
            min={0}
            max={1}
            step={0.1}
            decimalScale={2}
          />

          <Group justify="flex-end" mt="md">
            <Button variant="light" onClick={closeRuleModal}>
              Cancel
            </Button>
            <Button 
              onClick={handleSaveRule}
              disabled={!ruleFormData.name || !ruleFormData.matchValue || ruleFormData.sdks.length === 0}
            >
              {editingRule ? 'Update Rule' : 'Add Rule'}
            </Button>
          </Group>
        </Stack>
      </Modal>

      {/* Add/Edit Critical Event Modal */}
      <Modal
        opened={criticalModalOpened}
        onClose={closeCriticalModal}
        title={editingCritical ? 'Edit Critical Event Policy' : 'Add Critical Event Policy'}
        size="lg"
      >
        <Stack gap="md">
          <TextInput
            label="Event Name"
            placeholder="e.g., crash, payment_error"
            value={criticalFormData.name}
            onChange={(e) => setCriticalFormData({ ...criticalFormData, name: e.target.value })}
            required
          />

          <MultiSelect
            label="Scope"
            placeholder="Select scope"
            data={SCOPE_OPTIONS}
            value={criticalFormData.scope}
            onChange={(scope) => setCriticalFormData({ ...criticalFormData, scope })}
          />

          <Box>
            <Text size="sm" fw={500} mb="xs">Property Filters (Optional)</Text>
            <Group grow mb="xs">
              <TextInput
                placeholder="Property name"
                value={propName}
                onChange={(e) => setPropName(e.target.value)}
                size="sm"
              />
              <TextInput
                placeholder="Value (regex supported)"
                value={propValue}
                onChange={(e) => setPropValue(e.target.value)}
                size="sm"
              />
              <Button 
                size="sm" 
                variant="light"
                onClick={handleAddProp}
                disabled={!propName || !propValue}
              >
                Add
              </Button>
            </Group>

            {criticalFormData.props.length > 0 && (
              <Stack gap="xs">
                {criticalFormData.props.map((prop, idx) => (
                  <Group key={idx} justify="space-between" p="xs" bg="gray.0" style={{ borderRadius: 4 }}>
                    <Text size="sm">
                      <code>{prop.name}</code> = <code>{prop.value}</code>
                    </Text>
                    <ActionIcon 
                      size="sm" 
                      variant="subtle" 
                      color="red"
                      onClick={() => handleRemoveProp(idx)}
                    >
                      <IconTrash size={14} />
                    </ActionIcon>
                  </Group>
                ))}
              </Stack>
            )}
          </Box>

          <Group justify="flex-end" mt="md">
            <Button variant="light" onClick={closeCriticalModal}>
              Cancel
            </Button>
            <Button 
              onClick={handleSaveCritical}
              disabled={!criticalFormData.name || criticalFormData.scope.length === 0}
            >
              {editingCritical ? 'Update Policy' : 'Add Policy'}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Box>
  );
}

