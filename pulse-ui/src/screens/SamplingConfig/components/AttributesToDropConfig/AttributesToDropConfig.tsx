/**
 * Attributes to Drop Configuration Component
 * Manages rules for dropping/removing attributes from events
 * 
 * Each rule has:
 * - values: string[] — list of attribute names to drop
 * - condition: EventFilter — when to drop them (event name, props, scopes, SDKs)
 * 
 * Structure mirrors AttributesToAddConfig but with plain string names instead of key-value pairs.
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
  IconTrashX,
  IconPlus,
  IconTrash,
  IconEdit,
  IconChevronDown,
  IconChevronRight,
  IconX,
  IconInfoCircle,
} from '@tabler/icons-react';
import {
  AttributeToDrop,
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

interface AttributesToDropConfigProps {
  attributes: AttributeToDrop[];
  onChange: (attributes: AttributeToDrop[]) => void;
  disabled?: boolean;
}

// Extended prop match with operator for UI
interface PropMatchWithOperator extends EventPropMatch {
  operator: PropertyMatchOperator;
  rawValue: string;
}

export function AttributesToDropConfig({ attributes, onChange, disabled = false }: AttributesToDropConfigProps) {
  // Fetch dynamic options from backend
  const { data: scopesAndSdks, isLoading } = useGetSdkScopesAndSdks();
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingAttribute, setEditingAttribute] = useState<AttributeToDrop | null>(null);
  const [expandedAttributes, setExpandedAttributes] = useState<Set<string>>(new Set());
  
  // Form state - Attribute names to drop (plain strings)
  const [attributeNames, setAttributeNames] = useState<string[]>(['']);
  
  // Form state - Condition
  const [conditionNameOperator, setConditionNameOperator] = useState<PropertyMatchOperator>('equals');
  const [conditionNameRaw, setConditionNameRaw] = useState('');
  const [conditionProps, setConditionProps] = useState<PropMatchWithOperator[]>([
    { name: '', value: '', operator: 'equals', rawValue: '' }
  ]);
  const [conditionScopes, setConditionScopes] = useState<ScopeEnum[]>([]);
  const [conditionSdks, setConditionSdks] = useState<SdkEnum[]>([]);

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
    setAttributeNames(['']);
    setConditionNameOperator('equals');
    setConditionNameRaw('');
    setConditionProps([{ name: '', value: '', operator: 'equals', rawValue: '' }]);
    setConditionScopes([]);
    setConditionSdks([]);
    setEditingAttribute(null);
  };

  const openAddModal = () => {
    if (disabled) return;
    resetForm();
    setIsModalOpen(true);
  };

  const openEditModal = (attr: AttributeToDrop) => {
    if (disabled) return;
    setEditingAttribute(attr);
    
    // Load attribute names
    setAttributeNames(attr.values.length > 0 ? [...attr.values] : ['']);
    
    // Load condition
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
    setConditionScopes([...attr.condition.scopes]);
    setConditionSdks([...attr.condition.sdks]);
    setIsModalOpen(true);
  };

  const handleSave = () => {
    // Filter out empty attribute names
    const validNames = attributeNames.filter(n => n.trim());
    if (validNames.length === 0) return;
    
    // Convert props to proper format
    const validProps: EventPropMatch[] = conditionProps
      .filter(p => p.name.trim() && p.rawValue.trim())
      .map(p => {
        const operator = PROPERTY_MATCH_OPERATORS.find(op => op.value === p.operator);
        return {
          name: p.name.trim(),
          value: operator ? operator.toRegex(p.rawValue.trim()) : p.rawValue.trim(),
        };
      });
    
    // Convert condition name operator + raw value to regex pattern
    const nameOperator = PROPERTY_MATCH_OPERATORS.find(op => op.value === conditionNameOperator);
    const conditionNameRegex = conditionNameRaw.trim() 
      ? (nameOperator ? nameOperator.toRegex(conditionNameRaw.trim()) : conditionNameRaw.trim())
      : '';
    
    const newAttr: AttributeToDrop = {
      id: editingAttribute?.id || generateId(),
      values: validNames.map(n => n.trim()),
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

  // Attribute names handlers
  const addAttributeNameField = () => {
    setAttributeNames([...attributeNames, '']);
  };

  const removeAttributeNameField = (index: number) => {
    if (attributeNames.length <= 1) return;
    setAttributeNames(attributeNames.filter((_, i) => i !== index));
  };

  const updateAttributeNameField = (index: number, value: string) => {
    setAttributeNames(attributeNames.map((n, i) => i === index ? value : n));
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

  const getSdkLabel = (sdk: SdkEnum) => SDK_DISPLAY_INFO[sdk]?.label || sdk;
  const getScopeColor = (scope: ScopeEnum) => SCOPE_DISPLAY_INFO[scope]?.color || '#6B7280';

  // Check if form is valid for saving
  const hasValidNames = attributeNames.some(n => n.trim());

  return (
    <>
      <Box className={classes.card}>
        <Box className={classes.cardHeader}>
          <Box className={classes.cardHeaderLeft}>
            <Box className={`${classes.cardIcon} ${classes.blocked}`}>
              <IconTrashX size={20} />
            </Box>
            <Box>
              <Text className={classes.cardTitle}>Attributes to Drop</Text>
              <Text className={classes.cardDescription}>Remove sensitive or unnecessary attributes from telemetry</Text>
            </Box>
          </Box>
          {!disabled && (
            <Button
              size="xs"
              leftSection={<IconPlus size={14} />}
              onClick={openAddModal}
              color="red"
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
            color="orange" 
            variant="light" 
            mb="lg"
            title="How it works"
          >
            <Text size="xs">
              Define which attributes to remove from telemetry data. Use this to strip sensitive data 
              (PII, tokens, etc.) or reduce payload size before export.
            </Text>
            <Text size="xs" mt="xs" c="dimmed">
              💡 <strong>Tip:</strong> Each rule specifies attribute names to drop and a condition for when to apply it.
            </Text>
          </Alert>

          {attributes.length === 0 ? (
            <Box className={classes.emptyState}>
              <IconTrashX size={32} style={{ opacity: 0.3 }} />
              <Text size="sm" c="dimmed" mt="xs">No attribute drop rules configured</Text>
              <Text size="xs" c="dimmed">All attributes will be sent with telemetry</Text>
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
                          Drop {attr.values.length} attribute{attr.values.length > 1 ? 's' : ''}
                        </Text>
                        {attr.condition.name && (
                          <Badge size="xs" color="orange" variant="light">
                            When: {formatNameForDisplay(attr.condition.name)}
                          </Badge>
                        )}
                      </Group>
                      <Group gap="xs">
                        {attr.values.slice(0, 3).map((v, idx) => (
                          <Badge key={idx} size="xs" variant="outline" color="red">
                            {v}
                          </Badge>
                        ))}
                        {attr.values.length > 3 && (
                          <Badge size="xs" variant="outline" color="gray">
                            +{attr.values.length - 3}
                          </Badge>
                        )}
                      </Group>
                    </Group>
                    
                    <Collapse in={isExpanded}>
                      <Divider my="sm" />
                      <Stack gap="xs">
                        <Box>
                          <Text size="xs" c="dimmed" mb="xs">Attributes to Drop:</Text>
                          <Group gap="xs" wrap="wrap">
                            {attr.values.map((v, idx) => (
                              <Badge key={idx} size="sm" color="red" variant="light">
                                {v}
                              </Badge>
                            ))}
                          </Group>
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
        title={editingAttribute ? 'Edit Attribute Drop Rule' : 'Add Attribute Drop Rule'}
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
            {/* Attributes to Drop Section */}
            <Box p="md" style={{ backgroundColor: '#fef2f2', borderRadius: 8, border: '1px solid #fecaca' }}>
              <Text size="sm" fw={600} c="red.7" mb="sm">Attributes to Drop</Text>
              <Text size="xs" c="dimmed" mb="md">
                These attribute keys will be removed from matching events
              </Text>
              {attributeNames.map((name, index) => (
                <Group key={index} mb="xs" align="flex-end" wrap="nowrap">
                  <TextInput
                    placeholder="e.g., user.email, auth_token, credit_card"
                    value={name}
                    onChange={(e) => updateAttributeNameField(index, e.currentTarget.value)}
                    style={{ flex: 1 }}
                    size="sm"
                  />
                  <ActionIcon
                    color="red"
                    variant="subtle"
                    onClick={() => removeAttributeNameField(index)}
                    disabled={attributeNames.length === 1}
                  >
                    <IconX size={16} />
                  </ActionIcon>
                </Group>
              ))}
              <Button
                size="xs"
                variant="subtle"
                color="red"
                leftSection={<IconPlus size={14} />}
                onClick={addAttributeNameField}
              >
                Add Another Attribute
              </Button>
            </Box>

            <Divider label="Drop these attributes when..." labelPosition="center" />

            {/* Condition Section */}
            <Box>
              <Text size="sm" fw={500} mb="xs">Event Name (Optional)</Text>
              <Text size="xs" c="dimmed" mb="sm">
                Only drop attributes from events matching this name (leave empty for all events)
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
                  placeholder={conditionNameOperator === 'regex' ? 'Enter regex pattern (leave empty for all)' : 'e.g., http.request, screen_view (leave empty for all)'}
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
                Only drop attributes when these event properties match (leave empty for all events)
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
                onClick={handleSave}
                disabled={
                  !hasValidNames || 
                  conditionScopes.length === 0 || 
                  conditionSdks.length === 0 ||
                  (conditionNameOperator === 'regex' && conditionNameRaw.trim() && validateRegex(conditionNameRaw) !== null) ||
                  conditionProps.some(p => p.operator === 'regex' && p.rawValue.trim() && validateRegex(p.rawValue) !== null)
                }
                color="red"
              >
                {editingAttribute ? 'Update Rule' : 'Add Drop Rule'}
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>
    </>
  );
}
