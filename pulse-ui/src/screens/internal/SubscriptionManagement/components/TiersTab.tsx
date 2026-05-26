import { useState, useEffect } from "react";
import {
  Box,
  Table,
  Button,
  Badge,
  Text,
  Group,
  Stack,
  Modal,
  TextInput,
  Switch,
  NumberInput,
  ActionIcon,
  Tooltip,
  Accordion,
  Select,
} from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { IconPlus, IconEdit, IconPlayerPlay, IconPlayerStop } from "@tabler/icons-react";
import {
  useInternalTiers,
  useCreateTier,
  useUpdateTier,
  useDeactivateTier,
  useActivateTier,
} from "../../../../hooks/useInternalTiers";
import type { TierRestResponse, CreateTierPayload, UpdateTierPayload } from "../../../../hooks/useInternalTiers";
import { TableSkeleton } from "../../../../components/Skeletons";
import { ConfirmationModal } from "../../../../components/ConfirmationModal";
import { WINDOW_TYPE_OPTIONS, DATA_TYPE_OPTIONS, REQUIRED_USAGE_LIMIT_PARAMETERS } from "../../../../constants/Constants";

type LimitMap = Record<string, { windowType: string; dataType: string; value: number; overage: number }>;

interface LimitEditorProps {
  limits: LimitMap;
  onChange: (limits: LimitMap) => void;
}

function LimitEditor({ limits, onChange }: LimitEditorProps) {
  const [newKey, setNewKey] = useState("");

  const keys = Object.keys(limits);
  const isRequired = (key: string) => REQUIRED_USAGE_LIMIT_PARAMETERS.includes(key as any);

  const updateLimit = (key: string, field: string, val: number | string) => {
    onChange({ ...limits, [key]: { ...limits[key], [field]: val } });
  };

  const addLimit = () => {
    const k = newKey.trim();
    if (!k || limits[k]) return;
    onChange({ ...limits, [k]: { windowType: "monthly", dataType: "INTEGER", value: 0, overage: 0 } });
    setNewKey("");
  };

  const removeLimit = (key: string) => {
    const next = { ...limits };
    delete next[key];
    onChange(next);
  };

  return (
    <Stack gap="xs">
      {keys.map((key) => (
        <Box
          key={key}
          style={{ border: "1px solid var(--mantine-color-default-border)", borderRadius: 4, padding: "8px 12px" }}
        >
          <Group justify="space-between" mb={4}>
            <Group gap="xs">
              <Text size="xs" fw={600} ff="monospace">{key}</Text>
              {isRequired(key) && (
                <Badge size="xs" color="red" variant="light">
                  Required
                </Badge>
              )}
            </Group>
            {!isRequired(key) && (
              <Button size="compact-xs" variant="subtle" color="red" onClick={() => removeLimit(key)}>
                Remove
              </Button>
            )}
          </Group>
          <Group gap="xs" wrap="nowrap">
            <Select
              label="Window"
              size="xs"
              value={limits[key].windowType}
              onChange={(val) => updateLimit(key, "windowType", val || "")}
              data={WINDOW_TYPE_OPTIONS}
              searchable
              clearable={false}
              style={{ flex: 1 }}
            />
            <Select
              label="Data type"
              size="xs"
              value={limits[key].dataType}
              onChange={(val) => updateLimit(key, "dataType", val || "")}
              data={DATA_TYPE_OPTIONS}
              searchable
              clearable={false}
              style={{ flex: 1 }}
            />
            {limits[key].dataType === "BOOLEAN" ? (
              <Select
                label="Value"
                size="xs"
                value={String(limits[key].value)}
                onChange={(val) => updateLimit(key, "value", val ? Number(val) : 0)}
                data={[
                  { value: "0", label: "False (0)" },
                  { value: "1", label: "True (1)" },
                ]}
                searchable={false}
                clearable={false}
                style={{ flex: 1 }}
              />
            ) : (
              <NumberInput
                label="Value"
                size="xs"
                min={0}
                value={limits[key].value}
                onChange={(v) => updateLimit(key, "value", Number(v))}
                style={{ flex: 1 }}
              />
            )}
            <NumberInput
              label="Overage %"
              size="xs"
              min={0}
              max={100}
              value={limits[key].overage}
              onChange={(v) => updateLimit(key, "overage", Number(v))}
              style={{ flex: 1 }}
            />
          </Group>
        </Box>
      ))}
      <Group gap="xs">
        <TextInput
          placeholder="limit_key (e.g. span_count)"
          size="xs"
          value={newKey}
          onChange={(e) => setNewKey(e.currentTarget.value)}
          onKeyDown={(e) => e.key === "Enter" && addLimit()}
          style={{ flex: 1 }}
        />
        <Button size="xs" variant="light" color="teal" onClick={addLimit} leftSection={<IconPlus size={12} />}>
          Add Limit
        </Button>
      </Group>
    </Stack>
  );
}

