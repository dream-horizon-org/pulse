import {
  Badge,
  Box,
  Button,
  Group,
  Loader,
  MultiSelect,
  Select,
  Text,
  TextInput,
} from "@mantine/core";
import { IconChartFunnel, IconSearch } from "@tabler/icons-react";
import { DataTable } from "mantine-datatable";
import { ChangeEvent, useCallback, useEffect, useMemo, useState } from "react";
import { generatePath, useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import { ROUTES } from "../../constants";
import { useProjectContext } from "../../contexts";
import { useGetFunnelsList } from "../../hooks/useGetFunnelsList";
import type { FunnelJourneyListItem } from "../../services/funnels.service";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import { FunnelConversionCell } from "./components/FunnelConversionCell";
import {
  DEFAULT_PAGE_SIZE,
  FILTER_CREATED_BY_LABEL,
  FILTER_STATUS_LABEL,
  FILTER_TAGS_LABEL,
  SEARCH_PLACEHOLDER,
  STATUS_OPTION_ALL,
} from "./FunnelJourneyListing.constants";
import {
  COLUMN_CONVERSION_TITLE,
  CREATE_FUNNEL_ITEM,
  EMPTY_FILTERED_DESCRIPTION_FUNNELS,
  EMPTY_TAB_FUNNEL_DESCRIPTION,
  EMPTY_TAB_FUNNEL_FILTERED_TITLE,
  EMPTY_TAB_FUNNEL_TITLE,
  FILTER_TYPE_LABEL,
  FUNNELS_LOADING,
  FUNNELS_PAGE_TITLE,
  FUNNELS_SUBTITLE,
  TYPE_OPTION_ALL,
  TYPE_OPTION_ORDERED,
  TYPE_OPTION_UNORDERED,
} from "./funnelsList.constants";
import { FunnelJourneyListingPagination } from "./FunnelJourneyListingPagination";
import classes from "./FunnelJourneyListing.module.css";

const badgeRootStyle = { fontFamily: "inherit" as const };

type StatusFilterValue =
  | ""
  | "ACTIVE"
  | "STOPPED"
  | "CREATING"
  | "UPDATING"
  | "COMPLETED";
type TypeFilterValue = "" | "ORDERED" | "UNORDERED";

export function FunnelsList() {
  const navigate = useNavigate();
  const { projectId } = useProjectContext();
  const [searchStr, setSearchStr] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");

  const [statusFilter, setStatusFilter] = useState<StatusFilterValue>("");
  const [typeFilter, setTypeFilter] = useState<TypeFilterValue>("");
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
  }, [debouncedSearch, statusFilter, typeFilter, createdByFilter, tagsFilter]);

  const queryParams = useMemo(
    () => ({
      search: debouncedSearch.trim() || null,
      status:
        statusFilter === "ACTIVE" ||
        statusFilter === "STOPPED" ||
        statusFilter === "CREATING" ||
        statusFilter === "UPDATING" ||
        statusFilter === "COMPLETED"
          ? statusFilter
          : null,
      createdBy: createdByFilter.length ? createdByFilter : null,
      tags: tagsFilter.length ? tagsFilter : null,
      funnelType:
        typeFilter === "ORDERED" || typeFilter === "UNORDERED"
          ? typeFilter
          : null,
      page,
      pageSize,
    }),
    [
      debouncedSearch,
      statusFilter,
      createdByFilter,
      tagsFilter,
      typeFilter,
      page,
      pageSize,
    ],
  );

  const {
    data: apiResponse,
    isLoading,
    isFetching,
    error,
  } = useGetFunnelsList({ queryParams });

  const payload = apiResponse?.data;
  const items = payload?.items ?? [];

  useEffect(() => {
    const serverPage = payload?.page;
    if (serverPage == null) return;
    setPage((p) => (serverPage !== p ? serverPage : p));
  }, [payload?.page]);

  const creatorOptions =
    payload?.filterOptions?.creators?.map((c) => ({ value: c, label: c })) ??
    [];
  const tagOptions =
    payload?.filterOptions?.tags?.map((t) => ({ value: t, label: t })) ?? [];

  const goCreateFunnel = () => {
    if (!projectId) return;
    navigate(generatePath(ROUTES.FUNNELS_CREATE.path, { projectId }));
  };

  const openRow = (row: FunnelJourneyListItem) => {
    if (!projectId) return;
    navigate(
      generatePath(ROUTES.FUNNEL_DETAIL.path, {
        projectId,
        funnelId: row.id,
      }),
    );
  };

  const onSearchChange = (e: ChangeEvent<HTMLInputElement>) => {
    setSearchStr(e.currentTarget.value);
  };

  const columns = useMemo(
    () => [
      {
        accessor: "name",
        title: "Name",
        render: (row: FunnelJourneyListItem) => (
          <Text size="sm" fw={700} lineClamp={1} ta="left">
            {row.name}
          </Text>
        ),
      },
      {
        accessor: "status",
        title: "Status",
        render: (row: FunnelJourneyListItem) => (
          <Badge
            size="sm"
            color={
              row.status === "ACTIVE"
                ? "teal"
                : row.status === "CREATING"
                  ? "blue"
                  : row.status === "UPDATING"
                    ? "orange"
                    : row.status === "COMPLETED"
                      ? "violet"
                      : "gray"
            }
            variant="light"
            styles={{ root: badgeRootStyle }}
          >
            {row.status === "ACTIVE"
              ? "Active"
              : row.status === "CREATING"
                ? "Creating"
                : row.status === "UPDATING"
                  ? "Updating"
                  : row.status === "COMPLETED"
                    ? "Completed"
                    : "Stopped"}
          </Badge>
        ),
      },
      {
        accessor: "conversion",
        title: COLUMN_CONVERSION_TITLE,
        render: (row: FunnelJourneyListItem) => (
          <FunnelConversionCell row={row} />
        ),
      },
      {
        accessor: "createdBy",
        title: "Created by",
        render: (row: FunnelJourneyListItem) => (
          <Text size="sm" c="dark.4" lineClamp={1} ta="left">
            {row.createdBy}
          </Text>
        ),
      },
      {
        accessor: "lastUpdatedAt",
        title: "Last updated",
        render: (row: FunnelJourneyListItem) => (
          <Text size="sm" c="dark.4" ta="left">
            {dayjs(row.lastUpdatedAt).format("MMM D, YYYY HH:mm")}
          </Text>
        ),
      },
    ],
    [],
  );

  const requestError =
    apiResponse?.error?.message ||
    (error instanceof Error ? error.message : null);

  const hasActiveFilters = Boolean(
    debouncedSearch.trim() ||
      statusFilter ||
      typeFilter ||
      createdByFilter.length ||
      tagsFilter.length,
  );

  const emptyTitle = hasActiveFilters
    ? EMPTY_TAB_FUNNEL_FILTERED_TITLE
    : EMPTY_TAB_FUNNEL_TITLE;

  const emptyDescription = hasActiveFilters
    ? EMPTY_FILTERED_DESCRIPTION_FUNNELS
    : EMPTY_TAB_FUNNEL_DESCRIPTION;

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
          <h1 className={classes.title}>{FUNNELS_PAGE_TITLE}</h1>
          <p className={classes.subtitle}>{FUNNELS_SUBTITLE}</p>
        </Box>
        <Box className={classes.toolbar}>
          <Button
            color="teal"
            leftSection={<IconChartFunnel size={16} />}
            onClick={goCreateFunnel}
          >
            {CREATE_FUNNEL_ITEM}
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
              { value: "STOPPED", label: "Stopped" },
              { value: "CREATING", label: "Creating" },
              { value: "UPDATING", label: "Updating" },
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
          <Select
            label={FILTER_TYPE_LABEL}
            placeholder={TYPE_OPTION_ALL}
            clearable
            data={[
              { value: "ORDERED", label: TYPE_OPTION_ORDERED },
              { value: "UNORDERED", label: TYPE_OPTION_UNORDERED },
            ]}
            value={typeFilter || null}
            onChange={(v) => setTypeFilter((v as TypeFilterValue) || "")}
            size="sm"
            style={{ width: 160 }}
          />
        </Box>
      </Box>

      {requestError ? (
        <ErrorAndEmptyState message={requestError} />
      ) : isLoading && !payload ? (
        <Box className={classes.loadingContainer}>
          <Loader color="teal" size="lg" />
          <Text size="sm" c="dimmed">
            {FUNNELS_LOADING}
          </Text>
        </Box>
      ) : totalCount === 0 ? (
        <Box className={classes.emptyState}>
          <IconChartFunnel
            size={64}
            className={classes.emptyStateIcon}
            stroke={1.25}
          />
          <Text className={classes.emptyStateTitle}>{emptyTitle}</Text>
          <Text className={classes.emptyStateDescription}>
            {emptyDescription}
          </Text>
          <Group mt="lg">
            <Button color="teal" onClick={goCreateFunnel}>
              {CREATE_FUNNEL_ITEM}
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
