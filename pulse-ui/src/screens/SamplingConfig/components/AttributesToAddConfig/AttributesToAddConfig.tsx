/**
 * Attributes to Add Configuration Component
 * Manages rules for adding/enriching attributes to events
 * 
 * When an event matches the condition, the specified attribute key-value pairs
 * will be added to the telemetry data.
 * 
 * Use cases:
 * - Add environment tags
 * - Enrich events with computed metadata
 * - Add custom labels for analytics grouping
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
  MultiSelect,
  Select,
  Stack,
  Paper,
  Collapse,
  Divider,
  Alert,
  Loader,
} from '@mantine/core';
import {
  IconPlaylistAdd,
  IconPlus,
  IconTrash,
  IconEdit,
  IconChevronDown,
  IconChevronRight,
  IconX,
  IconInfoCircle,
} from '@tabler/icons-react';
import {
  AttributeToAdd,
  AttributeValue,
  EventPropMatch,
  ScopeEnum,
  SdkEnum,
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
} from '../../SamplingConfig.constants';
import { useGetSdkScopesAndSdks } from '../../../../hooks/useSdkConfig';
import classes from '../../SamplingConfig.module.css';

interface AttributesToAddConfigProps {
  attributes: AttributeToAdd[];
  onChange: (attributes: AttributeToAdd[]) => void;
  disabled?: boolean;
}

// Extended prop match with operator for UI
interface PropMatchWithOperator extends EventPropMatch {
  operator: PropertyMatchOperator;
  rawValue: string;
}

export function AttributesToAddConfig({ attributes, onChange, disabled = false }: AttributesToAddConfigProps) {
  // Fetch dynamic options from backend
  const { data: scopesAndSdks, isLoading } = useGetSdkScopesAndSdks();
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingAttribute, setEditingAttribute] = useState<AttributeToAdd | null>(null);
  const [expandedAttributes, setExpandedAttributes] = useState<Set<string>>(new Set());
  
  // Form state - Condition
  const [conditionNameOperator, setConditionNameOperator] = useState<PropertyMatchOperator>('equals');
  const [conditionNameRaw, setConditionNameRaw] = useState('');
  const [conditionProps, setConditionProps] = useState<PropMatchWithOperator[]>([
    { name: '', value: '', operator: 'equals', rawValue: '' }
  ]);
  const [conditionScopes, setConditionScopes] = useState<ScopeEnum[]>([]);
  const [conditionSdks, setConditionSdks] = useState<SdkEnum[]>([]);
  
  // Form state - Values to add
  const [valuesToAdd, setValuesToAdd] = useState<AttributeValue[]>([
    { name: '', value: '' }
  ]);

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
    setConditionNameOperator('equals');
    setConditionNameRaw('');
    setConditionProps([{ name: '', value: '', operator: 'equals', rawValue: '' }]);
    setConditionScopes([]);
    setConditionSdks([]);
    setValuesToAdd([{ name: '', value: '' }]);
    setEditingAttribute(null);
  };

  const openAddModal = () => {
    if (disabled) return;
    resetForm();
    setIsModalOpen(true);
  };

  const openEditModal = (attr: AttributeToAdd) => {
    if (disabled) return;
    setEditingAttribute(attr);
    // Detect operator from the condition name regex pattern
    const detectedName = detectOperatorFromRegex(attr.condition.name);
    setConditionNameOperator(detectedName.operator);
    setConditionNameRaw(detectedName.rawValue);
    const propsWithOperators: PropMatchWithOperator[] = attr.condition.props.length > 0 
      ? attr.condition.props.map(p => {
          const detected = detectOperatorFromRegex(p.value);
          return {
            name: p.name,
            value: p.value,
            operator: detected.operator,
            rawValue: detected.rawValue,
          };
        })
      : [{ name: '', value: '', operator: 'equals', rawValue: '' }];
    setConditionProps(propsWithOperators);
    setConditionScopes(attr.condition.scopes);
    setConditionSdks(attr.condition.sdks);
    setValuesToAdd(attr.values.length > 0 ? [...attr.values] : [{ name: '', value: '' }]);
    setIsModalOpen(true);
  };

  const handleSaveAttribute = () => {
    // Convert operator+rawValue to regex pattern for condition props
    const validProps: EventPropMatch[] = conditionProps
      .filter(p => p.name.trim() && p.rawValue.trim())
      .map(p => {
        const operator = PROPERTY_MATCH_OPERATORS.find(op => op.value === p.operator);
        return {
          name: p.name.trim(),
          value: operator ? operator.toRegex(p.rawValue.trim()) : p.rawValue.trim(),
        };
      });
    
    // Filter valid values to add
    const validValues: AttributeValue[] = valuesToAdd
      .filter(v => v.name.trim() && v.value.trim())
      .map(v => ({ name: v.name.trim(), value: v.value.trim() }));
    
    if (validValues.length === 0) {
      return; // Must have at least one value to add
    }
    
    // Convert condition name operator + raw value to regex pattern
    const nameOperator = PROPERTY_MATCH_OPERATORS.find(op => op.value === conditionNameOperator);
    const conditionNameRegex = conditionNameRaw.trim() 
      ? (nameOperator ? nameOperator.toRegex(conditionNameRaw.trim()) : conditionNameRaw.trim())
      : '';
    
    const newAttr: AttributeToAdd = {
      id: editingAttribute?.id || generateId(),
      values: validValues,
      condition: {
        name: conditionNameRegex,
        props: validProps,
        scopes: conditionScopes,
        sdks: conditionSdks,
      },
    };

    if (editingAttribute) {
      onChange(attributes.map(a => a.id === editingAttribute.id ? newAttr : a));
    } else {
      onChange([...attributes, newAttr]);
    }

    setIsModalOpen(false);
    resetForm();
  };

  const handleRemoveAttribute = (attrId: string) => {
    if (disabled) return;
    onChange(attributes.filter(a => a.id !== attrId));
  };

  const toggleAttributeExpand = (attrId: string) => {
    const newExpanded = new Set(expandedAttributes);
    if (newExpanded.has(attrId)) {
      newExpanded.delete(attrId);
    } else {
      newExpanded.add(attrId);
    }
    setExpandedAttributes(newExpanded);
  };

  // Condition props handlers
  const addConditionPropField = () => {
    setConditionProps([...conditionProps, { name: '', value: '', operator: 'equals', rawValue: '' }]);
  };

  const removeConditionPropField = (index: number) => {
    setConditionProps(conditionProps.filter((_, i) => i !== index));
  };

  const updateConditionPropField = (index: number, field: 'name' | 'rawValue' | 'operator', val: string) => {
    setConditionProps(conditionProps.map((p, i) => 
      i === index ? { ...p, [field]: val } : p
    ));
  };

  // Values to add handlers
  const addValueField = () => {
    setValuesToAdd([...valuesToAdd, { name: '', value: '' }]);
  };

  const removeValueField = (index: number) => {
    setValuesToAdd(valuesToAdd.filter((_, i) => i !== index));
  };

  const updateValueField = (index: number, field: 'name' | 'value', val: string) => {
    setValuesToAdd(valuesToAdd.map((v, i) => 
      i === index ? { ...v, [field]: val } : v
    ));
  };

  const getSdkLabel = (sdk: SdkEnum) => SDK_DISPLAY_INFO[sdk]?.label || sdk;
  const getScopeColor = (scope: ScopeEnum) => SCOPE_DISPLAY_INFO[scope]?.color || '#6B7280';

  // Check if form is valid for saving
  const hasValidValues = valuesToAdd.some(v => v.name.trim() && v.value.trim());

  return (
    <>
      <Box className={classes.card}>
        <Box className={classes.cardHeader}>
          <Box className={classes.cardHeaderLeft}>
            <Box className={`${classes.cardIcon} ${classes.critical}`}>
              <IconPlaylistAdd size={20} />
            </Box>
            <Box>
              <Text className={classes.cardTitle}>Attributes to Add</Text>
              <Text className={classes.cardDescription}>Enrich telemetry with additional metadata</Text>
            </Box>
          </Box>
          {!disabled && (
            <Button
              size="xs"
              leftSection={<IconPlus size={14} />}
              onClick={openAddModal}
              color="teal"
              variant="light"
            >
              Add Rule
            </Button>
          )}
        </Box>
        
        <Box className={classes.cardContent}>
          {/* Explanation */}
          <Alert 
            icon={<IconInfoCircle size={18} />} 
            color="teal" 
            variant="light" 
            mb="lg"
            title="Why Add Attributes?"
          >
            <Text size="xs">
              Enrich events with additional context for better analytics and debugging. 
              When an event matches the condition, the specified key-value pairs will be added.
            </Text>
            <Text size="xs" mt="xs" c="dimmed">
              💡 <strong>Example:</strong> Add "environment=production" to all events matching "app_version" starting with "3.".
            </Text>
          </Alert>

          {attributes.length === 0 ? (
            <Box className={classes.emptyState}>
              <IconPlaylistAdd size={32} style={{ opacity: 0.3 }} />
              <Text size="sm" c="dimmed" mt="xs">No attribute enrichment rules configured</Text>
              <Text size="xs" c="dimmed">Events will be sent without additional attributes</Text>
            </Box>
          ) : (
            <Stack gap="xs">
              {attributes.map(attr => {
                const isExpanded = expandedAttributes.has(attr.id || '');
                
                return (
                  <Paper key={attr.id} withBorder p="sm">
                    <Group 
                      justify="space-between" 
                      style={{ cursor: 'pointer' }} 
                      onClick={() => toggleAttributeExpand(attr.id || '')}
                    >
                      <Group gap="sm">
                        {isExpanded ? <IconChevronDown size={16} /> : <IconChevronRight size={16} />}
                        <Text fw={600}>
                          Add {attr.values.length} attribute{attr.values.length > 1 ? 's' : ''}
                        </Text>
                        <Badge size="xs" color="teal" variant="light">
                          When: {formatNameForDisplay(attr.condition.name)}
                        </Badge>
                      </Group>
                      <Group gap="xs">
                        {attr.values.slice(0, 2).map((v, idx) => (
                          <Badge key={idx} size="xs" variant="outline" color="teal">
                            {v.name}={v.value.substring(0, 15)}{v.value.length > 15 ? '...' : ''}
                          </Badge>
                        ))}
                        {attr.values.length > 2 && (
                          <Badge size="xs" variant="outline" color="gray">
                            +{attr.values.length - 2}
                          </Badge>
                        )}
                      </Group>
                    </Group>
                    
                    <Collapse in={isExpanded}>
                      <Divider my="sm" />
                      <Stack gap="xs">
                        <Box>
                          <Text size="xs" c="dimmed" mb="xs">Attributes to Add:</Text>
                          {attr.values.map((v, idx) => (
                            <Text key={idx} size="xs" ff="monospace" ml="md" c="teal.7">
                              {v.name} = "{v.value}"
                            </Text>
                          ))}
                        </Box>

                        <Divider variant="dashed" />
                        
                        <Text size="xs" fw={500} c="dimmed">Condition:</Text>
                        
                        {attr.condition.name && (
                          <Group gap="xs">
                            <Text size="xs" c="dimmed" w={80}>Event Name:</Text>
                            <Badge size="xs" variant="outline">{formatNameForDisplay(attr.condition.name)}</Badge>
                          </Group>
                        )}

                        <Group gap="xs">
                          <Text size="xs" c="dimmed" w={80}>Scopes:</Text>
                          {attr.condition.scopes.map(scope => (
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
                          <Text size="xs" c="dimmed" w={80}>SDKs:</Text>
                          {attr.condition.sdks.map(sdk => (
                            <Badge key={sdk} size="xs" variant="outline">
                              {getSdkLabel(sdk)}
                            </Badge>
                          ))}
                        </Group>
                        
                        {attr.condition.props.length > 0 && (
                          <Box>
                            <Text size="xs" c="dimmed" mb="xs">Property Matches:</Text>
                            {attr.condition.props.map((prop, idx) => (
                              <Text key={idx} size="xs" ff="monospace" ml="md">
                                {prop.name} = /{prop.value}/
                              </Text>
                            ))}
                          </Box>
                        )}
                        
                        {!disabled && (
                          <Group justify="flex-end" mt="xs">
                            <ActionIcon variant="subtle" onClick={(e) => { e.stopPropagation(); openEditModal(attr); }}>
                              <IconEdit size={16} />
                            </ActionIcon>
                            <ActionIcon variant="subtle" color="red" onClick={(e) => { e.stopPropagation(); handleRemoveAttribute(attr.id || ''); }}>
                              <IconTrash size={16} />
                            </ActionIcon>
                          </Group>
                        )}
                      </Stack>
                    </Collapse>
                  </Paper>
                );
              })}
            </Stack>
          )}
        </Box>
      </Box>

      {/* Add/Edit Modal */}
      <Modal
        opened={isModalOpen}
        onClose={() => { setIsModalOpen(false); resetForm(); }}
        title={editingAttribute ? 'Edit Attribute Enrichment Rule' : 'Add Attribute Enrichment Rule'}
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
            {/* Values to Add Section */}
            <Box p="md" style={{ backgroundColor: '#f0fdf4', borderRadius: 8, border: '1px solid #bbf7d0' }}>
              <Text size="sm" fw={600} c="teal.7" mb="sm">Attributes to Add</Text>
              <Text size="xs" c="dimmed" mb="md">
                These key-value pairs will be added to matching events
              </Text>
              {valuesToAdd.map((val, index) => (
                <Group key={index} mb="xs" align="flex-end" wrap="nowrap">
                  <TextInput
                    placeholder="Attribute key"
                    value={val.name}
                    onChange={(e) => updateValueField(index, 'name', e.currentTarget.value)}
                    style={{ flex: 1 }}
                    size="sm"
                  />
                  <Text size="sm" c="dimmed">=</Text>
                  <TextInput
                    placeholder="Attribute value"
                    value={val.value}
                    onChange={(e) => updateValueField(index, 'value', e.currentTarget.value)}
                    style={{ flex: 1 }}
                    size="sm"
                  />
                  <ActionIcon
                    color="red"
                    variant="subtle"
                    onClick={() => removeValueField(index)}
                    disabled={valuesToAdd.length === 1}
                  >
                    <IconX size={16} />
                  </ActionIcon>
                </Group>
              ))}
              <Button
                size="xs"
                variant="subtle"
                color="teal"
                leftSection={<IconPlus size={14} />}
                onClick={addValueField}
              >
                Add Another Attribute
              </Button>
            </Box>

            <Divider label="Add these attributes when..." labelPosition="center" />

            {/* Condition Section */}
            <Box>
              <Text size="sm" fw={500} mb="xs">Event Name (Optional)</Text>
              <Text size="xs" c="dimmed" mb="sm">
                Only add attributes to events matching this condition (leave empty for all events)
              </Text>
              <Group wrap="nowrap" align="flex-start">
                <Select
                  placeholder="Condition"
                  value={conditionNameOperator}
                  onChange={(v) => setConditionNameOperator(v as PropertyMatchOperator || 'equals')}
                  data={PROPERTY_MATCH_OPERATORS.map(op => ({ value: op.value, label: op.label }))}
                  style={{ width: 150 }}
                />
                <TextInput
                  placeholder={conditionNameOperator === 'regex' ? 'Enter regex pattern (leave empty for all)' : 'e.g., screen_view, purchase (leave empty for all)'}
                  value={conditionNameRaw}
                  onChange={(e) => setConditionNameRaw(e.currentTarget.value)}
                  style={{ flex: 1 }}
                  error={conditionNameOperator === 'regex' && conditionNameRaw.trim() ? validateRegex(conditionNameRaw) : undefined}
                />
              </Group>
              {conditionNameOperator === 'regex' && (
                <Text size="xs" c="dimmed" mt="xs">
                  💡 Enter a valid JavaScript regular expression pattern
                </Text>
              )}
            </Box>

            <Box>
              <Text size="sm" fw={500} mb="xs">Property Matches (Optional)</Text>
              <Text size="xs" c="dimmed" mb="sm">
                Only add attributes when these properties match
              </Text>
              {conditionProps.map((prop, index) => (
                <Group key={index} mb="xs" align="flex-end" wrap="nowrap">
                  <TextInput
                    placeholder="Property name"
                    value={prop.name}
                    onChange={(e) => updateConditionPropField(index, 'name', e.currentTarget.value)}
                    style={{ flex: 1 }}
                    size="sm"
                  />
                  <Select
                    placeholder="Operator"
                    value={prop.operator}
                    onChange={(v) => updateConditionPropField(index, 'operator', v || 'equals')}
                    data={PROPERTY_MATCH_OPERATORS.map(op => ({ value: op.value, label: op.label }))}
                    style={{ width: 130 }}
                    size="sm"
                  />
                  <TextInput
                    placeholder={prop.operator === 'regex' ? 'Regex pattern' : 'Value'}
                    value={prop.rawValue}
                    onChange={(e) => updateConditionPropField(index, 'rawValue', e.currentTarget.value)}
                    style={{ flex: 1 }}
                    size="sm"
                    error={prop.operator === 'regex' && prop.rawValue.trim() ? validateRegex(prop.rawValue) : undefined}
                  />
                  <ActionIcon
                    color="red"
                    variant="subtle"
                    onClick={() => removeConditionPropField(index)}
                    disabled={conditionProps.length === 1}
                  >
                    <IconX size={16} />
                  </ActionIcon>
                </Group>
              ))}
              <Button
                size="xs"
                variant="subtle"
                leftSection={<IconPlus size={14} />}
                onClick={addConditionPropField}
              >
                Add Condition
              </Button>
            </Box>

            {/* Scopes with Select All */}
            <Box>
              <Group justify="space-between" mb="xs">
                <Text size="sm" fw={500}>Scopes</Text>
                <Button 
                  size="compact-xs" 
                  variant="subtle" 
                  onClick={() => setConditionScopes(allScopes)}
                  disabled={scopeOptions.length === 0}
                >
                  Select All
                </Button>
              </Group>
              <MultiSelect
                description="Which telemetry types this rule applies to"
                placeholder="Select scopes"
                data={scopeOptions.map(s => ({ value: s.value, label: s.label }))}
                value={conditionScopes}
                onChange={(v) => setConditionScopes(v as ScopeEnum[])}
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
                  onClick={() => setConditionSdks(allSdks)}
                  disabled={sdkOptions.length === 0}
                >
                  Select All
                </Button>
              </Group>
              <MultiSelect
                description="Which SDK platforms this rule applies to"
                placeholder="Select SDKs"
                data={sdkOptions.map(s => ({ value: s.value, label: s.label }))}
                value={conditionSdks}
                onChange={(v) => setConditionSdks(v as SdkEnum[])}
                required
              />
            </Box>

            <Group justify="flex-end" mt="md">
              <Button variant="subtle" onClick={() => { setIsModalOpen(false); resetForm(); }}>
                Cancel
              </Button>
              <Button
                onClick={handleSaveAttribute}
                disabled={
                  !hasValidValues || 
                  conditionScopes.length === 0 || 
                  conditionSdks.length === 0 ||
                  (conditionNameOperator === 'regex' && conditionNameRaw.trim() && validateRegex(conditionNameRaw) !== null) ||
                  conditionProps.some(p => p.operator === 'regex' && p.rawValue.trim() && validateRegex(p.rawValue) !== null)
                }
                color="teal"
              >
                {editingAttribute ? 'Update Rule' : 'Add Enrichment Rule'}
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>
    </>
  );
}

