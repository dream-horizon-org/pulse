import {
  Box,
  Button,
  Group,
  Pagination,
  Popover,
  ScrollArea,
  TextInput,
  Badge,
  SegmentedControl,
  Text,
  Tooltip,
  ActionIcon,
} from "@mantine/core";
import classes from "./AlertListingPage.module.css";
import { useNavigate } from "react-router-dom";
import {
  ALERTS_SEARCH_PLACEHOLDER,
  CREATE_ALERT,
  NO_ALERT_CONFIGURED,
  ROUTES,
} from "../../constants";
import { ChangeEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  AlertListingPageProps,
  FiltersType,
} from "./AlertListingPage.interface";
import { IconFilterEdit, IconPlus, IconSearch, IconX } from "@tabler/icons-react";
import { useDisclosure } from "@mantine/hooks";
import { useGetAlertList, AlertListItem } from "../../hooks/useGetAlertList";
import { AlertCard } from "./components/AlertCard";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import { DefaultErrorResponse } from "../../helpers/makeRequest";
import { LoaderWithMessage } from "../../components/LoaderWithMessage";
import { debounce } from "lodash";
import { useGetAlertFilters } from "../../hooks/useGetAlertFilters";
import { AlertFilters } from "./components/AlertFilters";
import { useGetAlertScopes } from "../../hooks/useGetAlertScopes";
import { useGetAlertSeverities } from "../../hooks/useGetAlertSeverities";
import { useGetAllScopeMetrics } from "../../hooks/useGetAlertMetrics";

const LIMIT = 12;

type StatusTab = "all" | "firing" | "normal" | "snoozed";

