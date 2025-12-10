import {
  Box,
  Button,
  Group,
  Pagination,
  Popover,
  ScrollArea,
  TextInput,
  Title,
} from "@mantine/core";
import classes from "./AlertListingPage.module.css";
import { useNavigate } from "react-router-dom";
import {
  ALERTS_SEARCH_PLACEHOLDER,
  CREATE_ALERT,
  NO_ALERT_CONFIGURED,
  ROUTES,
} from "../../constants";
import { ChangeEvent, useCallback, useEffect, useRef, useState } from "react";
import {
  AlertListingPageProps,
  FiltersType,
} from "./AlertListingPage.interface";
import { IconFilterEdit } from "@tabler/icons-react";
import { useDisclosure } from "@mantine/hooks";
import { useGetAlertList, AlertListItem } from "../../hooks/useGetAlertList";
import { AlertCard } from "./components/AlertCard";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import { DefaultErrorResponse } from "../../helpers/makeRequest";
import { LoaderWithMessage } from "../../components/LoaderWithMessage";
import { debounce } from "lodash";
import { useGetAlertFilters } from "../../hooks/useGetAlertFilters";
import { AlertFilters } from "./components/AlertFilters";

const LIMIT = 12;

export function AlertListingPage({
  isInteractionDetailsFlow = false,
  onCreateAlert,
}: AlertListingPageProps) {
  const [rows, setRows] = useState<Array<AlertListItem>>([]);
  const navigate = useNavigate();
  const [filterOpened, { open: filterOpen, close: filterClose }] =
    useDisclosure(false);
  const [searchStr, setSearchStr] = useState<string>("");
  const [errors, setErrors] = useState<
    DefaultErrorResponse | null | undefined
  >();
  const [pagination, setPagination] = useState<number>(0);
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
      return;
    }
  }, [response, isLoading]);

  const handleSearch = (e: ChangeEvent<HTMLInputElement>) => {
    setPagination(0);
    offsetRef.current = 0;
    setSearchStr(e.target.value);
    debouncedRefetch();
  };

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const debouncedRefetch = useCallback(debounce(refetch, 300), []);

  const isFilterActive = () => {
    if (
      filters &&
      (filters.created_by || filters.scope || filters.updated_by)
    ) {
      return true;
    }

    return false;
  };

  const getAlertItems = () => {
    if (error || errors) {
      return (
        <ErrorAndEmptyState
          classes={[classes.EmptyStateAlert]}
          message={errors?.cause || error?.message || "Something went wrong"}
        />
      );
    }

    if (!rows || rows.length === 0) {
      return (
        <ErrorAndEmptyState
          classes={[classes.EmptyStateAlert]}
          message={NO_ALERT_CONFIGURED}
        />
      );
    }

    return (
      <Box className={classes.alertsGrid}>
        {rows.map((element) => (
          <AlertCard
            key={element.alert_id}
            alert_id={element.alert_id}
            name={element.name}
            description={element.description}
            current_state={element.current_state}
            scope={element.scope}
            alerts={element.alerts}
            severity_id={element.severity_id}
            is_snoozed={element.is_snoozed}
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
          {/* Header */}
          <Box className={classes.pageHeader}>
            <Box className={classes.titleSection}>
              <h1 className={classes.pageTitle}>Alerts</h1>
              <span className={classes.alertCount}>
                {response?.data?.total_alerts || 0}{" "}
                {response?.data?.total_alerts === 1 ? "Alert" : "Alerts"}
              </span>
            </Box>
          </Box>

          {/* Search and Filter Section */}
          <Box className={classes.controlsSection}>
            <Group className={classes.searchBarContainer}>
              <TextInput
                className={classes.searchInput}
                placeholder={ALERTS_SEARCH_PLACEHOLDER}
                onChange={handleSearch}
                size="sm"
              />
              <Popover
                opened={filterOpened}
                withArrow
                shadow="md"
                onChange={handleFilterToggle}
                closeOnEscape
              >
                <Popover.Target>
                  <Button
                    onClick={filterOpen}
                    variant={isFilterActive() ? "filled" : "outline"}
                    size="sm"
                    className={classes.filterButton}
                  >
                    <IconFilterEdit size={18} strokeWidth={1.5} />
                  </Button>
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
                    onFilterSave={handleFilter}
                    onFilterReset={handleReset}
                    created_by={filters?.created_by || null}
                    scope={filters?.scope || null}
                    updated_by={filters?.updated_by || null}
                  />
                </Popover.Dropdown>
              </Popover>
              <Button
                size="sm"
                variant="outline"
                onClick={handleOnCreateAlert}
                className={classes.createButton}
              >
                {CREATE_ALERT}
              </Button>
            </Group>
          </Box>
        </>
      )}

      {isInteractionDetailsFlow && (
        <Group className={classes.searchBarContainer} pb="0" mb="md">
          <Title order={5} className={classes.useCaseIdTitle}>
            Configured Alerts
          </Title>
          <Button
            size="sm"
            variant="outline"
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
            <Box className={classes.PaginationContainer}>
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
          </>
        )}
      </Box>
    </div>
  );
}


