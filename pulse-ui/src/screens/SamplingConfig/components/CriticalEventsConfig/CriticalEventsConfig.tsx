/**
 * Critical Events Configuration Component
 * Manages events that are always sent regardless of sampling
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
  MultiSelect,
  Stack,
  Paper,
  Collapse,
  Divider,
  Alert,
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
  CriticalEventPolicy,
  EventPropMatch,
  ScopeEnum,
} from '../../SamplingConfig.interface';
import {
  SCOPE_OPTIONS,
  generateId,
  UI_CONSTANTS,
} from '../../SamplingConfig.constants';
import classes from '../../SamplingConfig.module.css';

interface CriticalEventsConfigProps {
  config: CriticalEventPolicies;
  onChange: (config: CriticalEventPolicies) => void;
}

export function CriticalEventsConfig({ config, onChange }: CriticalEventsConfigProps) {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingEvent, setEditingEvent] = useState<CriticalEventPolicy | null>(null);
  const [expandedEvents, setExpandedEvents] = useState<Set<string>>(new Set());
  
  // Form state
  const [eventName, setEventName] = useState('');
  const [eventProps, setEventProps] = useState<EventPropMatch[]>([{ name: '', value: '' }]);
  const [eventScopes, setEventScopes] = useState<ScopeEnum[]>([]);

  const resetForm = () => {
    setEventName('');
    setEventProps([{ name: '', value: '' }]);
    setEventScopes([]);
    setEditingEvent(null);
  };

  const openAddModal = () => {
    resetForm();
    setIsModalOpen(true);
  };

  const openEditModal = (event: CriticalEventPolicy) => {
    setEditingEvent(event);
    setEventName(event.name);
    setEventProps(event.props.length > 0 ? event.props : [{ name: '', value: '' }]);
    setEventScopes(event.scope);
    setIsModalOpen(true);
  };

  const handleSaveEvent = () => {
    const validProps = eventProps.filter(p => p.name.trim() && p.value.trim());
    const newEvent: CriticalEventPolicy = {
      id: editingEvent?.id || generateId(),
      name: eventName.trim(),
      props: validProps,
      scope: eventScopes,
    };

    if (editingEvent) {
      onChange({
        alwaysSend: config.alwaysSend.map(e => e.id === editingEvent.id ? newEvent : e),
      });
    } else {
      onChange({
        alwaysSend: [...config.alwaysSend, newEvent],
      });
    }

    setIsModalOpen(false);
    resetForm();
  };

  const handleRemoveEvent = (eventId: string) => {
    onChange({
      alwaysSend: config.alwaysSend.filter(e => e.id !== eventId),
    });
  };

  const toggleEventExpand = (eventId: string) => {
    const newExpanded = new Set(expandedEvents);
    if (newExpanded.has(eventId)) {
      newExpanded.delete(eventId);
    } else {
      newExpanded.add(eventId);
    }
    setExpandedEvents(newExpanded);
  };

  const addPropField = () => {
    setEventProps([...eventProps, { name: '', value: '' }]);
  };

  const removePropField = (index: number) => {
    setEventProps(eventProps.filter((_, i) => i !== index));
  };

  const updatePropField = (index: number, field: 'name' | 'value', val: string) => {
    setEventProps(eventProps.map((p, i) => 
      i === index ? { ...p, [field]: val } : p
    ));
  };

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
          <Button
            size="xs"
            leftSection={<IconPlus size={14} />}
            onClick={openAddModal}
            color="teal"
          >
            Add Critical Event
          </Button>
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
              <Text size="sm" c="dimmed" mt="xs">No critical events configured</Text>
              <Text size="xs" c="dimmed">All events will be subject to sampling rules</Text>
              <Text size="xs" c="teal.6" mt="xs">
                Recommended: Add "crash" and "payment_error" events as critical
              </Text>
            </Box>
          ) : (
            <Stack gap="xs">
              {config.alwaysSend.map(event => {
                const isExpanded = expandedEvents.has(event.id || '');
                
                return (
                  <Paper key={event.id} withBorder p="sm">
                    <Group 
                      justify="space-between" 
                      style={{ cursor: 'pointer' }} 
                      onClick={() => toggleEventExpand(event.id || '')}
                    >
                      <Group gap="sm">
                        {isExpanded ? <IconChevronDown size={16} /> : <IconChevronRight size={16} />}
                        <Text fw={600}>{event.name}</Text>
                        <Badge size="xs" color="teal" variant="light">
                          Always Send
                        </Badge>
                      </Group>
                      <Group gap="xs">
                        {event.scope.slice(0, 2).map(scope => (
                          <Badge 
                            key={scope} 
                            size="xs" 
                            color={SCOPE_OPTIONS.find(s => s.value === scope)?.color}
                            variant="light"
                          >
                            {scope}
                          </Badge>
                        ))}
                        {event.scope.length > 2 && (
                          <Badge size="xs" variant="light" color="gray">
                            +{event.scope.length - 2}
                          </Badge>
                        )}
                      </Group>
                    </Group>
                    
                    <Collapse in={isExpanded}>
                      <Divider my="sm" />
                      <Stack gap="xs">
                        <Group gap="xs">
                          <Text size="xs" c="dimmed" w={60}>Scopes:</Text>
                          {event.scope.map(scope => (
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
                        
                        {event.props.length > 0 && (
                          <Box>
                            <Text size="xs" c="dimmed" mb="xs">Property Matches:</Text>
                            {event.props.map((prop, idx) => (
                              <Text key={idx} size="xs" ff="monospace" ml="md">
                                {prop.name} = /{prop.value}/
                              </Text>
                            ))}
                          </Box>
                        )}
                        
                        <Group justify="flex-end" mt="xs">
                          <ActionIcon variant="subtle" onClick={(e) => { e.stopPropagation(); openEditModal(event); }}>
                            <IconEdit size={16} />
                          </ActionIcon>
                          <ActionIcon variant="subtle" color="red" onClick={(e) => { e.stopPropagation(); handleRemoveEvent(event.id || ''); }}>
                            <IconTrash size={16} />
                          </ActionIcon>
                        </Group>
                      </Stack>
                    </Collapse>
                  </Paper>
                );
              })}
            </Stack>
          )}
        </Box>
      </Box>

      {/* Add/Edit Critical Event Modal */}
      <Modal
        opened={isModalOpen}
        onClose={() => { setIsModalOpen(false); resetForm(); }}
        title={editingEvent ? 'Edit Critical Event' : 'Add Critical Event'}
        size="lg"
        centered
      >
        <Stack gap="md">
          <TextInput
            label="Event Name"
            description="Exact name of the event that should always be sent"
            placeholder="e.g., crash, payment_error, auth_failure"
            value={eventName}
            onChange={(e) => setEventName(e.currentTarget.value)}
            required
          />

          <Box>
            <Text size="sm" fw={500} mb="xs">Property Matches (Optional)</Text>
            <Text size="xs" c="dimmed" mb="sm">
              Only mark as critical when these properties match the regex patterns
            </Text>
            {eventProps.map((prop, index) => (
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
                  disabled={eventProps.length === 1}
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
            description="Which telemetry types this critical event applies to"
            placeholder="Select scopes"
            data={SCOPE_OPTIONS.map(s => ({ value: s.value, label: s.label }))}
            value={eventScopes}
            onChange={(v) => setEventScopes(v as ScopeEnum[])}
            required
          />

          <Group justify="flex-end" mt="md">
            <Button variant="subtle" onClick={() => { setIsModalOpen(false); resetForm(); }}>
              Cancel
            </Button>
            <Button
              onClick={handleSaveEvent}
              disabled={!eventName.trim() || eventScopes.length === 0}
              color="teal"
            >
              {editingEvent ? 'Update Event' : 'Add Critical Event'}
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}