export function AlertListingPage({
  isInteractionDetailsFlow = false,
  onCreateAlert,
}: AlertListingPageProps) {
  const [rows, setRows] = useState<Array<AlertListItem>>([]);
  const navigate = useNavigate();
  const [filterOpened, { open: filterOpen, close: filterClose }] = useDisclosure(false);
  const [searchStr, setSearchStr] = useState<string>("");
  const [errors, setErrors] = useState<DefaultErrorResponse | null | undefined>();
  const [pagination, setPagination] = useState<number>(0);
  const [statusTab, setStatusTab] = useState<StatusTab>("all");
  const offsetRef = useRef<number>(0);

  const [filters, setFilters] = useState<FiltersType>();

  const {
    data: response,
    refetch,
    isLoading,
    error,
  } = useGetAlertList({
    queryParams: {
      offset: offsetRef.current || 0,
      limit: LIMIT,
      name: searchStr || null,
      created_by: filters?.created_by || null,
      scope: filters?.scope || null,
      updated_by: filters?.updated_by || null,
    },
  });

  const { data: filtersData } = useGetAlertFilters();
  const { data: scopesData } = useGetAlertScopes();
  const { data: severitiesData } = useGetAlertSeverities();

  // Create lookup maps from API data
  const scopeLabels = useMemo(() => {
    const map: Record<string, string> = {};
    scopesData?.data?.scopes?.forEach((s) => {
      map[s.name] = s.label;
    });
    return map;
  }, [scopesData]);

  // Get all scope names and fetch metrics for all scopes
  const scopeNames = useMemo(() => {
    return scopesData?.data?.scopes?.map(s => s.name) || [];
  }, [scopesData]);

  const { metricLabels } = useGetAllScopeMetrics({ scopeNames });

  const severityConfig = useMemo(() => {
    const map: Record<number, { label: string; color: string; description: string }> = {};
    const colors = ["#ef4444", "#f59e0b", "#3b82f6", "#10b981", "#6366f1"];
    const labels = ["P1 Critical", "P2 Warning", "P3 Info", "P4 Low", "P5 Trace"];
    // severitiesData.data is an array of AlertSeverityItem
    const severities = severitiesData?.data || [];
    if (Array.isArray(severities)) {
      severities.forEach((s: { severity_id: number; name: number; description: string }) => {
        map[s.severity_id] = {
          label: labels[s.name - 1] || `P${s.name}`,
          color: colors[s.name - 1] || "#6b7280",
          description: s.description,
        };
      });
    }
    return map;
  }, [severitiesData]);

  useEffect(() => {
    refetch();
  }, [filters, refetch]);

  useEffect(() => {
    if (response?.data) {
      setPagination(response.data.offset / LIMIT);
      response.data.alerts.sort((a, b) =>
        b.alert_id.valueOf() - a.alert_id.valueOf() > 0 ? 1 : -1,
      );
      setRows(response.data.alerts);
      setErrors(null);
      return;
    }
    if (response?.error) {
      setErrors(response.error);
    }
  }, [response, isLoading]);

  // Filter rows based on status tab
  const filteredRows = useMemo(() => {
    if (statusTab === "all") return rows;
    if (statusTab === "firing") return rows.filter(r => r.status === "FIRING" && !r.is_snoozed);
    if (statusTab === "snoozed") return rows.filter(r => r.is_snoozed);
    if (statusTab === "normal") return rows.filter(r => r.status !== "FIRING" && !r.is_snoozed);
    return rows;
  }, [rows, statusTab]);

  // Calculate stats
  const stats = useMemo(() => {
    const total = rows.length;
    const firing = rows.filter(r => r.status === "FIRING" && !r.is_snoozed).length;
    const snoozed = rows.filter(r => r.is_snoozed).length;
    const normal = rows.filter(r => r.status !== "FIRING" && !r.is_snoozed).length;
    return { total, firing, snoozed, normal };
  }, [rows]);

  const handleSearch = (e: ChangeEvent<HTMLInputElement>) => {
    setPagination(0);
    offsetRef.current = 0;
    setSearchStr(e.target.value);
    debouncedRefetch();
  };

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const debouncedRefetch = useCallback(debounce(refetch, 300), []);

  const activeFilterCount = useMemo(() => {
    let count = 0;
    if (filters?.created_by) count++;
    if (filters?.scope) count++;
    if (filters?.updated_by) count++;
    return count;
  }, [filters]);

  const getAlertItems = () => {
    if (error || errors) {
      return (
        <ErrorAndEmptyState
          classes={[classes.EmptyStateAlert]}
          message={errors?.cause || error?.message || "Something went wrong"}
        />
      );
    }

    if (!filteredRows || filteredRows.length === 0) {
      return (
        <Box className={classes.emptyState}>
          <Box className={classes.emptyStateIcon}>🔔</Box>
          <Text className={classes.emptyStateTitle}>
            {statusTab === "all" ? NO_ALERT_CONFIGURED : `No ${statusTab} alerts`}
          </Text>
          <Text className={classes.emptyStateDescription}>
            {statusTab === "all" 
              ? "Create your first alert to monitor your application metrics"
              : `There are no alerts in ${statusTab} state right now`
            }
          </Text>
          {statusTab === "all" && (
            <Button 
              leftSection={<IconPlus size={16} />} 
              onClick={handleOnCreateAlert}
              className={classes.emptyStateButton}
            >
              {CREATE_ALERT}
            </Button>
          )}
        </Box>
      );
    }

    return (
      <Box className={classes.alertsGrid}>
        {filteredRows.map((element) => (
          <AlertCard
            key={element.alert_id}
            alert_id={element.alert_id}
            name={element.name}
            description={element.description}
            current_state={element.status}
            scope={element.scope}
            alerts={element.alerts}
            severity_id={element.severity_id}
            is_snoozed={element.is_snoozed}
            evaluation_period={element.evaluation_period}
            evaluation_interval={element.evaluation_interval}
            scopeLabels={scopeLabels}
            severityConfig={severityConfig}
            metricLabels={metricLabels}
            onClick={() =>
              navigate(`${ROUTES.ALERT_DETAIL.basePath}/${element.alert_id}`)
            }
          />
        ))}
      </Box>
    );
  };

  const handleFilter = (newFilters: FiltersType) => {
    offsetRef.current = 0;
    setFilters(newFilters);
    filterClose();
  };

  const handleReset = () => {
    offsetRef.current = 0;
    setFilters(undefined);
    filterClose();
  };

  const clearFilters = () => {
    setFilters(undefined);
  };

  useEffect(() => {
    refetch();
  }, [pagination, refetch]);

  const handlePaginationChange = (offset: number) => {
    setPagination(offset - 1);
    offsetRef.current = (offset - 1) * LIMIT;
  };

  const handleFilterToggle = (value: boolean) => {
    if (value === false) {
      filterClose();
      return;
    }
    filterOpen();
  };

  const handleOnCreateAlert = () => {
    if (isInteractionDetailsFlow) {
      onCreateAlert?.();
    } else {
      navigate(ROUTES["ALERTS_FORM"].basePath);
    }
  };

  return (
    <div className={classes.AlertListPageMainContainer}>
      {!isInteractionDetailsFlow && (
        <>
          {/* Header with Stats */}
          <Box className={classes.pageHeader}>
            <Box className={classes.headerTop}>
              <Box className={classes.titleSection}>
                <h1 className={classes.pageTitle}>Alerts</h1>
                <Badge size="lg" variant="light" color="teal" className={classes.totalBadge}>
                  {response?.data?.total_alerts || 0} Total
                </Badge>
              </Box>
              <Button
                leftSection={<IconPlus size={16} />}
                onClick={handleOnCreateAlert}
                className={classes.createButtonPrimary}
              >
                {CREATE_ALERT}
              </Button>
            </Box>

          </Box>

          {/* Controls Section */}
          <Box className={classes.controlsSection}>
            {/* Status Tabs */}
            <SegmentedControl
              value={statusTab}
              onChange={(v) => setStatusTab(v as StatusTab)}
              data={[
                { label: "All", value: "all" },
                { label: `Firing (${stats.firing})`, value: "firing" },
                { label: `Normal (${stats.normal})`, value: "normal" },
                { label: `Snoozed (${stats.snoozed})`, value: "snoozed" },
              ]}
              className={classes.statusTabs}
            />

            {/* Search and Filter Row */}
            <Group className={classes.searchRow}>
              <TextInput
                className={classes.searchInput}
                placeholder={ALERTS_SEARCH_PLACEHOLDER}
                leftSection={<IconSearch size={16} />}
                onChange={handleSearch}
                value={searchStr}
                size="sm"
              />
              
              <Group gap="xs">
                {activeFilterCount > 0 && (
                  <Badge 
                    size="sm" 
                    variant="filled" 
                    color="teal"
                    rightSection={
                      <ActionIcon size="xs" variant="transparent" color="white" onClick={clearFilters}>
                        <IconX size={12} />
                      </ActionIcon>
                    }
                  >
                    {activeFilterCount} filter{activeFilterCount > 1 ? "s" : ""}
                  </Badge>
                )}
                <Popover
                  opened={filterOpened}
                  withArrow
                  shadow="md"
                  onChange={handleFilterToggle}
                  closeOnEscape
                >
                  <Popover.Target>
                    <Tooltip label="Filter alerts">
                      <Button
                        onClick={filterOpen}
                        variant={activeFilterCount > 0 ? "filled" : "outline"}
                        size="sm"
                        className={classes.filterButton}
                      >
                        <IconFilterEdit size={18} strokeWidth={1.5} />
                      </Button>
                    </Tooltip>
                  </Popover.Target>
                  <Popover.Dropdown>
                    <AlertFilters
                      options={
                        filtersData?.data || {
                          created_by: [],
                          scope: [],
                          updated_by: [],
                        }
                      }
                      scopeLabels={scopeLabels}
                      onFilterSave={handleFilter}
                      onFilterReset={handleReset}
                      created_by={filters?.created_by || null}
                      scope={filters?.scope || null}
                      updated_by={filters?.updated_by || null}
                    />
                  </Popover.Dropdown>
                </Popover>
              </Group>
            </Group>
          </Box>
        </>
      )}

      {isInteractionDetailsFlow && (
        <Group className={classes.interactionHeader} pb="0" mb="md">
          <Text className={classes.interactionTitle}>Configured Alerts</Text>
          <Button
            size="sm"
            variant="outline"
            leftSection={<IconPlus size={14} />}
            onClick={handleOnCreateAlert}
            className={classes.createButton}
          >
            {CREATE_ALERT}
          </Button>
        </Group>
      )}

      <Box className={classes.AlertListItemContainer}>
        {isLoading ? (
          <Box className={classes.loader}>
            <LoaderWithMessage loadingMessage="Fetching alerts..." />
          </Box>
        ) : (
          <>
            <ScrollArea className={classes.scrollArea} type="scroll">
              <Box className={classes.AlertListItemInnerContainer}>
                {getAlertItems()}
              </Box>
            </ScrollArea>
            {filteredRows.length > 0 && (
              <Box className={classes.PaginationContainer}>
                <Text size="sm" c="dimmed">
                  Showing {Math.min(filteredRows.length, LIMIT)} of {response?.data?.total_alerts || 0} alerts
                </Text>
                <Pagination
                  total={
                    Math.ceil(
                      (response?.data?.total_alerts || 0) /
                        (response?.data?.limit || LIMIT),
                    ) || 0
                  }
                  value={pagination + 1}
                  onChange={handlePaginationChange}
                />
              </Box>
            )}
          </>
        )}
      </Box>
    </div>
  );
}
