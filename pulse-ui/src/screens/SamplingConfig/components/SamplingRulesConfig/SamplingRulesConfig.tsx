/**
 * Sampling Rules Configuration Component
 * Manages default sample rate and version-based sampling rules
 */

import { useState } from 'react';
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
  SamplingConfig,
  SamplingRule,
  SdkEnum,
  SamplingMatchType,
} from '../../SamplingConfig.interface';
import {
  SDK_OPTIONS,
  SAMPLING_MATCH_TYPE_OPTIONS,
  generateId,
  UI_CONSTANTS,
} from '../../SamplingConfig.constants';
import classes from '../../SamplingConfig.module.css';

interface SamplingRulesConfigProps {
  config: SamplingConfig;
  onChange: (config: SamplingConfig) => void;
}

export function SamplingRulesConfig({ config, onChange }: SamplingRulesConfigProps) {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingRule, setEditingRule] = useState<SamplingRule | null>(null);
  
  // Form state
  const [ruleName, setRuleName] = useState('');
  const [matchType, setMatchType] = useState<SamplingMatchType>('app_version_min');
  const [matchVersion, setMatchVersion] = useState('');
  const [matchSdks, setMatchSdks] = useState<SdkEnum[]>([]);
  const [sampleRate, setSampleRate] = useState(0.5);

  const resetForm = () => {
    setRuleName('');
    setMatchType('app_version_min');
    setMatchVersion('');
    setMatchSdks([]);
    setSampleRate(0.5);
    setEditingRule(null);
  };

  const openAddModal = () => {
    resetForm();
    setIsModalOpen(true);
  };

  const openEditModal = (rule: SamplingRule) => {
    setEditingRule(rule);
    setRuleName(rule.name);
    setMatchType(rule.match.type);
    setMatchVersion(
      rule.match.type === 'app_version_min' 
        ? rule.match.app_version_min_inclusive || ''
        : rule.match.app_version_max_inclusive || ''
    );
    setMatchSdks(rule.match.sdks);
    setSampleRate(rule.session_sample_rate);
    setIsModalOpen(true);
  };

  const handleSaveRule = () => {
    const newRule: SamplingRule = {
      id: editingRule?.id || generateId(),
      name: ruleName.trim(),
      match: {
        type: matchType,
        sdks: matchSdks,
        ...(matchType === 'app_version_min' 
          ? { app_version_min_inclusive: matchVersion }
          : { app_version_max_inclusive: matchVersion }
        ),
      },
      session_sample_rate: sampleRate,
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
    onChange({
      ...config,
      rules: config.rules.filter(r => r.id !== ruleId),
    });
  };

  const handleDefaultRateChange = (rate: number) => {
    onChange({
      ...config,
      default: { session_sample_rate: rate },
    });
  };

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
              adjust based on your debugging needs. Use version-based rules to sample more for newer app versions.
            </Text>
          </Alert>

          {/* Default Sample Rate */}
          <Box mb="xl">
            <Group justify="space-between" mb="md">
              <Box>
                <Group gap="xs">
                  <Text fw={600}>Default Session Sample Rate</Text>
                  <Tooltip 
                    label="This rate applies to all sessions that don't match any specific rules below. 50% means half of all sessions will be recorded."
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
                {Math.round(config.default.session_sample_rate * 100)}%
              </Text>
            </Group>
            <Slider
              value={config.default.session_sample_rate}
              onChange={handleDefaultRateChange}
              min={0}
              max={1}
              step={0.05}
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
            <Text size="xs" c="dimmed" mt="xs" ta="center">
              At {Math.round(config.default.session_sample_rate * 100)}%, approximately {Math.round(config.default.session_sample_rate * 1000)} out of 1,000 sessions will be recorded
            </Text>
          </Box>

          <Divider my="lg" label="Version-Based Rules" labelPosition="center" />

          {/* Rules Section */}
          <Group justify="space-between" mb="md">
            <Box>
              <Group gap="xs">
                <Text fw={600}>{UI_CONSTANTS.SECTIONS.SAMPLING_RULES.TITLE}</Text>
                <Tooltip 
                  label="Override the default sample rate for specific app versions. Rules are evaluated in order - first matching rule wins."
                  multiline
                  w={280}
                  withArrow
                >
                  <IconInfoCircle size={14} style={{ color: '#868e96', cursor: 'help' }} />
                </Tooltip>
              </Group>
              <Text size="xs" c="dimmed">{UI_CONSTANTS.SECTIONS.SAMPLING_RULES.DESCRIPTION}</Text>
            </Box>
            <Button size="xs" leftSection={<IconPlus size={14} />} onClick={openAddModal}>
              Add Rule
            </Button>
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
                    <strong>Example:</strong> Sample 100% of users on v2.0.0+ to catch bugs in new releases, 
                    while keeping 10% for older versions to save costs.
                  </Text>
                </Group>
              </Paper>
            </Box>
          ) : (
            <Stack gap="xs">
              {config.rules.map(rule => (
                <Paper key={rule.id} withBorder p="sm">
                  <Group justify="space-between">
                    <Box>
                      <Group gap="xs" mb="xs">
                        <Text fw={600}>{rule.name}</Text>
                        <Badge size="sm" color="teal" variant="light">
                          {Math.round(rule.session_sample_rate * 100)}%
                        </Badge>
                      </Group>
                      <Group gap="xs">
                        <Badge size="xs" variant="outline">
                          {rule.match.type === 'app_version_min' ? '≥' : '≤'} v
                          {rule.match.app_version_min_inclusive || rule.match.app_version_max_inclusive}
                        </Badge>
                        {rule.match.sdks.slice(0, 2).map(sdk => (
                          <Badge key={sdk} size="xs" variant="dot">
                            {SDK_OPTIONS.find(s => s.value === sdk)?.label || sdk}
                          </Badge>
                        ))}
                        {rule.match.sdks.length > 2 && (
                          <Badge size="xs" variant="dot" color="gray">
                            +{rule.match.sdks.length - 2}
                          </Badge>
                        )}
                      </Group>
                    </Box>
                    <Group gap="xs">
                      <ActionIcon variant="subtle" onClick={() => openEditModal(rule)}>
                        <IconEdit size={16} />
                      </ActionIcon>
                      <ActionIcon variant="subtle" color="red" onClick={() => handleRemoveRule(rule.id || '')}>
                        <IconTrash size={16} />
                      </ActionIcon>
                    </Group>
                  </Group>
                </Paper>
              ))}
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
        <Stack gap="md">
          <TextInput
            label="Rule Name"
            placeholder="e.g., high_value_users, beta_testers"
            value={ruleName}
            onChange={(e) => setRuleName(e.currentTarget.value)}
            required
          />

          <Select
            label="Match Type"
            data={SAMPLING_MATCH_TYPE_OPTIONS.map(o => ({ 
              value: o.value, 
              label: o.label,
              description: o.description,
            }))}
            value={matchType}
            onChange={(v) => setMatchType(v as SamplingMatchType)}
            required
          />

          <TextInput
            label={matchType === 'app_version_min' ? 'Minimum Version (inclusive)' : 'Maximum Version (inclusive)'}
            placeholder="e.g., 1.0.0, 2.5.1"
            description="Semantic version format: major.minor.patch"
            value={matchVersion}
            onChange={(e) => setMatchVersion(e.currentTarget.value)}
            required
          />

          <MultiSelect
            label="Target SDKs"
            placeholder="Select SDKs this rule applies to"
            data={SDK_OPTIONS.map(s => ({ value: s.value, label: s.label }))}
            value={matchSdks}
            onChange={(v) => setMatchSdks(v as SdkEnum[])}
            required
          />

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
              disabled={!ruleName.trim() || !matchVersion.trim() || matchSdks.length === 0}
            >
              {editingRule ? 'Update Rule' : 'Add Rule'}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}