function CreateTierModal({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const createMutation = useCreateTier();
  const [name, setName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [customLimitsAllowed, setCustomLimitsAllowed] = useState(false);
  const [limits, setLimits] = useState<LimitMap>(() => ({
    max_user_sessions_per_project: { windowType: "monthly", dataType: "INTEGER", value: 0, overage: 0 },
    max_events_per_project: { windowType: "monthly", dataType: "INTEGER", value: 0, overage: 0 },
  }));

  const handleClose = () => {
    setName("");
    setDisplayName("");
    setCustomLimitsAllowed(false);
    setLimits({
      max_user_sessions_per_project: { windowType: "monthly", dataType: "INTEGER", value: 0, overage: 0 },
      max_events_per_project: { windowType: "monthly", dataType: "INTEGER", value: 0, overage: 0 },
    });
    onClose();
  };

  const handleSubmit = () => {
    if (!name.trim() || !displayName.trim()) {
      notifications.show({ message: "Name and display name are required", color: "red" });
      return;
    }

    // Validate required limits
    const missingLimits = REQUIRED_USAGE_LIMIT_PARAMETERS.filter(param => !limits[param]);
    if (missingLimits.length > 0) {
      notifications.show({
        message: `Missing required limits: ${missingLimits.join(", ")}`,
        color: "red",
      });
      return;
    }

    const payload: CreateTierPayload = {
      name: name.trim(),
      displayName: displayName.trim(),
      isCustomLimitsAllowed: customLimitsAllowed,
      usageLimitDefaults: limits,
    };
    createMutation.mutate(payload, {
      onSuccess: () => {
        notifications.show({ message: `Tier "${displayName}" created`, color: "teal" });
        handleClose();
      },
      onError: (e) =>
        notifications.show({
          title: "Create failed",
          message: e instanceof Error ? e.message : "Unknown error",
          color: "red",
        }),
    });
  };

  return (
    <Modal opened={opened} onClose={handleClose} title="Create Tier" size="lg">
      <Stack gap="sm">
        <TextInput
          label="Name"
          description="Lowercase, letters/numbers/underscores"
          placeholder="enterprise_v2"
          value={name}
          onChange={(e) => setName(e.currentTarget.value)}
          required
        />
        <TextInput
          label="Display Name"
          placeholder="Enterprise V2"
          value={displayName}
          onChange={(e) => setDisplayName(e.currentTarget.value)}
          required
        />
        <Switch
          label="Allow custom limits override"
          checked={customLimitsAllowed}
          onChange={(e) => setCustomLimitsAllowed(e.currentTarget.checked)}
        />
        <Accordion>
          <Accordion.Item value="limits">
            <Accordion.Control>
              Usage Limit Defaults ({Object.keys(limits).length} keys)
            </Accordion.Control>
            <Accordion.Panel>
              <LimitEditor limits={limits} onChange={setLimits} />
            </Accordion.Panel>
          </Accordion.Item>
        </Accordion>
        <Group justify="flex-end" mt="sm">
          <Button variant="default" onClick={handleClose}>Cancel</Button>
          <Button color="teal" loading={createMutation.isPending} onClick={handleSubmit}>
            Create
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function EditTierModal({
  tier,
  opened,
  onClose,
}: {
  tier: TierRestResponse | null;
  opened: boolean;
  onClose: () => void;
}) {
  const updateMutation = useUpdateTier();
  const [displayName, setDisplayName] = useState("");
  const [customLimitsAllowed, setCustomLimitsAllowed] = useState(false);
  const [limits, setLimits] = useState<LimitMap>({});

  useEffect(() => {
    if (!tier) return;
    setDisplayName(tier.displayName);
    setCustomLimitsAllowed(tier.isCustomLimitsAllowed);
    const mapped: LimitMap = {};
    for (const [k, v] of Object.entries(tier.usageLimitDefaults)) {
      mapped[k] = { windowType: v.windowType, dataType: v.dataType, value: v.value, overage: v.overage };
    }
    setLimits(mapped);
  }, [tier]);

  const handleClose = () => onClose();

  const handleSubmit = () => {
    if (!tier) return;

    // Validate required limits
    const missingLimits = REQUIRED_USAGE_LIMIT_PARAMETERS.filter(param => !limits[param]);
    if (missingLimits.length > 0) {
      notifications.show({
        message: `Missing required limits: ${missingLimits.join(", ")}`,
        color: "red",
      });
      return;
    }

    const payload: UpdateTierPayload = {
      displayName: displayName.trim(),
      isCustomLimitsAllowed: customLimitsAllowed,
      usageLimitDefaults: limits,
    };
    updateMutation.mutate(
      { tierId: tier.tierId, payload },
      {
        onSuccess: () => {
          notifications.show({ message: `Tier "${tier.displayName}" updated`, color: "teal" });
          handleClose();
        },
        onError: (e) =>
          notifications.show({
            title: "Update failed",
            message: e instanceof Error ? e.message : "Unknown error",
            color: "red",
          }),
      },
    );
  };

  return (
    <Modal opened={opened} onClose={handleClose} title={`Edit Tier: ${tier?.name}`} size="lg">
      <Stack gap="sm">
        <TextInput
          label="Display Name"
          value={displayName}
          onChange={(e) => setDisplayName(e.currentTarget.value)}
          required
        />
        <Switch
          label="Allow custom limits override"
          checked={customLimitsAllowed}
          onChange={(e) => setCustomLimitsAllowed(e.currentTarget.checked)}
        />
        <Accordion>
          <Accordion.Item value="limits">
            <Accordion.Control>
              Usage Limit Defaults ({Object.keys(limits).length} keys)
            </Accordion.Control>
            <Accordion.Panel>
              <LimitEditor limits={limits} onChange={setLimits} />
            </Accordion.Panel>
          </Accordion.Item>
        </Accordion>
        <Group justify="flex-end" mt="sm">
          <Button variant="default" onClick={handleClose}>Cancel</Button>
          <Button color="teal" loading={updateMutation.isPending} onClick={handleSubmit}>
            Save Changes
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

interface PendingToggle {
  tier: TierRestResponse;
  action: "activate" | "deactivate";
}

export function TiersTab() {
  const { data: tiers, isLoading } = useInternalTiers();
  const deactivateMutation = useDeactivateTier();
  const activateMutation = useActivateTier();

  const [createOpen, setCreateOpen] = useState(false);
  const [editTier, setEditTier] = useState<TierRestResponse | null>(null);
  const [pendingToggle, setPendingToggle] = useState<PendingToggle | null>(null);

  const confirmToggle = () => {
    if (!pendingToggle) return;
    const { tier, action } = pendingToggle;
    if (action === "deactivate") {
      deactivateMutation.mutate(tier.tierId, {
        onSuccess: () => {
          notifications.show({ message: `Tier "${tier.displayName}" deactivated`, color: "teal" });
          setPendingToggle(null);
        },
        onError: (e) => {
          notifications.show({
            title: "Deactivate failed",
            message: e instanceof Error ? e.message : "Unknown error",
            color: "red",
          });
          setPendingToggle(null);
        },
      });
    } else {
      activateMutation.mutate(tier.tierId, {
        onSuccess: () => {
          notifications.show({ message: `Tier "${tier.displayName}" activated`, color: "teal" });
          setPendingToggle(null);
        },
        onError: (e) => {
          notifications.show({
            title: "Activate failed",
            message: e instanceof Error ? e.message : "Unknown error",
            color: "red",
          });
          setPendingToggle(null);
        },
      });
    }
  };

  if (isLoading) return <TableSkeleton columns={5} rows={4} />;

  return (
    <>
      <Group justify="flex-end" mb="sm">
        <Button size="sm" color="teal" leftSection={<IconPlus size={14} />} onClick={() => setCreateOpen(true)}>
          Create Tier
        </Button>
      </Group>

      <Table striped highlightOnHover withTableBorder>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>ID</Table.Th>
            <Table.Th>Name</Table.Th>
            <Table.Th>Display Name</Table.Th>
            <Table.Th>Custom Limits</Table.Th>
            <Table.Th>Limits Count</Table.Th>
            <Table.Th>Status</Table.Th>
            <Table.Th style={{ width: 100 }}>Actions</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {(tiers ?? []).map((tier) => (
            <Table.Tr key={tier.tierId}>
              <Table.Td>
                <Text size="xs" ff="monospace" c="dimmed">{tier.tierId}</Text>
              </Table.Td>
              <Table.Td>
                <Text size="sm" ff="monospace">{tier.name}</Text>
              </Table.Td>
              <Table.Td>
                <Text size="sm" fw={500}>{tier.displayName}</Text>
              </Table.Td>
              <Table.Td>
                <Badge size="xs" color={tier.isCustomLimitsAllowed ? "teal" : "gray"} variant="light">
                  {tier.isCustomLimitsAllowed ? "Yes" : "No"}
                </Badge>
              </Table.Td>
              <Table.Td>
                <Text size="xs" c="dimmed">{Object.keys(tier.usageLimitDefaults).length} keys</Text>
              </Table.Td>
              <Table.Td>
                <Badge size="xs" color={tier.isActive ? "green" : "red"} variant="light">
                  {tier.isActive ? "Active" : "Inactive"}
                </Badge>
              </Table.Td>
              <Table.Td>
                <Group gap={4}>
                  <Tooltip label="Edit tier">
                    <ActionIcon size="sm" variant="subtle" color="teal" onClick={() => setEditTier(tier)}>
                      <IconEdit size={14} />
                    </ActionIcon>
                  </Tooltip>
                  {tier.isActive ? (
                    <Tooltip label="Deactivate">
                      <ActionIcon
                        size="sm"
                        variant="subtle"
                        color="red"
                        onClick={() => setPendingToggle({ tier, action: "deactivate" })}
                      >
                        <IconPlayerStop size={14} />
                      </ActionIcon>
                    </Tooltip>
                  ) : (
                    <Tooltip label="Activate">
                      <ActionIcon
                        size="sm"
                        variant="subtle"
                        color="green"
                        onClick={() => setPendingToggle({ tier, action: "activate" })}
                      >
                        <IconPlayerPlay size={14} />
                      </ActionIcon>
                    </Tooltip>
                  )}
                </Group>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
      <CreateTierModal opened={createOpen} onClose={() => setCreateOpen(false)} />
      <EditTierModal tier={editTier} opened={!!editTier} onClose={() => setEditTier(null)} />

      <ConfirmationModal
        opened={!!pendingToggle}
        onClose={() => setPendingToggle(null)}
        onConfirm={confirmToggle}
        message={
          pendingToggle?.action === "deactivate"
            ? `Deactivate tier "${pendingToggle?.tier.displayName}"? New tenants cannot be assigned to it.`
            : `Reactivate tier "${pendingToggle?.tier.displayName}"?`
        }
        confirmLabel={pendingToggle?.action === "deactivate" ? "Deactivate" : "Activate"}
        confirmColor={pendingToggle?.action === "deactivate" ? "red" : "teal"}
        severity={pendingToggle?.action === "deactivate" ? "danger" : "info"}
        loading={deactivateMutation.isPending || activateMutation.isPending}
      />
    </>
  );
}
