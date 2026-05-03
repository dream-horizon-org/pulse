import {
  ActionIcon,
  Badge,
  Box,
  Button,
  Group,
  Loader,
  MultiSelect,
  Select,
  Text,
  TextInput,
  Tooltip,
} from "@mantine/core";
import { modals } from "@mantine/modals";
import { IconRoute, IconSearch, IconTrash } from "@tabler/icons-react";
import { useDeleteJourney } from "../../hooks/useDeleteJourney";
import { DataTable } from "mantine-datatable";
import { ChangeEvent, useCallback, useEffect, useMemo, useState } from "react";
import { generatePath, useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import { ROUTES } from "../../constants";
import { useProjectContext } from "../../contexts";
import { useGetJourneysList } from "../../hooks/useGetJourneysList";
import { FunnelType, type JourneyListItem } from "../../services/funnels.service";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import {
  DEFAULT_PAGE_SIZE,
  FILTER_CREATED_BY_LABEL,
  FILTER_STATUS_LABEL,
  FILTER_TAGS_LABEL,
  SEARCH_PLACEHOLDER,
  STATUS_OPTION_ALL
} from "./FunnelJourneyListing.constants";
import {
  CREATE_JOURNEY_ITEM,
  EMPTY_FILTERED_DESCRIPTION_JOURNEYS,
  EMPTY_TAB_JOURNEY_DESCRIPTION,
  EMPTY_TAB_JOURNEY_FILTERED_TITLE,
  EMPTY_TAB_JOURNEY_TITLE,
  JOURNEYS_LOADING,
  JOURNEYS_PAGE_TITLE,
  JOURNEYS_SUBTITLE
} from "./journeysList.constants";
import { FunnelJourneyListingPagination } from "./FunnelJourneyListingPagination";
import classes from "./FunnelJourneyListing.module.css";

const badgeRootStyle = { fontFamily: "inherit" as const };

type StatusFilterValue =
  | ""
  | "ACTIVE"
  | "IN_PROGRESS"
  | "WARN"
  | "PENDING"
  | "FAILED"
  | "COMPLETED";

export function JourneysList() {
  const navigate = useNavigate();
  const { projectId } = useProjectContext();
  const [searchStr, setSearchStr] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");

  const [statusFilter, setStatusFilter] = useState<StatusFilterValue>("");
  const [createdByFilter, setCreatedByFilter] = useState<string[]>([]);
  const [tagsFilter, setTagsFilter] = useState<string[]>([]);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  useEffect(() => {
    const t = window.setTimeout(() => setDebouncedSearch(searchStr), 300);
    return () => window.clearTimeout(t);
  }, [searchStr]);

  useEffect(() => {
    setPage(1);
  }, [debouncedSearch, statusFilter, createdByFilter, tagsFilter]);

  const queryParams = useMemo(
    () => ({
      search: debouncedSearch.trim() || null,
      status:
        statusFilter === "ACTIVE" ||
        statusFilter === "IN_PROGRESS" ||
        statusFilter === "WARN" ||
        statusFilter === "PENDING" ||
        statusFilter === "FAILED" ||
        statusFilter === "COMPLETED"
          ? statusFilter
          : null,
      createdBy: createdByFilter.length ? createdByFilter : null,
      tags: tagsFilter.length ? tagsFilter : null,
      page,
      pageSize,
    }),
    [
      debouncedSearch,
      statusFilter,
      createdByFilter,
      tagsFilter,
      page,
      pageSize,
    ],
  );

  const {
    data: apiResponse,
    isLoading,
    isFetching,
    error,
  } = useGetJourneysList({ queryParams });

  const payload = apiResponse?.data;
  const items = payload?.items ?? [];

  useEffect(() => {
    const serverPage = payload?.page;
    if (serverPage == null) return;
    setPage((p) => (serverPage !== p ? serverPage : p));
  }, [payload?.page]);

  const creatorOptions =
    payload?.filterOptions?.creators?.map((c) => ({ value: c, label: c })) ?? [];
  const tagOptions =
    payload?.filterOptions?.tags?.map((t) => ({ value: t, label: t })) ?? [];

  const goCreateJourney = () => {
    if (!projectId) return;
    navigate(generatePath(ROUTES.JOURNEYS_CREATE.path, { projectId }));
  };

  const openRow = (row: JourneyListItem) => {
    if (!projectId) return;
    navigate(
      generatePath(ROUTES.JOURNEY_DETAIL.path, {
        projectId,
        journeyId: row.id,
      }),
    );
  };

  const { mutate: deleteJourneyMutation, isPending: isDeleting } = useDeleteJourney();

  /** Per-row delete handler. Mirrors FunnelsList's. */
  const handleDeleteRow = useCallback(
    (row: JourneyListItem) => {
      modals.openConfirmModal({
        title: "Delete this journey?",
        centered: true,
        children: (
          <Text size="sm">
            The journey &quot;<Text span fw={600}>{row.name}</Text>&quot; and
            all its data will be permanently removed: tag mappings, run
            history, and computed results. This can&apos;t be undone.
          </Text>
        ),
        labels: { confirm: "Delete permanently", cancel: "Cancel" },
        confirmProps: { color: "red" },
        onConfirm: () => deleteJourneyMutation(row.id),
      });
    },
    [deleteJourneyMutation],
  );

  const onSearchChange = (e: ChangeEvent<HTMLInputElement>) => {
    setSearchStr(e.currentTarget.value);
  };

  const columns = useMemo(
    () => [
      {
        accessor: "name",
        title: "Name",
        render: (row: JourneyListItem) => (
          <Text size="sm" fw={700} lineClamp={1} ta="left">
            {row.name}
          </Text>
        ),
      },
      {
        accessor: "status",
        title: "Status",
        render: (row: JourneyListItem) => (
          <Badge
            size="sm"
            color={
              row.status === "ACTIVE"
                ? "teal"
                : row.status === "IN_PROGRESS"
                  ? "blue"
                  : row.status === "WARN"
                    ? "orange"
                    : row.status === "COMPLETED"
                      ? "violet"
                      : row.status === "FAILED"
                        ? "red"
                        : "gray"
            }
            variant="light"
            styles={{ root: badgeRootStyle }}
          >
            {row.status === "ACTIVE"
              ? "Active"
              : row.status === "IN_PROGRESS"
                ? "In Progress"
                : row.status === "WARN"
                  ? "Warning"
                  : row.status === "COMPLETED"
                    ? "Completed"
                    : row.status === "FAILED"
                      ? "Failed"
                      : "Pending"}
          </Badge>
        ),
      },
      {
        accessor: "journeyType",
        title: "Type",
        render: (row: JourneyListItem) => (
          <Badge
            size="sm"
            variant="light"
            color={row.journeyType === FunnelType.AUTO ? "blue" : "grape"}
            styles={{ root: badgeRootStyle }}
          >
            {row.journeyType === FunnelType.AUTO ? "Auto" : "Once"}
          </Badge>
        ),
      },
      {
        accessor: "tags",
        title: "Tags",
        render: (row: JourneyListItem) =>
          row.tags?.length ? (
            <Group gap={4} wrap="wrap">
              {row.tags.map((tag) => (
                <Badge key={tag} size="sm" variant="light" color="gray" styles={{ root: badgeRootStyle }}>
                  {tag}
                </Badge>
              ))}
            </Group>
          ) : (
            <Text size="sm" c="dimmed">—</Text>
          ),
      },
      {
        accessor: "createdBy",
        title: "Created by",
        render: (row: JourneyListItem) => (
          <Text size="sm" c="dark.4" lineClamp={1} ta="left">
            {row.createdBy}
          </Text>
        ),
      },
      {
        accessor: "updatedAt",
        title: "Last updated",
        render: (row: JourneyListItem) => (
          <Text size="sm" c="dark.4" ta="left">
            {row.updatedAt
              ? dayjs(row.updatedAt).format("MMM D, YYYY HH:mm")
              : "—"}
          </Text>
        ),
      },
      {
        accessor: "actions",
        title: "",
        render: (row: JourneyListItem) => (
          <Tooltip label="Delete journey" withArrow position="left">
            <ActionIcon
              variant="subtle"
              color="red"
              size="sm"
              loading={isDeleting}
              onClick={(e) => {
                e.stopPropagation();
                handleDeleteRow(row);
              }}
              aria-label={`Delete ${row.name}`}
            >
              <IconTrash size={16} />
            </ActionIcon>
          </Tooltip>
        ),
      },
    ],
    [handleDeleteRow, isDeleting],
  );

  const requestError =
    apiResponse?.error?.message ||
    (error instanceof Error ? error.message : null);

  const hasActiveFilters = Boolean(
    debouncedSearch.trim() ||
      statusFilter ||
      createdByFilter.length ||
      tagsFilter.length,
  );

  const emptyTitle = hasActiveFilters
    ? EMPTY_TAB_JOURNEY_FILTERED_TITLE
    : EMPTY_TAB_JOURNEY_TITLE;

  const emptyDescription = hasActiveFilters
    ? EMPTY_FILTERED_DESCRIPTION_JOURNEYS
    : EMPTY_TAB_JOURNEY_DESCRIPTION;

  const totalCount = payload?.totalCount ?? items.length;
  const totalPages =
    payload?.totalPages ?? Math.max(1, Math.ceil(totalCount / pageSize) || 1);

  const handlePageSizeChange = useCallback((next: number) => {
    setPageSize(next);
    setPage(1);
  }, []);

  return (
    <Box className={classes.shell}>
      <Box className={classes.header}>
        <Box className={classes.titleBlock}>
          <h1 className={classes.title}>{JOURNEYS_PAGE_TITLE}</h1>
          <p className={classes.subtitle}>{JOURNEYS_SUBTITLE}</p>
        </Box>
        <Box className={classes.toolbar}>
          <Button
            color="teal"
            leftSection={<IconRoute size={16} />}
            onClick={goCreateJourney}
          >
            {CREATE_JOURNEY_ITEM}
          </Button>
        </Box>
      </Box>

      <Box className={classes.filterBar}>
        <Box className={classes.filterBarInner}>
          <TextInput
            placeholder={SEARCH_PLACEHOLDER}
            leftSection={<IconSearch size={16} />}
            value={searchStr}
            onChange={onSearchChange}
            style={{ minWidth: 220, flex: "1 1 200px" }}
            size="sm"
          />
          <Select
            label={FILTER_STATUS_LABEL}
            placeholder={STATUS_OPTION_ALL}
            clearable
            data={[
              { value: "ACTIVE", label: "Active" },
              { value: "IN_PROGRESS", label: "In Progress" },
              { value: "WARN", label: "Warning" },
              { value: "PENDING", label: "Pending" },
              { value: "FAILED", label: "Failed" },
              { value: "COMPLETED", label: "Completed" },
            ]}
            value={statusFilter || null}
            onChange={(v) => setStatusFilter((v as StatusFilterValue) || "")}
            size="sm"
            style={{ width: 160 }}
          />
          <MultiSelect
            label={FILTER_CREATED_BY_LABEL}
            placeholder="Any"
            data={creatorOptions}
            value={createdByFilter}
            onChange={setCreatedByFilter}
            clearable
            searchable
            size="sm"
            style={{ minWidth: 200, flex: "1 1 180px" }}
          />
          <MultiSelect
            label={FILTER_TAGS_LABEL}
            placeholder="Any"
            data={tagOptions}
            value={tagsFilter}
            onChange={setTagsFilter}
            clearable
            searchable
            size="sm"
            style={{ minWidth: 200, flex: "1 1 180px" }}
          />
        </Box>
      </Box>

      {requestError ? (
        <ErrorAndEmptyState message={requestError} />
      ) : isLoading && !payload ? (
        <Box className={classes.loadingContainer}>
          <Loader color="teal" size="lg" />
          <Text size="sm" c="dimmed">
            {JOURNEYS_LOADING}
          </Text>
        </Box>
      ) : totalCount === 0 ? (
        <Box className={classes.emptyState}>
          <IconRoute
            size={64}
            className={classes.emptyStateIcon}
            stroke={1.25}
          />
          <Text className={classes.emptyStateTitle}>{emptyTitle}</Text>
          <Text className={classes.emptyStateDescription}>
            {emptyDescription}
          </Text>
          <Group mt="lg">
            <Button color="teal" onClick={goCreateJourney}>
              {CREATE_JOURNEY_ITEM}
            </Button>
          </Group>
        </Box>
      ) : (
        <>
          <Box className={classes.tableContainer}>
            <DataTable
              className={classes.dataTable}
              minHeight={280}
              highlightOnHover
              fetching={isFetching}
              idAccessor="id"
              columns={columns}
              records={items}
              onRowClick={({ record }) => openRow(record)}
              styles={{
                table: { cursor: "pointer" },
              }}
            />
          </Box>
          <FunnelJourneyListingPagination
            currentPage={page}
            totalPages={totalPages}
            totalCount={totalCount}
            pageSize={pageSize}
            onPrevious={() => setPage((p) => Math.max(1, p - 1))}
            onNext={() => setPage((p) => Math.min(totalPages, p + 1))}
            onGoToPage={setPage}
            onPageSizeChange={handlePageSizeChange}
          />
        </>
      )}
    </Box>
  );
}
