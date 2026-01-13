/**
 * Filters Configuration Component
 * Manages blacklist/whitelist event filters with props, scope, and SDK selection
 * 
 * Uses dynamic data from backend:
 * - GET /v1/configs/scopes-sdks for available scopes and SDKs
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
  SegmentedControl,
  MultiSelect,
  Select,
  Stack,
  Paper,
  Collapse,
  Divider,
  Loader,
} from '@mantine/core';
import {
  IconFilter,
  IconPlus,
  IconChevronDown,
  IconChevronRight,
  IconX,
} from '@tabler/icons-react';
import {
  EventFilter,
  EventPropMatch,
  SdkEnum,
  ScopeEnum,
  FilterMode,
  FiltersConfigProps,
} from '../../SamplingConfig.interface';
import {
  toSdkOptions,
  toScopeOptions,
  SDK_DISPLAY_INFO,
  SCOPE_DISPLAY_INFO,
  PROPERTY_MATCH_OPERATORS,
  PropertyMatchOperator,
  detectOperatorFromRegex,
  formatNameForDisplay,
  validateRegex,
  generateId,
  UI_CONSTANTS,
} from '../../SamplingConfig.constants';
import { useGetSdkScopesAndSdks } from '../../../../hooks/useSdkConfig';
import classes from '../../SamplingConfig.module.css';

// Extended prop match with operator for UI
interface PropMatchWithOperator extends EventPropMatch {
  operator: PropertyMatchOperator;
  rawValue: string;
}

export function FiltersConfig({ config, onChange, disabled = false }: FiltersConfigProps) {
  // Fetch dynamic options from backend
  const { data: scopesAndSdks, isLoading } = useGetSdkScopesAndSdks();

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingFilter, setEditingFilter] = useState<EventFilter | null>(null);
  const [expandedFilters, setExpandedFilters] = useState<Set<string>>(new Set());
  
  // Form state for new/editing filter
  const [filterNameOperator, setFilterNameOperator] = useState<PropertyMatchOperator>('equals');
  const [filterNameRaw, setFilterNameRaw] = useState('');
  const [filterProps, setFilterProps] = useState<PropMatchWithOperator[]>([
    { name: '', value: '', operator: 'equals', rawValue: '' }
  ]);
  const [filterScopes, setFilterScopes] = useState<ScopeEnum[]>([]);
  const [filterSdks, setFilterSdks] = useState<SdkEnum[]>([]);

  // Convert backend data to select options
  const sdkOptions = useMemo(() => {
    if (scopesAndSdks?.data?.sdks) {
      return toSdkOptions(scopesAndSdks.data.sdks);
    }
    return [];
  }, [scopesAndSdks]);

  const scopeOptions = useMemo(() => {
    if (scopesAndSdks?.data?.scope) {
      return toScopeOptions(scopesAndSdks.data.scope);
    }
    return [];
  }, [scopesAndSdks]);

  const allSdks = useMemo(() => sdkOptions.map(s => s.value), [sdkOptions]);
  const allScopes = useMemo(() => scopeOptions.map(s => s.value), [scopeOptions]);

  const resetForm = () => {
    setFilterNameOperator('equals');
    setFilterNameRaw('');
    setFilterProps([{ name: '', value: '', operator: 'equals', rawValue: '' }]);
    setFilterScopes([]);
    setFilterSdks([]);
    setEditingFilter(null);
  };

  const openAddModal = () => {
    if (disabled) return;
    resetForm();
    setIsModalOpen(true);
  };

  const openEditModal = (filter: EventFilter) => {
    if (disabled) return;
    setEditingFilter(filter);
    // Detect operator from the name regex pattern
    const detectedName = detectOperatorFromRegex(filter.name);
    setFilterNameOperator(detectedName.operator);
    setFilterNameRaw(detectedName.rawValue);
    // Convert regex patterns back to operator + rawValue by detecting the pattern
    const propsWithOperators: PropMatchWithOperator[] = filter.props.length > 0 
      ? filter.props.map(p => {
          const detected = detectOperatorFromRegex(p.value);
          return {
            name: p.name,
            value: p.value,
            operator: detected.operator,
            rawValue: detected.rawValue,
          };
        })
      : [{ name: '', value: '', operator: 'equals', rawValue: '' }];
    setFilterProps(propsWithOperators);
    setFilterScopes(filter.scopes);
    setFilterSdks(filter.sdks);
    setIsModalOpen(true);
  };

  const handleSaveFilter = () => {
    // Convert operator+rawValue to regex pattern for backend
    const validProps: EventPropMatch[] = filterProps
      .filter(p => p.name.trim() && p.rawValue.trim())
      .map(p => {
        const operator = PROPERTY_MATCH_OPERATORS.find(op => op.value === p.operator);
        return {
          name: p.name.trim(),
          value: operator ? operator.toRegex(p.rawValue.trim()) : p.rawValue.trim(),
        };
      });
    
    // Convert name operator + raw value to regex pattern
    const nameOperator = PROPERTY_MATCH_OPERATORS.find(op => op.value === filterNameOperator);
    const filterNameRegex = nameOperator ? nameOperator.toRegex(filterNameRaw.trim()) : filterNameRaw.trim();
    
    const newFilter: EventFilter = {
      id: editingFilter?.id || generateId(),
      name: filterNameRegex,
      props: validProps,
      scopes: filterScopes,
      sdks: filterSdks,
    };

    if (editingFilter) {
      const updatedValues = config.values.map(f => 
        f.id === editingFilter.id ? newFilter : f
      );
      onChange({ ...config, values: updatedValues });
    } else {
      onChange({ ...config, values: [...config.values, newFilter] });
    }

    setIsModalOpen(false);
    resetForm();
  };

  const handleRemoveFilter = (filterId: string) => {
    if (disabled) return;
    onChange({ 
      ...config, 
      values: config.values.filter(f => f.id !== filterId) 
    });
  };

  const handleModeChange = (mode: string) => {
    if (disabled) return;
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
    setFilterProps([...filterProps, { name: '', value: '', operator: 'equals', rawValue: '' }]);
  };

  const removePropField = (index: number) => {
    setFilterProps(filterProps.filter((_, i) => i !== index));
  };

  const updatePropField = (index: number, field: 'name' | 'rawValue' | 'operator', val: string) => {
    setFilterProps(filterProps.map((p, i) => 
      i === index ? { ...p, [field]: val } : p
    ));
  };

  const getSdkLabel = (sdk: SdkEnum) => SDK_DISPLAY_INFO[sdk]?.label || sdk;
  const getScopeColor = (scope: ScopeEnum) => SCOPE_DISPLAY_INFO[scope]?.color || '#6B7280';

  const renderFilterCard = (filter: EventFilter) => {
    const isExpanded = expandedFilters.has(filter.id || '');
    
    return (
      <Paper key={filter.id} className={classes.filterCard} withBorder p="sm" mb="xs">
        <Group justify="space-between" style={{ cursor: 'pointer' }} onClick={() => toggleFilterExpand(filter.id || '')}>
          <Group gap="sm">
            {isExpanded ? <IconChevronDown size={16} /> : <IconChevronRight size={16} />}
            <Text fw={600}>{formatNameForDisplay(filter.name)}</Text>
          </Group>
          <Group gap="xs">
            {filter.sdks.slice(0, 2).map(sdk => (
              <Badge key={sdk} size="xs" variant="outline">
                {getSdkLabel(sdk)}
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
              {filter.scopes.map(scope => (
                <Badge 
                  key={scope} 
                  size="xs" 
                  color={getScopeColor(scope)}
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
                  {getSdkLabel(sdk)}
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
            
            {!disabled && (
              <Group justify="flex-end" mt="xs">
                <Button 
                  size="xs" 
                  variant="subtle" 
                  onClick={(e) => { e.stopPropagation(); openEditModal(filter); }}
                >
                  Edit
                </Button>
                <Button 
                  size="xs" 
                  variant="subtle" 
                  color="red"
                  onClick={(e) => { e.stopPropagation(); handleRemoveFilter(filter.id || ''); }}
                >
                  Remove
                </Button>
              </Group>
            )}
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
          {!disabled && (
            <Button
              size="xs"
              leftSection={<IconPlus size={14} />}
              onClick={openAddModal}
            >
              Add Filter
            </Button>
          )}
        </Box>
        
        <Box className={classes.cardContent}>
          <Box mb="lg" p="md" style={{ backgroundColor: '#f8fafc', borderRadius: 8 }}>
            <Group mb="xs">
              <Text size="sm" fw={600}>Filter Mode:</Text>
              <SegmentedControl
                size="xs"
                value={config.mode}
                onChange={handleModeChange}
                disabled={disabled}
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
          </Box>

          {config.values.length === 0 ? (
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
                {config.mode === 'blacklist' ? '🚫 Blocked Events' : '✅ Allowed Events'} ({config.values.length})
              </Text>
              {config.values.map(f => renderFilterCard(f))}
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
        {isLoading ? (
          <Box ta="center" py="xl">
            <Loader size="sm" />
            <Text size="sm" c="dimmed" mt="sm">Loading options...</Text>
          </Box>
        ) : (
          <Stack gap="md">
            <Box>
              <Text size="sm" fw={500} mb="xs">Event Name <Text component="span" c="red">*</Text></Text>
              <Text size="xs" c="dimmed" mb="sm">
                Match events by name using the selected condition
              </Text>
              <Group wrap="nowrap" align="flex-start">
                <Select
                  placeholder="Condition"
                  value={filterNameOperator}
                  onChange={(v) => setFilterNameOperator(v as PropertyMatchOperator || 'equals')}
                  data={PROPERTY_MATCH_OPERATORS.map(op => ({ value: op.value, label: op.label }))}
                  style={{ width: 150 }}
                />
                <TextInput
                  placeholder={filterNameOperator === 'regex' ? 'Enter regex pattern, e.g., ^error_.*' : 'e.g., debug_log, payment_error'}
                  value={filterNameRaw}
                  onChange={(e) => setFilterNameRaw(e.currentTarget.value)}
                  style={{ flex: 1 }}
                  error={filterNameOperator === 'regex' ? validateRegex(filterNameRaw) : undefined}
                />
              </Group>
              {filterNameOperator === 'regex' && (
                <Text size="xs" c="dimmed" mt="xs">
                  💡 Enter a valid JavaScript regular expression pattern
                </Text>
              )}
            </Box>

            {/* Show current mode info */}
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
              <Text size="sm" fw={500} mb="xs">Property Matches (Optional)</Text>
              <Text size="xs" c="dimmed" mb="sm">
                Only filter events where these properties match the specified conditions
              </Text>
              {filterProps.map((prop, index) => (
                <Group key={index} mb="xs" align="flex-end" wrap="nowrap">
                  <TextInput
                    placeholder="Property name"
                    value={prop.name}
                    onChange={(e) => updatePropField(index, 'name', e.currentTarget.value)}
                    style={{ flex: 1 }}
                    size="sm"
                  />
                  <Select
                    placeholder="Operator"
                    value={prop.operator}
                    onChange={(v) => updatePropField(index, 'operator', v || 'equals')}
                    data={PROPERTY_MATCH_OPERATORS.map(op => ({ value: op.value, label: op.label }))}
                    style={{ width: 130 }}
                    size="sm"
                  />
                  <TextInput
                    placeholder={prop.operator === 'regex' ? 'Regex pattern' : 'Value'}
                    value={prop.rawValue}
                    onChange={(e) => updatePropField(index, 'rawValue', e.currentTarget.value)}
                    style={{ flex: 1 }}
                    size="sm"
                    error={prop.operator === 'regex' && prop.rawValue.trim() ? validateRegex(prop.rawValue) : undefined}
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
                Add Property Match
              </Button>
            </Box>

            {/* Scopes with Select All */}
            <Box>
              <Group justify="space-between" mb="xs">
                <Text size="sm" fw={500}>Scopes</Text>
                <Button 
                  size="compact-xs" 
                  variant="subtle" 
                  onClick={() => setFilterScopes(allScopes)}
                  disabled={scopeOptions.length === 0}
                >
                  Select All
                </Button>
              </Group>
              <MultiSelect
                description="Which telemetry types this filter applies to"
                placeholder="Select scopes"
                data={scopeOptions.map(s => ({ value: s.value, label: s.label }))}
                value={filterScopes}
                onChange={(v) => setFilterScopes(v as ScopeEnum[])}
                required
              />
            </Box>

            {/* SDKs with Select All */}
            <Box>
              <Group justify="space-between" mb="xs">
                <Text size="sm" fw={500}>SDKs</Text>
                <Button 
                  size="compact-xs" 
                  variant="subtle" 
                  onClick={() => setFilterSdks(allSdks)}
                  disabled={sdkOptions.length === 0}
                >
                  Select All
                </Button>
              </Group>
              <MultiSelect
                description="Which SDK platforms this filter applies to"
                placeholder="Select SDKs"
                data={sdkOptions.map(s => ({ value: s.value, label: s.label }))}
                value={filterSdks}
                onChange={(v) => setFilterSdks(v as SdkEnum[])}
                required
              />
            </Box>

            <Group justify="flex-end" mt="md">
              <Button variant="subtle" onClick={() => { setIsModalOpen(false); resetForm(); }}>
                Cancel
              </Button>
              <Button
                onClick={handleSaveFilter}
                disabled={
                  !filterNameRaw.trim() || 
                  filterScopes.length === 0 || 
                  filterSdks.length === 0 ||
                  (filterNameOperator === 'regex' && validateRegex(filterNameRaw) !== null) ||
                  filterProps.some(p => p.operator === 'regex' && p.rawValue.trim() && validateRegex(p.rawValue) !== null)
                }
              >
                {editingFilter ? 'Update Filter' : 'Add Filter'}
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>
    </>
  );
}
