/**
 * Attributes to Drop Configuration Component
 * Manages rules for dropping/removing attributes from events
 * 
 * Flow:
 * 1. User specifies which attributes to drop (can be multiple)
 * 2. User defines conditions for when to drop them (scopes, SDKs, optional property conditions)
 * 
 * Each attribute creates a separate rule entry in the config, but the UI groups them
 * by conditions for easier management.
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
import { showNotification } from '@mantine/notifications';
import {
  IconTrashX,
  IconPlus,
  IconTrash,
  IconEdit,
  IconChevronDown,
  IconChevronRight,
  IconX,
  IconInfoCircle,
  IconCheck,
} from '@tabler/icons-react';
import {
  EventFilter,
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
  attributes: EventFilter[];
  onChange: (attributes: EventFilter[]) => void;
  disabled?: boolean;
}

// Extended prop match with operator for UI
interface PropMatchWithOperator extends EventPropMatch {
  operator: PropertyMatchOperator;
  rawValue: string;
}

// Grouped attributes by conditions for display
interface GroupedAttributes {
  key: string;
  scopes: ScopeEnum[];
  sdks: SdkEnum[];
  props: EventPropMatch[];
  attributeNames: { id: string; name: string }[];
}

// Attribute name entry with ID for tracking existing vs new
interface AttributeNameEntry {
  id?: string; // undefined for new entries
  name: string;
}

export function AttributesToDropConfig({ attributes, onChange, disabled = false }: AttributesToDropConfigProps) {
  // Fetch dynamic options from backend
  const { data: scopesAndSdks, isLoading } = useGetSdkScopesAndSdks();
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingGroupKey, setEditingGroupKey] = useState<string | null>(null);
  const [originalGroupIds, setOriginalGroupIds] = useState<string[]>([]); // Track IDs being edited
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
  
  // Form state - Attributes to drop (with optional ID for tracking edits)
  const [attributeNames, setAttributeNames] = useState<AttributeNameEntry[]>([{ name: '' }]);
  
  // Form state - Conditions
  const [attrScopes, setAttrScopes] = useState<ScopeEnum[]>([]);
  const [attrSdks, setAttrSdks] = useState<SdkEnum[]>([]);
  const [attrProps, setAttrProps] = useState<PropMatchWithOperator[]>([
    { name: '', value: '', operator: 'equals', rawValue: '' }
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

  // Group attributes by their conditions for display
  const groupedAttributes = useMemo((): GroupedAttributes[] => {
    const groups = new Map<string, GroupedAttributes>();
    
    attributes.forEach(attr => {
      // Create a key based on conditions
      const conditionKey = JSON.stringify({
        scopes: [...attr.scopes].sort(),
        sdks: [...attr.sdks].sort(),
        props: attr.props.map(p => ({ name: p.name, value: p.value })).sort((a, b) => a.name.localeCompare(b.name)),
      });
      
      if (groups.has(conditionKey)) {
        groups.get(conditionKey)!.attributeNames.push({ id: attr.id || '', name: attr.name });
      } else {
        groups.set(conditionKey, {
          key: conditionKey,
          scopes: attr.scopes,
          sdks: attr.sdks,
          props: attr.props,
          attributeNames: [{ id: attr.id || '', name: attr.name }],
        });
      }
    });
    
    return Array.from(groups.values());
  }, [attributes]);

  // Helper to generate condition key for comparison
  const generateConditionKey = (scopes: ScopeEnum[], sdks: SdkEnum[], props: EventPropMatch[]) => {
    return JSON.stringify({
      scopes: [...scopes].sort(),
      sdks: [...sdks].sort(),
      props: props.map(p => ({ name: p.name, value: p.value })).sort((a, b) => a.name.localeCompare(b.name)),
    });
  };

  const resetForm = () => {
    setAttributeNames([{ name: '' }]);
    setAttrScopes([]);
    setAttrSdks([]);
    setAttrProps([{ name: '', value: '', operator: 'equals', rawValue: '' }]);
    setEditingGroupKey(null);
    setOriginalGroupIds([]);
  };

  const openAddModal = () => {
    if (disabled) return;
    resetForm();
    setIsModalOpen(true);
  };

  const openEditGroupModal = (group: GroupedAttributes) => {
    if (disabled) return;
    
    setEditingGroupKey(group.key);
    // Store original IDs for tracking what to update/delete
    setOriginalGroupIds(group.attributeNames.map(a => a.id));
    
    // Load all attribute names from the group
    const names: AttributeNameEntry[] = group.attributeNames.map(attr => {
      const detected = detectOperatorFromRegex(attr.name);
      return { id: attr.id, name: detected.rawValue };
    });
    setAttributeNames(names);
    
    // Load conditions
    const propsWithOperators: PropMatchWithOperator[] = group.props.length > 0 
      ? group.props.map(p => {
          const detected = detectOperatorFromRegex(p.value);
          return {
            name: p.name,
            value: p.value,
            operator: detected.operator,
            rawValue: detected.rawValue,
          };
        })
      : [{ name: '', value: '', operator: 'equals', rawValue: '' }];
    setAttrProps(propsWithOperators);
    setAttrScopes([...group.scopes]);
    setAttrSdks([...group.sdks]);
    setIsModalOpen(true);
  };

  const handleSave = () => {
    // Filter out empty attribute names
    const validEntries = attributeNames.filter(a => a.name.trim());
    if (validEntries.length === 0) return;
    
    // Convert props to proper format
    const validProps: EventPropMatch[] = attrProps
      .filter(p => p.name.trim() && p.rawValue.trim())
      .map(p => {
        const operator = PROPERTY_MATCH_OPERATORS.find(op => op.value === p.operator);
        return {
          name: p.name.trim(),
          value: operator ? operator.toRegex(p.rawValue.trim()) : p.rawValue.trim(),
        };
      });
    
    if (editingGroupKey) {
      // Editing a group - need to handle updates, additions, and deletions
      const updatedAttributes: EventFilter[] = [];
      const currentIds = new Set<string>();
      
      // Process each entry
      validEntries.forEach(entry => {
        const equalsOperator = PROPERTY_MATCH_OPERATORS.find(op => op.value === 'equals');
        const attrNameRegex = equalsOperator ? equalsOperator.toRegex(entry.name.trim()) : entry.name.trim();
        
        if (entry.id) {
          // Existing attribute - update it
          currentIds.add(entry.id);
          updatedAttributes.push({
            id: entry.id,
            name: attrNameRegex,
            props: validProps,
            scopes: attrScopes,
            sdks: attrSdks,
          });
        } else {
          // New attribute - create it
          updatedAttributes.push({
            id: generateId(),
            name: attrNameRegex,
            props: validProps,
            scopes: attrScopes,
            sdks: attrSdks,
          });
        }
      });
      
      // Find IDs that were removed
      const removedIds = new Set(originalGroupIds.filter(id => !currentIds.has(id)));
      
      // Build new attributes array: keep non-group items, remove deleted, update/add others
      const newAttributes = attributes.filter(a => {
        const attrId = a.id || '';
        // Keep if not part of original group
        if (!originalGroupIds.includes(attrId)) return true;
        // Remove if deleted
        if (removedIds.has(attrId)) return false;
        // Will be replaced by updated version
        return false;
      });
      
      // Add all updated/new attributes
      onChange([...newAttributes, ...updatedAttributes]);
    } else {
      // Adding new attributes - check if conditions match an existing group
      const newConditionKey = generateConditionKey(attrScopes, attrSdks, validProps);
      const matchingGroup = groupedAttributes.find(g => g.key === newConditionKey);
      
      const newAttrs: EventFilter[] = validEntries.map(entry => {
        const equalsOperator = PROPERTY_MATCH_OPERATORS.find(op => op.value === 'equals');
        const attrNameRegex = equalsOperator ? equalsOperator.toRegex(entry.name.trim()) : entry.name.trim();
        
        return {
          id: generateId(),
          name: attrNameRegex,
          props: validProps,
          scopes: attrScopes,
          sdks: attrSdks,
        };
      });
      
      onChange([...attributes, ...newAttrs]);
      
      // Show notification if added to existing group
      if (matchingGroup) {
        const existingCount = matchingGroup.attributeNames.length;
        const newCount = validEntries.length;
        showNotification({
          title: 'Added to existing rule',
          message: `${newCount} attribute${newCount > 1 ? 's' : ''} added to existing rule with ${existingCount} attribute${existingCount > 1 ? 's' : ''} (same conditions).`,
          color: 'blue',
          icon: <IconInfoCircle size={16} />,
          autoClose: 5000,
        });
      } else {
        showNotification({
          title: 'Rule created',
          message: `New drop rule created with ${validEntries.length} attribute${validEntries.length > 1 ? 's' : ''}.`,
          color: 'green',
          icon: <IconCheck size={16} />,
          autoClose: 3000,
        });
      }
    }

    setIsModalOpen(false);
    resetForm();
  };

  const handleRemoveAttribute = (attrId: string) => {
    if (disabled) return;
    onChange(attributes.filter(a => a.id !== attrId));
  };

  const handleRemoveGroup = (group: GroupedAttributes) => {
    if (disabled) return;
    const idsToRemove = new Set(group.attributeNames.map(a => a.id));
    onChange(attributes.filter(a => !idsToRemove.has(a.id || '')));
  };

  const toggleGroupExpand = (key: string) => {
    const newExpanded = new Set(expandedGroups);
    if (newExpanded.has(key)) {
      newExpanded.delete(key);
    } else {
      newExpanded.add(key);
    }
    setExpandedGroups(newExpanded);
  };

  // Attribute names handlers
  const addAttributeNameField = () => {
    setAttributeNames([...attributeNames, { name: '' }]);
  };

  const removeAttributeNameField = (index: number) => {
    if (attributeNames.length <= 1) return;
    setAttributeNames(attributeNames.filter((_, i) => i !== index));
  };

  const updateAttributeNameField = (index: number, value: string) => {
    setAttributeNames(attributeNames.map((a, i) => i === index ? { ...a, name: value } : a));
  };

  // Condition props handlers
  const addPropField = () => {
    setAttrProps([...attrProps, { name: '', value: '', operator: 'equals', rawValue: '' }]);
  };

  const removePropField = (index: number) => {
    setAttrProps(attrProps.filter((_, i) => i !== index));
  };

  const updatePropField = (index: number, field: 'name' | 'rawValue' | 'operator', val: string) => {
    setAttrProps(attrProps.map((p, i) => 
      i === index ? { ...p, [field]: val } : p
    ));
  };

  const getSdkLabel = (sdk: SdkEnum) => SDK_DISPLAY_INFO[sdk]?.label || sdk;
  const getScopeColor = (scope: ScopeEnum) => SCOPE_DISPLAY_INFO[scope]?.color || '#6B7280';

  // Check if form is valid for saving
  const hasValidNames = attributeNames.some(a => a.name.trim());

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
              💡 <strong>Tip:</strong> Rules with matching conditions are grouped together automatically.
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
              {groupedAttributes.map(group => {
                const isExpanded = expandedGroups.has(group.key);
                
                return (
                  <Paper key={group.key} withBorder p="sm">
                    <Group 
                      justify="space-between" 
                      style={{ cursor: 'pointer' }} 
                      onClick={() => toggleGroupExpand(group.key)}
                    >
                      <Group gap="sm">
                        {isExpanded ? <IconChevronDown size={16} /> : <IconChevronRight size={16} />}
                        <Box>
                          <Group gap="xs" wrap="wrap">
                            {group.attributeNames.slice(0, 3).map(attr => (
                              <Badge key={attr.id} size="sm" color="red" variant="light">
                                {formatNameForDisplay(attr.name)}
                              </Badge>
                            ))}
                            {group.attributeNames.length > 3 && (
                              <Badge size="sm" variant="light" color="gray">
                                +{group.attributeNames.length - 3} more
                              </Badge>
                            )}
                          </Group>
                          <Text size="xs" c="dimmed" mt={4}>
                            {group.attributeNames.length} attribute{group.attributeNames.length !== 1 ? 's' : ''} will be dropped
                          </Text>
                        </Box>
                      </Group>
                      <Group gap="xs">
                        {group.scopes.slice(0, 2).map(scope => (
                          <Badge 
                            key={scope} 
                            size="xs" 
                            color={getScopeColor(scope)}
                            variant="light"
                          >
                            {scope}
                          </Badge>
                        ))}
                        {group.scopes.length > 2 && (
                          <Badge size="xs" variant="light" color="gray">
                            +{group.scopes.length - 2}
                          </Badge>
                        )}
                      </Group>
                    </Group>
                    
                    <Collapse in={isExpanded}>
                      <Divider my="sm" />
                      <Stack gap="sm">
                        {/* Attributes being dropped */}
                        <Box>
                          <Text size="xs" c="dimmed" fw={500} mb="xs">Attributes to Drop:</Text>
                          <Group gap="xs" wrap="wrap">
                            {group.attributeNames.map(attr => (
                              <Badge 
                                key={attr.id} 
                                size="sm" 
                                color="red" 
                                variant="light"
                                rightSection={!disabled ? (
                                  <ActionIcon 
                                    size="xs" 
                                    color="red" 
                                    variant="transparent"
                                    onClick={(e) => { e.stopPropagation(); handleRemoveAttribute(attr.id); }}
                                  >
                                    <IconX size={12} />
                                  </ActionIcon>
                                ) : undefined}
                              >
                                {formatNameForDisplay(attr.name)}
                              </Badge>
                            ))}
                          </Group>
                        </Box>

                        <Divider variant="dashed" />

                        {/* Conditions */}
                        <Text size="xs" c="dimmed" fw={500}>Applies to events from:</Text>
                        
                        <Group gap="xs">
                          <Text size="xs" c="dimmed" w={80}>Scopes:</Text>
                          {group.scopes.map(scope => (
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
                          {group.sdks.map(sdk => (
                            <Badge key={sdk} size="xs" variant="outline">
                              {getSdkLabel(sdk)}
                            </Badge>
                          ))}
                        </Group>
                        
                        {group.props.length > 0 && (
                          <Box>
                            <Text size="xs" c="dimmed" fw={500} mb="xs">When Properties Match:</Text>
                            {group.props.map((prop, idx) => {
                              const detected = detectOperatorFromRegex(prop.value);
                              return (
                                <Text key={idx} size="xs" ff="monospace" ml="md">
                                  {prop.name} {detected.operator === 'equals' ? '=' : `(${detected.operator})`} "{detected.rawValue}"
                                </Text>
                              );
                            })}
                          </Box>
                        )}
                        
                        {!disabled && (
                          <Group justify="flex-end" mt="xs">
                            <Button 
                              size="xs" 
                              variant="subtle" 
                              leftSection={<IconEdit size={14} />}
                              onClick={(e) => { 
                                e.stopPropagation(); 
                                openEditGroupModal(group);
                              }}
                            >
                              Edit
                            </Button>
                            <Button 
                              size="xs" 
                              variant="subtle" 
                              color="red"
                              leftSection={<IconTrash size={14} />}
                              onClick={(e) => { e.stopPropagation(); handleRemoveGroup(group); }}
                            >
                              Remove All
                            </Button>
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
        title={editingGroupKey ? 'Edit Attribute Drop Rule' : 'Add Attribute Drop Rule'}
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
              {attributeNames.map((entry, index) => (
                <Group key={index} mb="xs" align="flex-end" wrap="nowrap">
                  <TextInput
                    placeholder="e.g., user.email, auth_token, credit_card"
                    value={entry.name}
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
              <Text size="sm" fw={500} mb="xs">Property Matches (Optional)</Text>
              <Text size="xs" c="dimmed" mb="sm">
                Only drop attributes when these event properties match (leave empty for all events)
              </Text>
              {attrProps.map((prop, index) => (
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
                    disabled={attrProps.length === 1}
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
                  onClick={() => setAttrScopes(allScopes)}
                  disabled={scopeOptions.length === 0}
                >
                  Select All
                </Button>
              </Group>
              <MultiSelect
                description="Which telemetry types this rule applies to"
                placeholder="Select scopes"
                data={scopeOptions.map(s => ({ value: s.value, label: s.label }))}
                value={attrScopes}
                onChange={(v) => setAttrScopes(v as ScopeEnum[])}
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
                  onClick={() => setAttrSdks(allSdks)}
                  disabled={sdkOptions.length === 0}
                >
                  Select All
                </Button>
              </Group>
              <MultiSelect
                description="Which SDK platforms this rule applies to"
                placeholder="Select SDKs"
                data={sdkOptions.map(s => ({ value: s.value, label: s.label }))}
                value={attrSdks}
                onChange={(v) => setAttrSdks(v as SdkEnum[])}
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
                  attrScopes.length === 0 || 
                  attrSdks.length === 0 ||
                  attrProps.some(p => p.operator === 'regex' && p.rawValue.trim() && validateRegex(p.rawValue) !== null)
                }
                color="red"
              >
                {editingGroupKey ? 'Update Rule' : 'Add Drop Rule'}
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>
    </>
  );
}
