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
} from "@tabler/icons-react";
import { DataTable } from "mantine-datatable";
import { ChangeEvent, useEffect, useMemo, useState } from "react";
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
  FUNNELS_JOURNEYS_PAGE_TITLE,
  FUNNELS_JOURNEYS_SUBTITLE,
  SEARCH_PLACEHOLDER,
  STATUS_OPTION_ALL,
  TAB_FUNNELS,
  TAB_JOURNEYS,
  TYPE_OPTION_ALL,
  TYPE_OPTION_ORDERED,
  TYPE_OPTION_UNORDERED,
} from "./FunnelsJourneysList.constants";
import classes from "./FunnelsJourneysList.module.css";

type StatusFilterValue = "" | "ACTIVE" | "STOPPED" | "CREATING";
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

  useEffect(() => {
    const t = window.setTimeout(() => setDebouncedSearch(searchStr), 300);
    return () => window.clearTimeout(t);
  }, [searchStr]);

  const queryParams = useMemo(
    () => ({
      kind: (listTab === "funnels" ? "FUNNEL" : "JOURNEY") as
        | "FUNNEL"
        | "JOURNEY",
      search: debouncedSearch.trim() || null,
      status:
        statusFilter === "ACTIVE" ||
        statusFilter === "STOPPED" ||
        statusFilter === "CREATING"
          ? statusFilter
          : null,
      createdBy: createdByFilter.length ? createdByFilter : null,
      tags: tagsFilter.length ? tagsFilter : null,
      funnelType:
        listTab === "funnels" &&
        (typeFilter === "ORDERED" || typeFilter === "UNORDERED")
          ? typeFilter
          : null,
    }),
    [
      listTab,
      debouncedSearch,
      statusFilter,
      createdByFilter,
      tagsFilter,
      typeFilter,
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

  const columns = useMemo(
    () => [
      {
        accessor: "name",
        title: "Name",
        render: (row: FunnelJourneyListItem) => (
          <Text size="sm" fw={500} lineClamp={1}>
            {row.name}
          </Text>
        ),
      },
      {
        accessor: "status",
        title: "Status",
        render: (row: FunnelJourneyListItem) => (
          <Badge
            color={
              row.status === "ACTIVE"
                ? "teal"
                : row.status === "CREATING"
                  ? "blue"
                  : row.status === "UPDATING"
                    ? "orange"
                    : "gray"
            }
            variant="light"
          >
            {row.status === "ACTIVE"
              ? "Active"
              : row.status === "CREATING"
                ? "Creating"
                : row.status === "UPDATING"
                  ? "Updating"
                  : "Stopped"}
          </Badge>
        ),
      },
      {
        accessor: "createdBy",
        title: "Created by",
        render: (row: FunnelJourneyListItem) => (
          <Text size="sm" c="dimmed" lineClamp={1}>
            {row.createdBy}
          </Text>
        ),
      },
      {
        accessor: "lastUpdatedAt",
        title: "Last updated",
        render: (row: FunnelJourneyListItem) => (
          <Text size="sm" c="dimmed">
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

  return (
    <Box className={classes.shell}>
      <Box className={classes.header}>
        <Box className={classes.titleBlock}>
          <Text size="xl" fw={700} c="dark.7">
            {FUNNELS_JOURNEYS_PAGE_TITLE}
          </Text>
          <Text size="sm" c="dimmed" mt={4}>
            {FUNNELS_JOURNEYS_SUBTITLE}
          </Text>
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

      <Tabs
        value={listTab}
        onChange={onTabChange}
        color="teal"
        variant="outline"
      >
        <Tabs.List>
          <Tabs.Tab value="funnels" leftSection={<IconChartFunnel size={16} />}>
            {TAB_FUNNELS}
          </Tabs.Tab>
          <Tabs.Tab value="journeys" leftSection={<IconRoute size={16} />}>
            {TAB_JOURNEYS}
          </Tabs.Tab>
        </Tabs.List>
      </Tabs>

      <Box className={classes.filtersRow} mt="md">
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

      {requestError ? (
        <ErrorAndEmptyState message={requestError} />
      ) : isLoading && !payload ? (
        <Box className={classes.loaderWrap}>
          <Loader color="teal" />
        </Box>
      ) : items.length === 0 ? (
        <Box className={classes.emptyState}>
          <Box className={classes.emptyStateIcon}>
            <EmptyIcon size={28} color="#0ba09a" />
          </Box>
          <Text size="lg" fw={700} c="dark.6">
            {emptyTitle}
          </Text>
          <Text size="sm" c="dimmed" maw={420} mt={6}>
            {emptyDescription}
          </Text>
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
        <Box className={classes.tableCard}>
          <Box className={classes.tableScroll}>
            <DataTable
              minHeight={280}
              withTableBorder
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
        </Box>
      )}
    </Box>
  );
}
