import { useState, useCallback } from "react";
import {
  Alert,
  Box,
  Button,
  Select,
  TextInput,
  Table,
  Badge,
  Group,
  Text,
  ActionIcon,
  Tooltip,
  Pagination,
  Modal,
  ScrollArea,
  Stack,
} from "@mantine/core";
import { useDebouncedCallback } from "@mantine/hooks";
import {
  IconAlertCircle,
  IconPlus,
  IconUpload,
  IconSearch,
  IconEdit,
  IconTrash,
  IconCalendar,
  IconCategory,
} from "@tabler/icons-react";
import classes from "./EventCatalog.module.css";
import { EventDefinitionModal } from "./components/EventDefinitionModal";
import { CsvUploadModal } from "./components/CsvUploadModal";
import { useEventDefinitions } from "./hooks/useEventDefinitions";
import { useEventCategories } from "./hooks/useEventCategories";
import { useDeleteEventDefinition } from "./hooks/useDeleteEventDefinition";
import { EventDefinition } from "./EventCatalog.types";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import { LoaderWithMessage } from "../../components/LoaderWithMessage";

export function EventCatalog() {
  const [searchInput, setSearchInput] = useState("");
  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState<string>("");
  const [page, setPage] = useState(1);
  const debouncedSetSearch = useDebouncedCallback((value: string) => {
    setSearch(value);
    setPage(1);
  }, 300);
  const pageSize = 20;

  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editingEvent, setEditingEvent] = useState<EventDefinition | null>(
    null,
  );
  const [csvModalOpen, setCsvModalOpen] = useState(false);
  const [deleteConfirmId, setDeleteConfirmId] = useState<number | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const { data, isLoading, refetch } = useEventDefinitions({
    search,
    category: categoryFilter,
    limit: pageSize,
    offset: (page - 1) * pageSize,
  });

  const { data: categoriesData, refetch: refetchCategories } =
    useEventCategories();
  const categories = categoriesData?.data ?? [];

  const deleteMutation = useDeleteEventDefinition();

  const eventDefinitions = data?.data?.eventDefinitions ?? [];
  const totalCount = data?.data?.totalCount ?? 0;

  const handleDelete = useCallback(
    (id: number) => {
      setDeleteError(null);
      deleteMutation.mutate(id, {
        onSuccess: (result) => {
          if (result?.error) {
            setDeleteError(
              result.error.message ||
                "Failed to archive this event. Please try again.",
            );
          } else {
            setDeleteConfirmId(null);
            refetch();
          }
        },
      });
    },
    [deleteMutation, refetch],
  );

  const handleModalClose = useCallback(() => {
    setCreateModalOpen(false);
    setEditingEvent(null);
    refetch();
    refetchCategories();
  }, [refetch, refetchCategories]);

  const handleCsvClose = useCallback(() => {
    setCsvModalOpen(false);
    refetch();
    refetchCategories();
  }, [refetch, refetchCategories]);

  const formatDate = (dateStr: string) => {
    if (!dateStr) return "-";
    const d = new Date(dateStr);
    return d.toLocaleDateString("en-US", {
      month: "short",
      day: "numeric",
      year: "numeric",
    });
  };

  const renderContent = () => {
    if (isLoading) {
      return (
        <Box className={classes.loader}>
          <LoaderWithMessage loadingMessage="Loading event definitions..." />
        </Box>
      );
    }

    if (eventDefinitions.length === 0) {
      return (
        <ErrorAndEmptyState
          classes={[classes.emptyState]}
          message="No event definitions found"
          description={
            search || categoryFilter
              ? "Try adjusting your search or filter criteria"
              : "Add events manually or upload a CSV to get started"
          }
          icon={<IconCategory size={40} color="#0ec9c2" />}
        />
      );
    }

    return (
      <>
        <ScrollArea className={classes.scrollArea}>
          <Box className={classes.tableWrapper}>
            <Table className={classes.table}>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th style={{ width: "22%" }}>Event</Table.Th>
                  <Table.Th style={{ width: "32%" }}>Description</Table.Th>
                  <Table.Th style={{ width: "12%" }}>Category</Table.Th>
                  <Table.Th style={{ width: "10%", textAlign: "center" }}>
                    Attributes
                  </Table.Th>
                  <Table.Th style={{ width: "14%" }}>Created</Table.Th>
                  <Table.Th style={{ width: "10%", textAlign: "center" }}>
                    Actions
                  </Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {eventDefinitions.map((event) => (
                  <Table.Tr key={event.id}>
                    <Table.Td>
                      <Box className={classes.eventNameCell}>
                        <span className={classes.eventName}>
                          {event.eventName}
                        </span>
                        {event.displayName &&
                          event.displayName !== event.eventName && (
                            <span className={classes.displayName}>
                              {event.displayName}
                            </span>
                          )}
                      </Box>
                    </Table.Td>
                    <Table.Td>
                      <Text size="sm" lineClamp={2} c="dark.4">
                        {event.description || "-"}
                      </Text>
                    </Table.Td>
                    <Table.Td>
                      {event.category ? (
                        <Badge
                          variant="light"
                          size="sm"
                          className={classes.categoryBadge}
                        >
                          {event.category}
                        </Badge>
                      ) : (
                        <Text size="sm" c="dimmed">
                          -
                        </Text>
                      )}
                    </Table.Td>
                    <Table.Td style={{ textAlign: "center" }}>
                      <Badge
                        variant="light"
                        size="sm"
                        className={classes.attrCount}
                      >
                        {event.attributes?.length ?? 0}
                      </Badge>
                    </Table.Td>
                    <Table.Td>
                      <Group gap={4} wrap="nowrap">
                        <IconCalendar size={13} color="#9ca3af" />
                        <Text size="xs" c="dark.3">
                          {formatDate(event.createdAt)}
                        </Text>
                      </Group>
                    </Table.Td>
                    <Table.Td>
                      <Group gap="xs" justify="center">
                        <Tooltip label="Edit" withArrow>
                          <ActionIcon
                            variant="subtle"
                            size="sm"
                            className={classes.actionButton}
                            onClick={() => setEditingEvent(event)}
                          >
                            <IconEdit size={15} />
                          </ActionIcon>
                        </Tooltip>
                        <Tooltip label="Archive" withArrow>
                          <ActionIcon
                            variant="subtle"
                            color="red"
                            size="sm"
                            className={classes.deleteButton}
                            onClick={() => setDeleteConfirmId(event.id)}
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

        {totalCount > pageSize && (
          <Group justify="flex-end" className={classes.paginationContainer}>
            <Pagination
              total={Math.ceil(totalCount / pageSize)}
              value={page}
              onChange={setPage}
              size="sm"
            />
          </Group>
        )}
      </>
    );
  };

  return (
    <Box className={classes.pageContainer}>
      <Box className={classes.pageHeader}>
        <Box className={classes.headerTop}>
          <Box className={classes.titleSection}>
            <h1 className={classes.pageTitle}>Event Catalog</h1>
            <span className={classes.eventCount}>
              {totalCount} {totalCount === 1 ? "Event" : "Events"}
            </span>
          </Box>
          <Box className={classes.headerActions}>
            <Button
              leftSection={<IconUpload size={16} />}
              variant="light"
              size="sm"
              className={classes.uploadButton}
              onClick={() => setCsvModalOpen(true)}
            >
              Upload CSV
            </Button>
            <Button
              leftSection={<IconPlus size={16} />}
              size="sm"
              className={classes.createButton}
              onClick={() => setCreateModalOpen(true)}
            >
              Add Event
            </Button>
          </Box>
        </Box>
      </Box>

      <Box className={classes.controlsSection}>
        <Group className={classes.searchBarContainer}>
          <TextInput
            className={classes.searchInput}
            placeholder="Search by event name, category, or description..."
            leftSection={<IconSearch size={16} />}
            value={searchInput}
            onChange={(e) => {
              const val = e.currentTarget.value;
              setSearchInput(val);
              debouncedSetSearch(val);
            }}
            size="sm"
          />
          <Select
            className={classes.categoryFilter}
            placeholder="All categories"
            data={categories.map((c) => ({ value: c, label: c }))}
            value={categoryFilter || null}
            onChange={(val) => {
              setCategoryFilter(val || "");
              setPage(1);
            }}
            clearable
            searchable
            size="sm"
            leftSection={<IconCategory size={16} />}
            nothingFoundMessage="No categories found"
          />
        </Group>
      </Box>

      {renderContent()}

      <EventDefinitionModal
        opened={createModalOpen || editingEvent !== null}
        onClose={handleModalClose}
        eventDefinition={editingEvent}
      />

      <CsvUploadModal opened={csvModalOpen} onClose={handleCsvClose} />

      <Modal
        opened={deleteConfirmId !== null}
        onClose={() => {
          setDeleteConfirmId(null);
          setDeleteError(null);
        }}
        title="Confirm Archive"
        size="sm"
        centered
      >
        <Stack gap="md" className={classes.confirmModalBody}>
          <Text size="sm">
            Are you sure you want to archive this event definition? It will no
            longer appear in suggestions.
          </Text>
          {deleteError && (
            <Alert
              icon={<IconAlertCircle size={16} />}
              color="red"
              variant="light"
              withCloseButton
              onClose={() => setDeleteError(null)}
              styles={{
                root: { borderRadius: 8 },
                message: { fontSize: 13 },
              }}
            >
              {deleteError}
            </Alert>
          )}
          <Group
            justify="flex-end"
            className={classes.confirmModalActions}
          >
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                setDeleteConfirmId(null);
                setDeleteError(null);
              }}
            >
              Cancel
            </Button>
            <Button
              color="red"
              size="sm"
              loading={deleteMutation.isPending}
              onClick={() => deleteConfirmId && handleDelete(deleteConfirmId)}
            >
              Archive
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Box>
  );
}
