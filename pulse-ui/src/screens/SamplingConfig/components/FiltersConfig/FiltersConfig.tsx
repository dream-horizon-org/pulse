/**
 * Filters Configuration Component
 * Manages blacklist/whitelist event filters with props, scope, and SDK selection
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
  SegmentedControl,
  MultiSelect,
  Stack,
  Paper,
  Collapse,
  Divider,
} from '@mantine/core';
import {
  IconFilter,
  IconPlus,
  IconChevronDown,
  IconChevronRight,
  IconX,
} from '@tabler/icons-react';
import {
  FiltersConfig as FiltersConfigType,
  EventFilter,
  EventPropMatch,
  SdkEnum,
  ScopeEnum,
  FilterMode,
} from '../../SamplingConfig.interface';
import {
  SDK_OPTIONS,
  SCOPE_OPTIONS,
  generateId,
  UI_CONSTANTS,
} from '../../SamplingConfig.constants';
import classes from '../../SamplingConfig.module.css';

interface FiltersConfigProps {
  config: FiltersConfigType;
  onChange: (config: FiltersConfigType) => void;
}

export function FiltersConfig({ config, onChange }: FiltersConfigProps) {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingFilter, setEditingFilter] = useState<EventFilter | null>(null);
  const [expandedFilters, setExpandedFilters] = useState<Set<string>>(new Set());
  
  // Form state for new/editing filter
  const [filterName, setFilterName] = useState('');
  const [filterProps, setFilterProps] = useState<EventPropMatch[]>([{ name: '', value: '' }]);
  const [filterScopes, setFilterScopes] = useState<ScopeEnum[]>([]);
  const [filterSdks, setFilterSdks] = useState<SdkEnum[]>([]);
  const [filterListType, setFilterListType] = useState<'whitelist' | 'blacklist'>('blacklist');

  const resetForm = () => {
    setFilterName('');
    setFilterProps([{ name: '', value: '' }]);
    setFilterScopes([]);
    setFilterSdks([]);
    setFilterListType(config.mode);
    setEditingFilter(null);
  };

  // Get the active filter list based on mode
  const activeListType = config.mode;
  const activeFilters = config.mode === 'blacklist' ? config.blacklist : config.whitelist;

  const openAddModal = () => {
    resetForm();
    setFilterListType(config.mode); // Default to current mode when adding
    setIsModalOpen(true);
  };

  const openEditModal = (filter: EventFilter, listType: 'whitelist' | 'blacklist') => {
    setEditingFilter(filter);
    setFilterName(filter.name);
    setFilterProps(filter.props.length > 0 ? filter.props : [{ name: '', value: '' }]);
    setFilterScopes(filter.scope);
    setFilterSdks(filter.sdks);
    setFilterListType(listType);
    setIsModalOpen(true);
  };

  const handleSaveFilter = () => {
    const validProps = filterProps.filter(p => p.name.trim() && p.value.trim());
    const newFilter: EventFilter = {
      id: editingFilter?.id || generateId(),
      name: filterName.trim(),
      props: validProps,
      scope: filterScopes,
      sdks: filterSdks,
    };

    // Use the editing filter's list type if editing, otherwise use current mode
    const targetListType = editingFilter ? filterListType : config.mode;

    if (editingFilter) {
      // Update existing filter in its original list
      const updatedConfig = { ...config };
      if (targetListType === 'blacklist') {
        updatedConfig.blacklist = config.blacklist.map(f => 
          f.id === editingFilter.id ? newFilter : f
        );
      } else {
        updatedConfig.whitelist = config.whitelist.map(f => 
          f.id === editingFilter.id ? newFilter : f
        );
      }
      onChange(updatedConfig);
    } else {
      // Add new filter to current mode's list
      const updatedConfig = { ...config };
      if (config.mode === 'blacklist') {
        updatedConfig.blacklist = [...config.blacklist, newFilter];
      } else {
        updatedConfig.whitelist = [...config.whitelist, newFilter];
      }
      onChange(updatedConfig);
    }

    setIsModalOpen(false);
    resetForm();
  };

  const handleRemoveFilter = (filterId: string, listType: 'whitelist' | 'blacklist') => {
    const updatedConfig = { ...config };
    if (listType === 'blacklist') {
      updatedConfig.blacklist = config.blacklist.filter(f => f.id !== filterId);
    } else {
      updatedConfig.whitelist = config.whitelist.filter(f => f.id !== filterId);
    }
    onChange(updatedConfig);
  };

  const handleModeChange = (mode: string) => {
    onChange({ ...config, mode: mode as FilterMode });
  };

  const toggleFilterExpand = (filterId: string) => {
    const newExpanded = new Set(expandedFilters);
    if (newExpanded.has(filterId)) {
      newExpanded.delete(filterId);
    } else {
      newExpanded.add(filterId);
    }
    setExpandedFilters(newExpanded);
  };

  const addPropField = () => {
    setFilterProps([...filterProps, { name: '', value: '' }]);
  };

  const removePropField = (index: number) => {
    setFilterProps(filterProps.filter((_, i) => i !== index));
  };

  const updatePropField = (index: number, field: 'name' | 'value', val: string) => {
    setFilterProps(filterProps.map((p, i) => 
      i === index ? { ...p, [field]: val } : p
    ));
  };

  const renderFilterCard = (filter: EventFilter, listType: 'whitelist' | 'blacklist') => {
    const isExpanded = expandedFilters.has(filter.id || '');
    
    return (
      <Paper key={filter.id} className={classes.filterCard} withBorder p="sm" mb="xs">
        <Group justify="space-between" style={{ cursor: 'pointer' }} onClick={() => toggleFilterExpand(filter.id || '')}>
          <Group gap="sm">
            {isExpanded ? <IconChevronDown size={16} /> : <IconChevronRight size={16} />}
            <Text fw={600}>{filter.name}</Text>
            <Badge size="xs" color={listType === 'blacklist' ? 'red' : 'green'} variant="light">
              {listType}
            </Badge>
          </Group>
          <Group gap="xs">
            {filter.sdks.slice(0, 2).map(sdk => (
              <Badge key={sdk} size="xs" variant="outline">
                {SDK_OPTIONS.find(s => s.value === sdk)?.label || sdk}
              </Badge>
            ))}
            {filter.sdks.length > 2 && (
              <Badge size="xs" variant="outline" color="gray">
                +{filter.sdks.length - 2}
              </Badge>
            )}
          </Group>
        </Group>
        
        <Collapse in={isExpanded}>
          <Divider my="sm" />
          <Stack gap="xs">
            <Group gap="xs">
              <Text size="xs" c="dimmed" w={60}>Scopes:</Text>
              {filter.scope.map(scope => (
                <Badge 
                  key={scope} 
                  size="xs" 
                  color={SCOPE_OPTIONS.find(s => s.value === scope)?.color}
                  variant="light"
                >
                  {scope}
                </Badge>
              ))}
            </Group>
            
            <Group gap="xs">
              <Text size="xs" c="dimmed" w={60}>SDKs:</Text>
              {filter.sdks.map(sdk => (
                <Badge key={sdk} size="xs" variant="outline">
                  {SDK_OPTIONS.find(s => s.value === sdk)?.label || sdk}
                </Badge>
              ))}
            </Group>
            
            {filter.props.length > 0 && (
              <Box>
                <Text size="xs" c="dimmed" mb="xs">Property Matches:</Text>
                {filter.props.map((prop, idx) => (
                  <Text key={idx} size="xs" ff="monospace" ml="md">
                    {prop.name} = /{prop.value}/
                  </Text>
                ))}
              </Box>
            )}
            
            <Group justify="flex-end" mt="xs">
              <Button 
                size="xs" 
                variant="subtle" 
                onClick={(e) => { e.stopPropagation(); openEditModal(filter, listType); }}
              >
                Edit
              </Button>
              <Button 
                size="xs" 
                variant="subtle" 
                color="red"
                onClick={(e) => { e.stopPropagation(); handleRemoveFilter(filter.id || '', listType); }}
              >
                Remove
              </Button>
            </Group>
          </Stack>
        </Collapse>
      </Paper>
    );
  };

  return (
    <>
      <Box className={classes.card}>
        <Box className={classes.cardHeader}>
          <Box className={classes.cardHeaderLeft}>
            <Box className={`${classes.cardIcon} ${classes.blocked}`}>
              <IconFilter size={20} />
            </Box>
            <Box>
              <Text className={classes.cardTitle}>{UI_CONSTANTS.SECTIONS.FILTERS.TITLE}</Text>
              <Text className={classes.cardDescription}>{UI_CONSTANTS.SECTIONS.FILTERS.DESCRIPTION}</Text>
            </Box>
          </Box>
          <Button
            size="xs"
            leftSection={<IconPlus size={14} />}
            onClick={openAddModal}
          >
            Add Filter
          </Button>
        </Box>
        
        <Box className={classes.cardContent}>
          <Box mb="lg" p="md" style={{ backgroundColor: '#f8fafc', borderRadius: 8 }}>
            <Group mb="xs">
              <Text size="sm" fw={600}>Filter Mode:</Text>
              <SegmentedControl
                size="xs"
                value={config.mode}
                onChange={handleModeChange}
                data={[
                  { value: 'blacklist', label: '🚫 Blacklist' },
                  { value: 'whitelist', label: '✅ Whitelist' },
                ]}
              />
            </Group>
            <Text size="xs" c="dimmed">
              {config.mode === 'blacklist' 
                ? 'Blacklist Mode: All events are sent EXCEPT those matching the filters below.'
                : 'Whitelist Mode: ONLY events matching the filters below will be sent. All others are blocked.'}
            </Text>
            {config.mode === 'blacklist' && config.whitelist.length > 0 && (
              <Text size="xs" c="orange.6" mt="xs">
                ⚠️ You have {config.whitelist.length} whitelist filter(s) that are inactive in blacklist mode
              </Text>
            )}
            {config.mode === 'whitelist' && config.blacklist.length > 0 && (
              <Text size="xs" c="orange.6" mt="xs">
                ⚠️ You have {config.blacklist.length} blacklist filter(s) that are inactive in whitelist mode
              </Text>
            )}
          </Box>

          {activeFilters.length === 0 ? (
            <Box className={classes.emptyState}>
              <IconFilter size={32} style={{ opacity: 0.3 }} />
              <Text size="sm" c="dimmed" mt="xs">
                No {config.mode} filters configured
              </Text>
              <Text size="xs" c="dimmed">
                {config.mode === 'blacklist' 
                  ? 'Add events that should be blocked from being sent'
                  : 'Add events that should be allowed (all others will be blocked)'}
              </Text>
            </Box>
          ) : (
            <Box>
              <Text size="sm" fw={600} mb="xs" c={config.mode === 'blacklist' ? 'red.6' : 'green.6'}>
                {config.mode === 'blacklist' ? '🚫 Blocked Events' : '✅ Allowed Events'} ({activeFilters.length})
              </Text>
              {activeFilters.map(f => renderFilterCard(f, activeListType))}
            </Box>
          )}
        </Box>
      </Box>

      {/* Add/Edit Filter Modal */}
      <Modal
        opened={isModalOpen}
        onClose={() => { setIsModalOpen(false); resetForm(); }}
        title={editingFilter ? 'Edit Filter' : 'Add Filter'}
        size="lg"
        centered
      >
        <Stack gap="md">
          <TextInput
            label="Event Name Pattern"
            description="Name of the event to filter (exact match)"
            placeholder="e.g., sensitive_event, debug_log"
            value={filterName}
            onChange={(e) => setFilterName(e.currentTarget.value)}
            required
          />

          {/* Show current mode info - list type is determined by the active mode */}
          <Box 
            p="sm" 
            style={{ 
              backgroundColor: config.mode === 'blacklist' ? '#fef2f2' : '#f0fdf4',
              borderRadius: 8,
              border: `1px solid ${config.mode === 'blacklist' ? '#fecaca' : '#bbf7d0'}`,
            }}
          >
            <Text size="sm" fw={500} c={config.mode === 'blacklist' ? 'red.7' : 'green.7'}>
              {config.mode === 'blacklist' 
                ? '🚫 Adding to Blacklist - This event will be blocked'
                : '✅ Adding to Whitelist - Only these events will be allowed'}
            </Text>
          </Box>

          <Box>
            <Text size="sm" fw={500} mb="xs">Property Matches (Regex)</Text>
            <Text size="xs" c="dimmed" mb="sm">
              Only filter events where these properties match the regex patterns
            </Text>
            {filterProps.map((prop, index) => (
              <Group key={index} mb="xs" align="flex-end">
                <TextInput
                  placeholder="Property name"
                  value={prop.name}
                  onChange={(e) => updatePropField(index, 'name', e.currentTarget.value)}
                  style={{ flex: 1 }}
                  size="sm"
                />
                <TextInput
                  placeholder="Regex pattern"
                  value={prop.value}
                  onChange={(e) => updatePropField(index, 'value', e.currentTarget.value)}
                  style={{ flex: 1 }}
                  size="sm"
                  leftSection={<Text size="xs" c="dimmed">/</Text>}
                  rightSection={<Text size="xs" c="dimmed">/</Text>}
                />
                <ActionIcon
                  color="red"
                  variant="subtle"
                  onClick={() => removePropField(index)}
                  disabled={filterProps.length === 1}
                >
                  <IconX size={16} />
                </ActionIcon>
              </Group>
            ))}
            <Button
              size="xs"
              variant="subtle"
              leftSection={<IconPlus size={14} />}
              onClick={addPropField}
            >
              Add Property
            </Button>
          </Box>

          <MultiSelect
            label="Scopes"
            description="Which telemetry types this filter applies to"
            placeholder="Select scopes"
            data={SCOPE_OPTIONS.map(s => ({ value: s.value, label: s.label }))}
            value={filterScopes}
            onChange={(v) => setFilterScopes(v as ScopeEnum[])}
            required
          />

          <MultiSelect
            label="SDKs"
            description="Which SDK platforms this filter applies to"
            placeholder="Select SDKs"
            data={SDK_OPTIONS.map(s => ({ value: s.value, label: s.label }))}
            value={filterSdks}
            onChange={(v) => setFilterSdks(v as SdkEnum[])}
            required
          />

          <Group justify="flex-end" mt="md">
            <Button variant="subtle" onClick={() => { setIsModalOpen(false); resetForm(); }}>
              Cancel
            </Button>
            <Button
              onClick={handleSaveFilter}
              disabled={!filterName.trim() || filterScopes.length === 0 || filterSdks.length === 0}
            >
              {editingFilter ? 'Update Filter' : 'Add Filter'}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}

