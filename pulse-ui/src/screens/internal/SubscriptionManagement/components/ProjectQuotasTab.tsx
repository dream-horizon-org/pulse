import { useState } from "react";
import {
  Box,
  Stack,
  Group,
  TextInput,
  Button,
  Table,
  Badge,
  Text,
  Modal,
  NumberInput,
  Select,
  Accordion,
  Alert,
  Loader,
  Paper,
  Title,
  Divider,
} from "@mantine/core";
import { notifications } from "@mantine/notifications";
import {
  IconSearch,
  IconHistory,
  IconRefresh,
  IconSettings,
} from "@tabler/icons-react";
import {
  useProjectLimits,
  useSetProjectLimits,
  useResetProjectLimits,
  useProjectLimitHistory,
} from "../../../../hooks/useInternalProjectLimits";
import type {
  LimitValueDto,
} from "../../../../hooks/useInternalProjectLimits";
import { useInternalTiers } from "../../../../hooks/useInternalTiers";

function LimitsTable({ limits }: { limits: Record<string, LimitValueDto> }) {
  const keys = Object.keys(limits);
  if (!keys.length)
    return (
      <Text size="sm" c="dimmed">
        No limits configured.
      </Text>
    );
  return (
    <Box style={{ overflowX: "auto", width: "100%" }}>
      <Table withTableBorder striped style={{ minWidth: 800 }}>
        <Table.Thead>
          <Table.Tr>
            <Table.Th style={{ minWidth: 180 }}>Key</Table.Th>
            <Table.Th style={{ minWidth: 140 }}>Display Name</Table.Th>
            <Table.Th style={{ minWidth: 100 }}>Window</Table.Th>
            <Table.Th style={{ minWidth: 120 }}>Value</Table.Th>
            <Table.Th style={{ minWidth: 100 }}>Overage %</Table.Th>
            <Table.Th style={{ minWidth: 120 }}>Final Threshold</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {keys.map((key) => {
            const lim = limits[key];
            return (
              <Table.Tr key={key}>
                <Table.Td>
                  <Text size="xs" ff="monospace">
                    {key}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="xs">{lim.displayName}</Text>
                </Table.Td>
                <Table.Td>
                  <Text size="xs" c="dimmed">
                    {lim.windowType}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="xs">
                    {lim.value === 0 ? (
                      <Badge size="xs" color="teal" variant="light">
                        Unlimited
                      </Badge>
                    ) : (
                      lim.value.toLocaleString()
                    )}
                  </Text>
                </Table.Td>
                <Table.Td>
                  <Text size="xs">{lim.overage}%</Text>
                </Table.Td>
                <Table.Td>
                  <Text size="xs" fw={500}>
                    {lim.finalThreshold === 0
                      ? "∞"
                      : lim.finalThreshold.toLocaleString()}
                  </Text>
                </Table.Td>
              </Table.Tr>
            );
          })}
        </Table.Tbody>
      </Table>
    </Box>
  );
}

