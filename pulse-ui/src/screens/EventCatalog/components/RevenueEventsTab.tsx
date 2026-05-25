import { useMemo, useState } from "react";
import {
  ActionIcon,
  Badge,
  Box,
  Button,
  Group,
  Loader,
  Modal,
  ScrollArea,
  Stack,
  Table,
  Text,
  Tooltip,
} from "@mantine/core";
import {
  IconCoin,
  IconEdit,
  IconPlus,
  IconTrash,
} from "@tabler/icons-react";
import { RevenueEventConfig } from "../RevenueEvent.types";
import { useRevenueEventConfig } from "../hooks/useRevenueEventConfig";
import { ConfigureRevenueEventModal } from "./ConfigureRevenueEventModal";
import classes from "../EventCatalog.module.css";

type RevenueEventsTabProps = {
  projectId: string;
};

export function RevenueEventsTab({ projectId }: RevenueEventsTabProps) {
  const {
    configs,
    isLoading,
    isDeleting,
    saveConfig,
    removeConfig,
  } = useRevenueEventConfig(projectId);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingConfig, setEditingConfig] = useState<RevenueEventConfig | null>(
    null,
  );
  const [deleteTarget, setDeleteTarget] = useState<RevenueEventConfig | null>(
    null,
  );
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const configuredEventNames = useMemo(
    () => configs.map((c) => c.eventName),
    [configs],
  );

  const formatDate = (iso: string) => {
    if (!iso) return "—";
    return new Date(iso).toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
    });
  };

  const openCreate = () => {
    setEditingConfig(null);
    setModalOpen(true);
  };

  const openEdit = (config: RevenueEventConfig) => {
    setEditingConfig(config);
    setModalOpen(true);
  };

  const handleDelete = async () => {
    if (!deleteTarget) {
      return;
    }
    setDeleteError(null);
    try {
      await removeConfig(deleteTarget.id);
      setDeleteTarget(null);
    } catch (err) {
      setDeleteError(
        err instanceof Error ? err.message : "Failed to remove revenue event",
      );
    }
  };

  if (isLoading) {
    return (
      <Box className={classes.revenueEmptyState}>
        <Loader size="sm" color="teal" />
        <Text size="sm" c="dimmed" mt="sm">
          Loading revenue events…
        </Text>
      </Box>
    );
  }

  return (
    <>
      {configs.length > 0 && (
        <Box className={classes.revenueTabHeader}>
          <Box>
            <Text size="sm" c="dimmed" maw={560}>
              Mark which custom events represent revenue.
            </Text>
          </Box>
          <Button
            leftSection={<IconPlus size={16} />}
            size="sm"
            className={classes.createButton}
            onClick={openCreate}
          >
            Add revenue event
          </Button>
        </Box>
      )}

      {configs.length === 0 ? (
        <Box className={classes.revenueEmptyState}>
          <div className={classes.revenueEmptyIconWrap}>
            <IconCoin size={22} stroke={1.75} />
          </div>
          <Text className={classes.revenueEmptyTitle}>
            No revenue events configured
          </Text>
          <Text className={classes.revenueEmptyDesc}>
            Map purchase events to value and currency attributes so Pulse can
            show revenue impact on signal pages.
          </Text>
          <ul className={classes.revenueEmptySteps}>
            <li>Pick a purchase or subscription event</li>
            <li>Set value attribute and currency</li>
            <li>Preview AOV and trend before confirming</li>
          </ul>
          <Button
            leftSection={<IconPlus size={16} />}
            size="sm"
            className={classes.createButton}
            onClick={openCreate}
          >
            Configure revenue event
          </Button>
        </Box>
      ) : (
        <ScrollArea className={classes.scrollArea}>
          <Box className={classes.tableWrapper}>
            <Table className={classes.table}>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th style={{ width: "24%" }}>Event</Table.Th>
                  <Table.Th style={{ width: "20%" }}>Value attribute</Table.Th>
                  <Table.Th style={{ width: "10%" }}>Currency</Table.Th>
                  <Table.Th style={{ width: "14%" }}>Window</Table.Th>
                  <Table.Th style={{ width: "14%" }}>Configured</Table.Th>
                  <Table.Th style={{ width: "10%", textAlign: "center" }}>
                    Actions
                  </Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {configs.map((config) => (
                  <Table.Tr key={config.id}>
                    <Table.Td>
                      <span className={classes.eventName}>{config.eventName}</span>
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm">{config.valueAttribute}</Text>
                    </Table.Td>
                    <Table.Td>
                      {config.currencyAttribute ? (
                        <Text size="sm">{config.currencyAttribute}</Text>
                      ) : (
                        <Badge variant="light" size="sm">
                          {config.currency}
                        </Badge>
                      )}
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm">{config.conversionWindowHours}h</Text>
                    </Table.Td>
                    <Table.Td>
                      <Text size="xs" c="dark.3">
                        {formatDate(config.configuredAt)}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      <Group gap="xs" justify="center">
                        <Tooltip label="Edit" withArrow>
                          <ActionIcon
                            variant="subtle"
                            size="sm"
                            className={classes.actionButton}
                            onClick={() => openEdit(config)}
                          >
                            <IconEdit size={15} />
                          </ActionIcon>
                        </Tooltip>
                        <Tooltip label="Remove" withArrow>
                          <ActionIcon
                            variant="subtle"
                            color="red"
                            size="sm"
                            className={classes.deleteButton}
                            onClick={() => {
                              setDeleteError(null);
                              setDeleteTarget(config);
                            }}
                          >
                            <IconTrash size={15} />
                          </ActionIcon>
                        </Tooltip>
                      </Group>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Box>
        </ScrollArea>
      )}

      <ConfigureRevenueEventModal
        opened={modalOpen}
        onClose={() => {
          setModalOpen(false);
          setEditingConfig(null);
        }}
        onSave={saveConfig}
        editingConfig={editingConfig}
        configuredEventNames={configuredEventNames}
      />

      <Modal
        opened={deleteTarget !== null}
        onClose={() => {
          setDeleteTarget(null);
          setDeleteError(null);
        }}
        title="Remove revenue event?"
        size="sm"
        centered
      >
        <Stack gap="md">
          <Text size="sm" c="dimmed">
            Remove &quot;{deleteTarget?.eventName}&quot; from this project&apos;s
            revenue configuration?
          </Text>
          {deleteError && (
            <Text size="sm" c="red">
              {deleteError}
            </Text>
          )}
          <Group justify="flex-end">
            <Button
              variant="outline"
              onClick={() => {
                setDeleteTarget(null);
                setDeleteError(null);
              }}
            >
              Cancel
            </Button>
            <Button
              color="red"
              loading={isDeleting}
              onClick={handleDelete}
            >
              Remove
            </Button>
          </Group>
        </Stack>
      </Modal>
    </>
  );
}
