import {
  Badge,
  Box,
  Button,
  Group,
  Loader,
  Menu,
  MultiSelect,
  Select,
  Tabs,
  Text,
  TextInput,
} from "@mantine/core";
import {
  IconChartFunnel,
  IconChevronDown,
  IconRoute,
  IconSearch,
  IconTrendingDown,
  IconTrendingUp,
} from "@tabler/icons-react";
import { DataTable } from "mantine-datatable";
import { ChangeEvent, useCallback, useEffect, useMemo, useState } from "react";
import { generatePath, useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import { ROUTES } from "../../constants";
import { useProjectContext } from "../../contexts";
import { useGetFunnelsJourneysList } from "../../hooks/useGetFunnelsJourneysList";
import type { FunnelJourneyListItem } from "../../services/funnels.service";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import {
  CREATE_FUNNEL_ITEM,
  CREATE_JOURNEY_ITEM,
  CREATE_MENU_LABEL,
  DEFAULT_PAGE_SIZE,
  EMPTY_FILTERED_DESCRIPTION,
  EMPTY_TAB_FUNNEL_DESCRIPTION,
  EMPTY_TAB_FUNNEL_FILTERED_TITLE,
  EMPTY_TAB_FUNNEL_TITLE,
  EMPTY_TAB_JOURNEY_DESCRIPTION,
  EMPTY_TAB_JOURNEY_FILTERED_TITLE,
  EMPTY_TAB_JOURNEY_TITLE,
  FILTER_CREATED_BY_LABEL,
  FILTER_STATUS_LABEL,
  FILTER_TAGS_LABEL,
  FILTER_TYPE_LABEL,
  FUNNELS_JOURNEYS_LOADING,
  FUNNELS_JOURNEYS_PAGE_TITLE,
  FUNNELS_JOURNEYS_SUBTITLE,
  COLUMN_CONVERSION_TITLE,
  SEARCH_PLACEHOLDER,
  STATUS_OPTION_ALL,
  TAB_FUNNELS,
  TAB_JOURNEYS,
  TYPE_OPTION_ALL,
  TYPE_OPTION_ORDERED,
  TYPE_OPTION_UNORDERED,
} from "./FunnelsJourneysList.constants";
import { FunnelsJourneysListPagination } from "./FunnelsJourneysListPagination";
import classes from "./FunnelsJourneysList.module.css";

const badgeRootStyle = { fontFamily: "inherit" as const };

function FunnelConversionCell({ row }: { row: FunnelJourneyListItem }) {
  if (row.kind !== "FUNNEL") {
    return (
      <Text size="sm" c="dimmed">
        —
      </Text>
    );
  }
  if (
    row.status === "CREATING" ||
    row.status === "UPDATING" ||
    row.overallConversionRate == null ||
    row.conversionTrend == null
  ) {
    return (
      <Text size="sm" c="dimmed">
        —
      </Text>
    );
  }
  const rate = row.overallConversionRate;
  const trend = row.conversionTrend;
  const up = trend > 0;

  return (
    <Group gap={8} align="center" wrap="nowrap">
      <Text size="sm" fw={600} ta="left">
        {rate.toFixed(1)}%
      </Text>
      {trend === 0 ? (
        <Text size="sm" c="dimmed" fw={500}>
          0.0%
        </Text>
      ) : (
        <Group gap={4} align="center" wrap="nowrap">
          {up ? (
            <IconTrendingUp
              size={14}
              style={{ color: "var(--mantine-color-teal-6)" }}
              aria-hidden
            />
          ) : (
            <IconTrendingDown
              size={14}
              style={{ color: "var(--mantine-color-red-6)" }}
              aria-hidden
            />
          )}
          <Text
            size="sm"
            fw={500}
            c={up ? "teal.7" : "red.7"}
            style={{ whiteSpace: "nowrap" }}
          >
            {up ? "+" : ""}
            {trend.toFixed(1)}%
          </Text>
        </Group>
      )}
    </Group>
  );
}

type StatusFilterValue =
  | ""
  | "ACTIVE"
  | "STOPPED"
  | "CREATING"
  | "UPDATING"
  | "COMPLETED";
type TypeFilterValue = "" | "ORDERED" | "UNORDERED";
type ListTab = "funnels" | "journeys";

export function FunnelsJourneysList() {
  const navigate = useNavigate();
  const { projectId } = useProjectContext();
  const [listTab, setListTab] = useState<ListTab>("funnels");
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
  }, [
    listTab,
    debouncedSearch,
    statusFilter,
    typeFilter,
    createdByFilter,
    tagsFilter,
  ]);

  const queryParams = useMemo(
    () => ({
      kind: (listTab === "funnels" ? "FUNNEL" : "JOURNEY") as
        | "FUNNEL"
        | "JOURNEY",
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
        listTab === "funnels" &&
        (typeFilter === "ORDERED" || typeFilter === "UNORDERED")
          ? typeFilter
          : null,
      page,
      pageSize,
    }),
    [
      listTab,
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
  } = useGetFunnelsJourneysList({ queryParams });

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
    navigate(
      generatePath(ROUTES.FUNNEL_ANALYSIS_CREATE_FUNNEL.path, { projectId }),
    );
  };

  const goCreateJourney = () => {
    if (!projectId) return;
    navigate(
      generatePath(ROUTES.FUNNEL_ANALYSIS_CREATE_JOURNEY.path, { projectId }),
    );
  };

  const openRow = (row: FunnelJourneyListItem) => {
    if (!projectId) return;
    navigate(
      generatePath(ROUTES.FUNNEL_JOURNEY_DETAIL.path, {
        projectId,
        id: row.id,
      }),
    );
  };

  const onSearchChange = (e: ChangeEvent<HTMLInputElement>) => {
    setSearchStr(e.currentTarget.value);
  };

  const onTabChange = (value: string | null) => {
    if (value !== "funnels" && value !== "journeys") return;
    setListTab(value);
    if (value === "journeys") {
      setTypeFilter("");
    }
  };

  const columns = useMemo(() => {
    const nameCol = {
      accessor: "name",
      title: "Name",
      render: (row: FunnelJourneyListItem) => (
        <Text size="sm" fw={700} lineClamp={1} ta="left">
          {row.name}
        </Text>
      ),
    };
    const statusCol = {
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
    };
    const conversionCol = {
      accessor: "conversion",
      title: COLUMN_CONVERSION_TITLE,
      render: (row: FunnelJourneyListItem) => (
        <FunnelConversionCell row={row} />
      ),
    };
    const createdByCol = {
      accessor: "createdBy",
      title: "Created by",
      render: (row: FunnelJourneyListItem) => (
        <Text size="sm" c="dark.4" lineClamp={1} ta="left">
          {row.createdBy}
        </Text>
      ),
    };
    const lastUpdatedCol = {
      accessor: "lastUpdatedAt",
      title: "Last updated",
      render: (row: FunnelJourneyListItem) => (
        <Text size="sm" c="dark.4" ta="left">
          {dayjs(row.lastUpdatedAt).format("MMM D, YYYY HH:mm")}
        </Text>
      ),
    };
    return listTab === "funnels"
      ? [nameCol, statusCol, conversionCol, createdByCol, lastUpdatedCol]
      : [nameCol, statusCol, createdByCol, lastUpdatedCol];
  }, [listTab]);

  const requestError =
    apiResponse?.error?.message ||
    (error instanceof Error ? error.message : null);

  const hasActiveFilters = Boolean(
    debouncedSearch.trim() ||
      statusFilter ||
      (listTab === "funnels" && typeFilter) ||
      createdByFilter.length ||
      tagsFilter.length,
  );

  const emptyTitle = hasActiveFilters
    ? listTab === "funnels"
      ? EMPTY_TAB_FUNNEL_FILTERED_TITLE
      : EMPTY_TAB_JOURNEY_FILTERED_TITLE
    : listTab === "funnels"
      ? EMPTY_TAB_FUNNEL_TITLE
      : EMPTY_TAB_JOURNEY_TITLE;

  const emptyDescription = hasActiveFilters
    ? EMPTY_FILTERED_DESCRIPTION
    : listTab === "funnels"
      ? EMPTY_TAB_FUNNEL_DESCRIPTION
      : EMPTY_TAB_JOURNEY_DESCRIPTION;

  const EmptyIcon = listTab === "funnels" ? IconChartFunnel : IconRoute;

  const totalCount = payload?.totalCount ?? items.length;
  const totalPages =
    payload?.totalPages ??
    Math.max(1, Math.ceil(totalCount / pageSize) || 1);

  const handlePageSizeChange = useCallback((next: number) => {
    setPageSize(next);
    setPage(1);
  }, []);

  return (
    <Box className={classes.shell}>
      <Box className={classes.header}>
        <Box className={classes.titleBlock}>
          <h1 className={classes.title}>{FUNNELS_JOURNEYS_PAGE_TITLE}</h1>
          <p className={classes.subtitle}>{FUNNELS_JOURNEYS_SUBTITLE}</p>
        </Box>
        <Box className={classes.toolbar}>
          <Menu shadow="md" width={220}>
            <Menu.Target>
              <Button color="teal" rightSection={<IconChevronDown size={16} />}>
                {CREATE_MENU_LABEL}
              </Button>
            </Menu.Target>
            <Menu.Dropdown>
              <Menu.Item
                leftSection={<IconChartFunnel size={16} />}
                onClick={goCreateFunnel}
              >
                {CREATE_FUNNEL_ITEM}
              </Menu.Item>
              <Menu.Item
                leftSection={<IconRoute size={16} />}
                onClick={goCreateJourney}
              >
                {CREATE_JOURNEY_ITEM}
              </Menu.Item>
            </Menu.Dropdown>
          </Menu>
        </Box>
      </Box>

      <Box className={classes.tabsCard}>
        <Tabs
          value={listTab}
          onChange={onTabChange}
          color="teal"
          variant="outline"
        >
          <Tabs.List>
            <Tabs.Tab
              value="funnels"
              leftSection={<IconChartFunnel size={16} />}
            >
              {TAB_FUNNELS}
            </Tabs.Tab>
            <Tabs.Tab value="journeys" leftSection={<IconRoute size={16} />}>
              {TAB_JOURNEYS}
            </Tabs.Tab>
          </Tabs.List>
        </Tabs>
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
          {listTab === "funnels" ? (
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
          ) : null}
        </Box>
      </Box>

      {requestError ? (
        <ErrorAndEmptyState message={requestError} />
      ) : isLoading && !payload ? (
        <Box className={classes.loadingContainer}>
          <Loader color="teal" size="lg" />
          <Text size="sm" c="dimmed">
            {FUNNELS_JOURNEYS_LOADING}
          </Text>
        </Box>
      ) : totalCount === 0 ? (
        <Box className={classes.emptyState}>
          <EmptyIcon size={64} className={classes.emptyStateIcon} stroke={1.25} />
          <Text className={classes.emptyStateTitle}>{emptyTitle}</Text>
          <Text className={classes.emptyStateDescription}>{emptyDescription}</Text>
          <Group mt="lg">
            <Button color="teal" onClick={goCreateFunnel}>
              {CREATE_FUNNEL_ITEM}
            </Button>
            <Button variant="default" onClick={goCreateJourney}>
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
          <FunnelsJourneysListPagination
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
