/**
 * Critical Events Configuration Component
 * Manages events that are always sent regardless of sampling
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
  IconShieldCheck,
  IconPlus,
  IconTrash,
  IconEdit,
  IconChevronDown,
  IconChevronRight,
  IconX,
  IconInfoCircle,
} from '@tabler/icons-react';
import {
  CriticalEventPolicies,
  CriticalPolicyRule,
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
  UI_CONSTANTS,
} from '../../SamplingConfig.constants';
import { useGetSdkScopesAndSdks } from '../../../../hooks/useSdkConfig';
import classes from '../../SamplingConfig.module.css';

interface CriticalEventsConfigProps {
  config: CriticalEventPolicies;
  onChange: (config: CriticalEventPolicies) => void;
  disabled?: boolean;
}

// Extended prop match with operator for UI
interface PropMatchWithOperator extends EventPropMatch {
  operator: PropertyMatchOperator;
  rawValue: string;
}

export function CriticalEventsConfig({ config, onChange, disabled = false }: CriticalEventsConfigProps) {
  // Fetch dynamic options from backend
  const { data: scopesAndSdks, isLoading } = useGetSdkScopesAndSdks();
  
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingPolicy, setEditingPolicy] = useState<CriticalPolicyRule | null>(null);
  const [expandedPolicies, setExpandedPolicies] = useState<Set<string>>(new Set());
  
  // Form state
  const [policyNameOperator, setPolicyNameOperator] = useState<PropertyMatchOperator>('equals');
  const [policyNameRaw, setPolicyNameRaw] = useState('');
  const [policyProps, setPolicyProps] = useState<PropMatchWithOperator[]>([
    { name: '', value: '', operator: 'equals', rawValue: '' }
  ]);
  const [policyScopes, setPolicyScopes] = useState<ScopeEnum[]>([]);
  const [policySdks, setPolicySdks] = useState<SdkEnum[]>([]);

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
    setPolicyNameOperator('equals');
    setPolicyNameRaw('');
    setPolicyProps([{ name: '', value: '', operator: 'equals', rawValue: '' }]);
    setPolicyScopes([]);
    setPolicySdks([]);
    setEditingPolicy(null);
  };

  const openAddModal = () => {
    if (disabled) return;
    resetForm();
    setIsModalOpen(true);
  };

  const openEditModal = (policy: CriticalPolicyRule) => {
    if (disabled) return;
    setEditingPolicy(policy);
    // Detect operator from the name regex pattern
    const detectedName = detectOperatorFromRegex(policy.name);
    setPolicyNameOperator(detectedName.operator);
    setPolicyNameRaw(detectedName.rawValue);
    // Convert regex patterns back to operator + rawValue by detecting the pattern
    const propsWithOperators: PropMatchWithOperator[] = policy.props.length > 0 
      ? policy.props.map(p => {
          const detected = detectOperatorFromRegex(p.value);
          return {
            name: p.name,
            value: p.value,
            operator: detected.operator,
            rawValue: detected.rawValue,
          };
        })
      : [{ name: '', value: '', operator: 'equals', rawValue: '' }];
    setPolicyProps(propsWithOperators);
    setPolicyScopes(policy.scopes);
    setPolicySdks(policy.sdks);
    setIsModalOpen(true);
  };

  const handleSavePolicy = () => {
    // Convert operator+rawValue to regex pattern
    const validProps: EventPropMatch[] = policyProps
      .filter(p => p.name.trim() && p.rawValue.trim())
      .map(p => {
        const operator = PROPERTY_MATCH_OPERATORS.find(op => op.value === p.operator);
        return {
          name: p.name.trim(),
          value: operator ? operator.toRegex(p.rawValue.trim()) : p.rawValue.trim(),
        };
      });
    
    // Convert name operator + raw value to regex pattern
    const nameOperator = PROPERTY_MATCH_OPERATORS.find(op => op.value === policyNameOperator);
    const policyNameRegex = nameOperator ? nameOperator.toRegex(policyNameRaw.trim()) : policyNameRaw.trim();
    
    const newPolicy: CriticalPolicyRule = {
      id: editingPolicy?.id || generateId(),
      name: policyNameRegex,
      props: validProps,
      scopes: policyScopes,
      sdks: policySdks,
    };

    if (editingPolicy) {
      onChange({
        alwaysSend: config.alwaysSend.map(p => p.id === editingPolicy.id ? newPolicy : p),
      });
    } else {
      onChange({
        alwaysSend: [...config.alwaysSend, newPolicy],
      });
    }

    setIsModalOpen(false);
    resetForm();
  };

  const handleRemovePolicy = (policyId: string) => {
    if (disabled) return;
    onChange({
      alwaysSend: config.alwaysSend.filter(p => p.id !== policyId),
    });
  };

  const togglePolicyExpand = (policyId: string) => {
    const newExpanded = new Set(expandedPolicies);
    if (newExpanded.has(policyId)) {
      newExpanded.delete(policyId);
    } else {
      newExpanded.add(policyId);
    }
    setExpandedPolicies(newExpanded);
  };

  const addPropField = () => {
    setPolicyProps([...policyProps, { name: '', value: '', operator: 'equals', rawValue: '' }]);
  };

  const removePropField = (index: number) => {
    setPolicyProps(policyProps.filter((_, i) => i !== index));
  };

  const updatePropField = (index: number, field: 'name' | 'rawValue' | 'operator', val: string) => {
    setPolicyProps(policyProps.map((p, i) => 
      i === index ? { ...p, [field]: val } : p
    ));
  };

  const getSdkLabel = (sdk: SdkEnum) => SDK_DISPLAY_INFO[sdk]?.label || sdk;
  const getScopeColor = (scope: ScopeEnum) => SCOPE_DISPLAY_INFO[scope]?.color || '#6B7280';

  return (
    <>
      <Box className={classes.card}>
        <Box className={classes.cardHeader}>
          <Box className={classes.cardHeaderLeft}>
            <Box className={`${classes.cardIcon} ${classes.critical}`}>
              <IconShieldCheck size={20} />
            </Box>
            <Box>
              <Text className={classes.cardTitle}>{UI_CONSTANTS.SECTIONS.CRITICAL_EVENTS.TITLE}</Text>
              <Text className={classes.cardDescription}>{UI_CONSTANTS.SECTIONS.CRITICAL_EVENTS.DESCRIPTION}</Text>
            </Box>
          </Box>
          {!disabled && (
            <Button
              size="xs"
              leftSection={<IconPlus size={14} />}
              onClick={openAddModal}
              color="teal"
            >
              Add Policy
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
            title="Why Critical Events?"
          >
            <Text size="xs">
              Critical events <strong>bypass all sampling rules</strong> and are always sent to Pulse, 
              even if the session would otherwise be dropped. Use this for events that you must never miss,
              like crashes, payment failures, or security incidents.
            </Text>
            <Text size="xs" mt="xs" c="dimmed">
              💡 <strong>Tip:</strong> Be selective — adding too many critical events defeats the purpose 
              of sampling. Focus on high-severity errors and business-critical failures.
            </Text>
          </Alert>

          {config.alwaysSend.length === 0 ? (
            <Box className={classes.emptyState}>
              <IconShieldCheck size={32} style={{ opacity: 0.3 }} />
              <Text size="sm" c="dimmed" mt="xs">No critical event policies configured</Text>
              <Text size="xs" c="dimmed">All events will be subject to sampling rules</Text>
              <Text size="xs" c="teal.6" mt="xs">
                Recommended: Add crash and error events as critical
              </Text>
            </Box>
          ) : (
            <Stack gap="xs">
              {config.alwaysSend.map(policy => {
                const isExpanded = expandedPolicies.has(policy.id || '');
                
                return (
                  <Paper key={policy.id} withBorder p="sm">
                    <Group 
                      justify="space-between" 
                      style={{ cursor: 'pointer' }} 
                      onClick={() => togglePolicyExpand(policy.id || '')}
                    >
                      <Group gap="sm">
                        {isExpanded ? <IconChevronDown size={16} /> : <IconChevronRight size={16} />}
                        <Text fw={600}>{formatNameForDisplay(policy.name)}</Text>
                        <Badge size="xs" color="teal" variant="light">
                          Always Send
                        </Badge>
                      </Group>
                      <Group gap="xs">
                        {policy.scopes.slice(0, 2).map(scope => (
                          <Badge 
                            key={scope} 
                            size="xs" 
                            color={getScopeColor(scope)}
                            variant="light"
                          >
                            {scope}
                          </Badge>
                        ))}
                        {policy.scopes.length > 2 && (
                          <Badge size="xs" variant="light" color="gray">
                            +{policy.scopes.length - 2}
                          </Badge>
                        )}
                      </Group>
                    </Group>
                    
                    <Collapse in={isExpanded}>
                      <Divider my="sm" />
                      <Stack gap="xs">
                        <Group gap="xs">
                          <Text size="xs" c="dimmed" w={60}>Scopes:</Text>
                          {policy.scopes.map(scope => (
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
                          {policy.sdks.map(sdk => (
                            <Badge key={sdk} size="xs" variant="outline">
                              {getSdkLabel(sdk)}
                            </Badge>
                          ))}
                        </Group>
                        
                        {policy.props.length > 0 && (
                          <Box>
                            <Text size="xs" c="dimmed" mb="xs">Property Matches:</Text>
                            {policy.props.map((prop, idx) => (
                              <Text key={idx} size="xs" ff="monospace" ml="md">
                                {prop.name} = /{prop.value}/
                              </Text>
                            ))}
                          </Box>
                        )}
                        
                        {!disabled && (
                          <Group justify="flex-end" mt="xs">
                            <ActionIcon variant="subtle" onClick={(e) => { e.stopPropagation(); openEditModal(policy); }}>
                              <IconEdit size={16} />
                            </ActionIcon>
                            <ActionIcon variant="subtle" color="red" onClick={(e) => { e.stopPropagation(); handleRemovePolicy(policy.id || ''); }}>
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

      {/* Add/Edit Critical Policy Modal */}
      <Modal
        opened={isModalOpen}
        onClose={() => { setIsModalOpen(false); resetForm(); }}
        title={editingPolicy ? 'Edit Critical Event Policy' : 'Add Critical Event Policy'}
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
              <Text size="sm" fw={500} mb="xs">Critical Event Name <Text component="span" c="red">*</Text></Text>
              <Text size="xs" c="dimmed" mb="sm">
                Match events by name using the selected condition (e.g., crash, payment_error)
              </Text>
              <Group wrap="nowrap" align="flex-start">
                <Select
                  placeholder="Condition"
                  value={policyNameOperator}
                  onChange={(v) => setPolicyNameOperator(v as PropertyMatchOperator || 'equals')}
                  data={PROPERTY_MATCH_OPERATORS.map(op => ({ value: op.value, label: op.label }))}
                  style={{ width: 150 }}
                />
                <TextInput
                  placeholder={policyNameOperator === 'regex' ? 'Enter regex pattern, e.g., ^crash_.*' : 'e.g., crash, payment_error, fatal_'}
                  value={policyNameRaw}
                  onChange={(e) => setPolicyNameRaw(e.currentTarget.value)}
                  style={{ flex: 1 }}
                  error={policyNameOperator === 'regex' ? validateRegex(policyNameRaw) : undefined}
                />
              </Group>
              {policyNameOperator === 'regex' && (
                <Text size="xs" c="dimmed" mt="xs">
                  💡 Enter a valid JavaScript regular expression pattern
                </Text>
              )}
            </Box>

            <Box>
              <Text size="sm" fw={500} mb="xs">Property Matches (Optional)</Text>
              <Text size="xs" c="dimmed" mb="sm">
                Only mark as critical when these properties match the specified conditions
              </Text>
              {policyProps.map((prop, index) => (
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
                    disabled={policyProps.length === 1}
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
                  onClick={() => setPolicyScopes(allScopes)}
                  disabled={scopeOptions.length === 0}
                >
                  Select All
                </Button>
              </Group>
              <MultiSelect
                description="Which telemetry types this critical event applies to"
                placeholder="Select scopes"
                data={scopeOptions.map(s => ({ value: s.value, label: s.label }))}
                value={policyScopes}
                onChange={(v) => setPolicyScopes(v as ScopeEnum[])}
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
                  onClick={() => setPolicySdks(allSdks)}
                  disabled={sdkOptions.length === 0}
                >
                  Select All
                </Button>
              </Group>
              <MultiSelect
                description="Which SDK platforms this policy applies to"
                placeholder="Select SDKs"
                data={sdkOptions.map(s => ({ value: s.value, label: s.label }))}
                value={policySdks}
                onChange={(v) => setPolicySdks(v as SdkEnum[])}
                required
              />
            </Box>

            <Group justify="flex-end" mt="md">
              <Button variant="subtle" onClick={() => { setIsModalOpen(false); resetForm(); }}>
                Cancel
              </Button>
              <Button
                onClick={handleSavePolicy}
                disabled={
                  !policyNameRaw.trim() || 
                  policyScopes.length === 0 || 
                  policySdks.length === 0 ||
                  (policyNameOperator === 'regex' && validateRegex(policyNameRaw) !== null) ||
                  policyProps.some(p => p.operator === 'regex' && p.rawValue.trim() && validateRegex(p.rawValue) !== null)
                }
                color="teal"
              >
                {editingPolicy ? 'Update Policy' : 'Add Critical Event Policy'}
              </Button>
            </Group>
          </Stack>
        )}
      </Modal>
    </>
  );
}
