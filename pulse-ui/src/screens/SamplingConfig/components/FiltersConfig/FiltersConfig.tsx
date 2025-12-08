/**
 * Filters Configuration Component
 * Manages whitelist/blacklist event filtering rules
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
  SegmentedControl,
  Stack,
  Tooltip,
  Collapse,
} from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconFilter,
  IconPlus,
  IconTrash,
  IconEdit,
  IconCopy,
  IconChevronDown,
  IconChevronRight,
} from '@tabler/icons-react';
import { v4 as uuid } from 'uuid';
import {
  FilterRule,
  FilterMode,
  SectionProps,
  SDK_OPTIONS,
  SCOPE_OPTIONS,
  PropertyFilter,
  SDKType,
  ScopeType,
} from '../../SamplingConfig.interface';
import { SAMPLING_CONFIG_CONSTANTS } from '../../SamplingConfig.constants';
import { SdkTag, ScopeTag, EmptyState } from '../shared';
import classes from '../../SamplingConfig.module.css';

interface FilterRuleFormData {
  id?: string;
  name: string;
  props: PropertyFilter[];
  scope: string[];
  sdks: string[];
}

const defaultFormData: FilterRuleFormData = {
  name: '',
  props: [],
  scope: ['TRACES'],
  sdks: ['ANDROID'],
};

export function FiltersConfig({ config, onUpdate }: SectionProps) {
  const [modalOpened, { open: openModal, close: closeModal }] = useDisclosure(false);
  const [expanded, setExpanded] = useState(true);
  const [editingRule, setEditingRule] = useState<FilterRuleFormData | null>(null);
  const [formData, setFormData] = useState<FilterRuleFormData>(defaultFormData);
  const [propName, setPropName] = useState('');
  const [propValue, setPropValue] = useState('');

  const { filtersConfig } = config;
  const activeList = filtersConfig.mode === 'WHITELIST' 
    ? filtersConfig.whitelist 
    : filtersConfig.blacklist;

  const handleModeChange = (mode: string) => {
    onUpdate({
      filtersConfig: {
        ...filtersConfig,
        mode: mode as FilterMode,
      },
    });
  };

  const handleAddRule = () => {
    setEditingRule(null);
    setFormData(defaultFormData);
    setPropName('');
    setPropValue('');
    openModal();
  };

  const handleEditRule = (rule: FilterRule) => {
    setEditingRule({
      id: rule.id,
      name: rule.name,
      props: rule.props,
      scope: rule.scope,
      sdks: rule.sdks,
    });
    setFormData({
      id: rule.id,
      name: rule.name,
      props: [...rule.props],
      scope: [...rule.scope],
      sdks: [...rule.sdks],
    });
    openModal();
  };

  const handleDuplicateRule = (rule: FilterRule) => {
    const newRule: FilterRule = {
      ...rule,
      id: uuid(),
      name: `${rule.name}_copy`,
    };
    
    const listKey = filtersConfig.mode === 'WHITELIST' ? 'whitelist' : 'blacklist';
    onUpdate({
      filtersConfig: {
        ...filtersConfig,
        [listKey]: [...filtersConfig[listKey], newRule],
      },
    });
  };

  const handleDeleteRule = (ruleId: string) => {
    const listKey = filtersConfig.mode === 'WHITELIST' ? 'whitelist' : 'blacklist';
    onUpdate({
      filtersConfig: {
        ...filtersConfig,
        [listKey]: filtersConfig[listKey].filter(r => r.id !== ruleId),
      },
    });
  };

  const handleAddProp = () => {
    if (propName && propValue) {
      setFormData({
        ...formData,
        props: [...formData.props, { name: propName, value: propValue }],
      });
      setPropName('');
      setPropValue('');
    }
  };

  const handleRemoveProp = (index: number) => {
    setFormData({
      ...formData,
      props: formData.props.filter((_, i) => i !== index),
    });
  };

  const handleSaveRule = () => {
    const newRule: FilterRule = {
      id: editingRule?.id || uuid(),
      name: formData.name,
      props: formData.props,
      scope: formData.scope as ScopeType[],
      sdks: formData.sdks as SDKType[],
    };

    const listKey = filtersConfig.mode === 'WHITELIST' ? 'whitelist' : 'blacklist';
    
    if (editingRule?.id) {
      // Update existing rule
      onUpdate({
        filtersConfig: {
          ...filtersConfig,
          [listKey]: filtersConfig[listKey].map(r => 
            r.id === editingRule.id ? newRule : r
          ),
        },
      });
    } else {
      // Add new rule
      onUpdate({
        filtersConfig: {
          ...filtersConfig,
          [listKey]: [...filtersConfig[listKey], newRule],
        },
      });
    }

    closeModal();
  };

  return (
    <Box className={classes.sectionCard}>
      <Box 
        className={classes.sectionHeader}
        onClick={() => setExpanded(!expanded)}
      >
        <Group className={classes.sectionTitleGroup}>
          <Box className={classes.sectionIcon}>
            <IconFilter size={20} />
          </Box>
          <Box>
            <Text className={classes.sectionTitle}>Event Filters</Text>
            <Text className={classes.sectionDescription}>
              {SAMPLING_CONFIG_CONSTANTS.DESCRIPTIONS.FILTERS}
            </Text>
          </Box>
        </Group>
        <Group gap="sm">
          <Text className={classes.sectionBadge} c="teal" bg="teal.0">
            {activeList.length} {activeList.length === 1 ? 'rule' : 'rules'}
          </Text>
          {expanded ? <IconChevronDown size={18} /> : <IconChevronRight size={18} />}
        </Group>
      </Box>

      <Collapse in={expanded}>
        <Box className={classes.sectionContent}>
          {/* Mode Toggle */}
          <Group justify="space-between" mb="md">
            <Text size="sm" fw={600} c="dark.5">Filter Mode</Text>
            <SegmentedControl
              value={filtersConfig.mode}
              onChange={handleModeChange}
              data={[
                { label: 'Blacklist', value: 'BLACKLIST' },
                { label: 'Whitelist', value: 'WHITELIST' },
              ]}
              size="xs"
              styles={{
                root: {
                  background: 'rgba(14, 201, 194, 0.08)',
                },
              }}
            />
          </Group>

          {/* Rules List */}
          {activeList.length === 0 ? (
            <EmptyState
              icon={<IconFilter size={28} />}
              title={`No ${filtersConfig.mode.toLowerCase()} rules`}
              description={`Add rules to ${filtersConfig.mode === 'BLACKLIST' ? 'block specific events from being sent' : 'only allow specific events to be sent'}`}
              actionLabel="Add Filter Rule"
              onAction={handleAddRule}
            />
          ) : (
            <>
              {activeList.map((rule) => (
                <Box key={rule.id || rule.name} className={`${classes.ruleCard} ${classes.fadeIn}`}>
                  <Box className={classes.ruleHeader}>
                    <Text className={classes.ruleName}>{rule.name}</Text>
                    <Group className={classes.ruleActions} gap={4}>
                      <Tooltip label="Edit">
                        <ActionIcon 
                          variant="subtle" 
                          size="sm"
                          onClick={() => handleEditRule(rule)}
                        >
                          <IconEdit size={14} />
                        </ActionIcon>
                      </Tooltip>
                      <Tooltip label="Duplicate">
                        <ActionIcon 
                          variant="subtle" 
                          size="sm"
                          onClick={() => handleDuplicateRule(rule)}
                        >
                          <IconCopy size={14} />
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

                  {rule.props.length > 0 && (
                    <Box mb="xs">
                      <Text size="xs" c="dimmed" mb={4}>Properties:</Text>
                      {rule.props.map((prop, idx) => (
                        <Text key={idx} size="xs" c="dark.5" ml="sm">
                          <code>{prop.name}</code> = <code>{prop.value}</code>
                        </Text>
                      ))}
                    </Box>
                  )}

                  <Group className={classes.ruleMeta}>
                    {rule.sdks.map((sdk) => (
                      <SdkTag key={sdk} sdk={sdk} />
                    ))}
                    {rule.scope.map((scope) => (
                      <ScopeTag key={scope} scope={scope} />
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
                Add Filter Rule
              </Button>
            </>
          )}
        </Box>
      </Collapse>

      {/* Add/Edit Rule Modal */}
      <Modal
        opened={modalOpened}
        onClose={closeModal}
        title={editingRule ? 'Edit Filter Rule' : 'Add Filter Rule'}
        size="lg"
      >
        <Stack gap="md">
          <TextInput
            label="Rule Name"
            placeholder="e.g., sensitive_event"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            required
          />

          <MultiSelect
            label="SDKs"
            placeholder="Select SDKs"
            data={SDK_OPTIONS}
            value={formData.sdks}
            onChange={(sdks) => setFormData({ ...formData, sdks })}
          />

          <MultiSelect
            label="Scope"
            placeholder="Select scope"
            data={SCOPE_OPTIONS}
            value={formData.scope}
            onChange={(scope) => setFormData({ ...formData, scope })}
          />

          <Box>
            <Text size="sm" fw={500} mb="xs">Property Filters</Text>
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

            {formData.props.length > 0 && (
              <Stack gap="xs">
                {formData.props.map((prop, idx) => (
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
            <Button variant="light" onClick={closeModal}>
              Cancel
            </Button>
            <Button 
              onClick={handleSaveRule}
              disabled={!formData.name || formData.sdks.length === 0 || formData.scope.length === 0}
            >
              {editingRule ? 'Update Rule' : 'Add Rule'}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Box>
  );
}