function SetLimitsModal({
  opened,
  onClose,
  projectId,
  currentLimits,
}: {
  opened: boolean;
  onClose: () => void;
  projectId: string;
  currentLimits: Record<string, LimitValueDto>;
}) {
  const setMutation = useSetProjectLimits();
  const [limits, setLimits] = useState<
    Record<
      string,
      { windowType: string; dataType: string; value: number; overage: number }
    >
  >(() =>
    Object.fromEntries(
      Object.entries(currentLimits).map(([k, v]) => [
        k,
        {
          windowType: v.windowType,
          dataType: v.dataType,
          value: v.value,
          overage: v.overage,
        },
      ]),
    ),
  );

  const updateField = (key: string, field: string, val: number | string) => {
    setLimits((prev) => ({ ...prev, [key]: { ...prev[key], [field]: val } }));
  };

  const handleSubmit = () => {
    if (!Object.keys(limits).length) {
      notifications.show({
        message: "At least one limit is required",
        color: "red",
      });
      return;
    }
    setMutation.mutate(
      { projectId, payload: { limits } },
      {
        onSuccess: () => {
          notifications.show({ message: "Custom limits saved", color: "teal" });
          onClose();
        },
        onError: (e) =>
          notifications.show({
            title: "Failed to save",
            message: e instanceof Error ? e.message : "Unknown error",
            color: "red",
          }),
      },
    );
  };

  const keys = Object.keys(limits);

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={`Set Custom Limits: ${projectId}`}
      size="xl"
    >
      <Stack gap="sm">
        {keys.map((key) => (
          <Box
            key={key}
            style={{
              border: "1px solid var(--mantine-color-default-border)",
              borderRadius: 4,
              padding: "8px 12px",
            }}
          >
            <Text size="xs" fw={600} ff="monospace" mb={4}>
              {key}
            </Text>
            <Group gap="xs" wrap="nowrap">
              <NumberInput
                label="Value (0=unlimited)"
                size="xs"
                min={0}
                value={limits[key].value}
                onChange={(v) => updateField(key, "value", Number(v))}
                style={{ flex: 1 }}
              />
              <NumberInput
                label="Overage %"
                size="xs"
                min={0}
                max={100}
                value={limits[key].overage}
                onChange={(v) => updateField(key, "overage", Number(v))}
                style={{ flex: 1 }}
              />
            </Group>
          </Box>
        ))}
        {!keys.length && (
          <Text size="sm" c="dimmed">
            No limits to edit. Fetch project limits first.
          </Text>
        )}
        <Group justify="flex-end" mt="sm">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button
            color="teal"
            loading={setMutation.isPending}
            onClick={handleSubmit}
            disabled={!keys.length}
          >
            Save Limits
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function ResetLimitsModal({
  opened,
  onClose,
  projectId,
}: {
  opened: boolean;
  onClose: () => void;
  projectId: string;
}) {
  const resetMutation = useResetProjectLimits();
  const { data: tiers } = useInternalTiers();
  const [selectedTierId, setSelectedTierId] = useState<string | null>(null);

  const tierOptions = (tiers ?? [])
    .filter((t) => t.isActive)
    .map((t) => ({ value: String(t.tierId), label: t.displayName }));

  const handleReset = () => {
    const payload = selectedTierId ? { tierId: Number(selectedTierId) } : {};
    resetMutation.mutate(
      { projectId, payload },
      {
        onSuccess: () => {
          notifications.show({
            message: "Limits reset to tier defaults",
            color: "teal",
          });
          onClose();
        },
        onError: (e) =>
          notifications.show({
            title: "Reset failed",
            message: e instanceof Error ? e.message : "Unknown error",
            color: "red",
          }),
      },
    );
  };

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={`Reset Limits: ${projectId}`}
    >
      <Stack gap="sm">
        <Text size="sm">
          Reset this project&apos;s limits to tier defaults. Leave tier blank to
          use free tier (default).
        </Text>
        <Select
          label="Target Tier (optional)"
          placeholder="Free tier (default)"
          data={tierOptions}
          value={selectedTierId}
          onChange={setSelectedTierId}
          clearable
        />
        <Group justify="flex-end" mt="sm">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button
            color="orange"
            loading={resetMutation.isPending}
            onClick={handleReset}
          >
            Reset to Defaults
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function HistoryModal({
  opened,
  onClose,
  projectId,
}: {
  opened: boolean;
  onClose: () => void;
  projectId: string;
}) {
  const { data, isLoading } = useProjectLimitHistory(projectId);

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={`Limit History: ${projectId}`}
      size="lg"
      fullScreen={false}
      styles={{ body: { maxWidth: "100%", overflowX: "auto" } }}
    >
      {isLoading && (
        <Group justify="center" p="md">
          <Loader size="sm" />
        </Group>
      )}
      {!isLoading && !data?.history?.length && (
        <Text size="sm" c="dimmed">
          No history found.
        </Text>
      )}
      {(data?.history ?? []).map((entry) => (
        <Accordion key={entry.projectUsageLimitId} mb="xs">
          <Accordion.Item value={String(entry.projectUsageLimitId)}>
            <Accordion.Control>
              <Group gap="xs">
                <Badge
                  size="xs"
                  color={entry.isActive ? "green" : "gray"}
                  variant="light"
                >
                  {entry.isActive ? "Active" : "Superseded"}
                </Badge>
                <Text size="xs" c="dimmed">
                  By {entry.createdBy} &middot;{" "}
                  {new Date(entry.createdAt).toLocaleString()}
                </Text>
              </Group>
            </Accordion.Control>
            <Accordion.Panel>
              <LimitsTable limits={entry.usageLimits} />
            </Accordion.Panel>
          </Accordion.Item>
        </Accordion>
      ))}
    </Modal>
  );
}

export function ProjectQuotasTab() {
  const [projectIdInput, setProjectIdInput] = useState("");
  const [activeProjectId, setActiveProjectId] = useState("");
  const [setLimitsOpen, setSetLimitsOpen] = useState(false);
  const [resetOpen, setResetOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);

  const {
    data: limits,
    isLoading,
    isError,
    error,
  } = useProjectLimits(activeProjectId);

  const handleFetch = () => {
    const trimmed = projectIdInput.trim();
    if (trimmed) setActiveProjectId(trimmed);
  };

  return (
    <Box>
      <Stack gap="md">
        <Paper withBorder p="md" radius="sm">
          <Stack gap="xs">
            <Text size="sm" fw={500}>Look up project limits</Text>
            <Group gap="xs" align="flex-end">
              <TextInput
                placeholder="Enter project ID..."
                value={projectIdInput}
                onChange={(e) => setProjectIdInput(e.currentTarget.value)}
                onKeyDown={(e) => e.key === "Enter" && handleFetch()}
                style={{ flex: 1, maxWidth: 380 }}
                size="sm"
              />
              <Button
                size="sm"
                color="teal"
                leftSection={<IconSearch size={14} />}
                onClick={handleFetch}
                disabled={!projectIdInput.trim()}
              >
                Fetch Limits
              </Button>
            </Group>
          </Stack>
        </Paper>

        {isLoading && activeProjectId && (
          <Group justify="center" p="xl">
            <Loader size="sm" />
          </Group>
        )}

        {isError && activeProjectId && (
          <Alert color="red" title="Failed to load limits">
            {error instanceof Error ? error.message : "Unknown error"}
          </Alert>
        )}

        {limits && !isLoading && (
          <Paper withBorder radius="sm" p="md">
            <Stack gap="sm">
              <Group justify="space-between" align="flex-start">
                <Stack gap={4}>
                  <Group gap="xs">
                    <Title order={6} ff="monospace">{activeProjectId}</Title>
                    <Badge
                      size="xs"
                      color={limits.isActive ? "green" : "gray"}
                      variant="light"
                    >
                      {limits.isActive ? "Active" : "Inactive"}
                    </Badge>
                  </Group>
                  <Text size="xs" c="dimmed">
                    Set by {limits.createdBy} &middot;{" "}
                    {new Date(limits.createdAt).toLocaleString()}
                  </Text>
                </Stack>
                <Group gap="xs">
                  <Button
                    size="xs"
                    variant="light"
                    color="blue"
                    leftSection={<IconHistory size={12} />}
                    onClick={() => setHistoryOpen(true)}
                  >
                    History
                  </Button>
                  <Button
                    size="xs"
                    variant="light"
                    color="orange"
                    leftSection={<IconRefresh size={12} />}
                    onClick={() => setResetOpen(true)}
                  >
                    Reset
                  </Button>
                  <Button
                    size="xs"
                    variant="light"
                    color="teal"
                    leftSection={<IconSettings size={12} />}
                    onClick={() => setSetLimitsOpen(true)}
                  >
                    Set Custom
                  </Button>
                </Group>
              </Group>
              <Divider />
              <LimitsTable limits={limits.usageLimits} />
            </Stack>
          </Paper>
        )}
      </Stack>

      {limits && (
        <>
          <SetLimitsModal
            opened={setLimitsOpen}
            onClose={() => setSetLimitsOpen(false)}
            projectId={activeProjectId}
            currentLimits={limits.usageLimits}
          />
          <ResetLimitsModal
            opened={resetOpen}
            onClose={() => setResetOpen(false)}
            projectId={activeProjectId}
          />
          <HistoryModal
            opened={historyOpen}
            onClose={() => setHistoryOpen(false)}
            projectId={activeProjectId}
          />
        </>
      )}
    </Box>
  );
}
