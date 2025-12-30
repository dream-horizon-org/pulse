import classes from "./CriticalInteractionList.module.css";

import {
  Box,
  Button,
  Group,
  Popover,
  ScrollArea,
  Switch,
  TextInput,
} from "@mantine/core";
import { useIntersection } from "@mantine/hooks";

import {
  COOKIES_KEY,
  CRITICAL_INTERACTION_LISTING_PAGE_CONSTANTS,
  ROUTES,
} from "../../constants";
import { IconFilterEdit } from "@tabler/icons-react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import {
  ChangeEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  defaultPageSize,
  FiltersType,
  PaginationType,
} from "./CriticalInteractionList.interface";
import {
  GetInteractionsResponse,
  useGetInteractions,
} from "../../hooks/useGetInteractions";
import { useDebouncedCallback, useDisclosure } from "@mantine/hooks";
import { useGetInteractionListFilters } from "../../hooks/useGetInteractionListFilters";
import { Filters } from "./components/Filters";
import { debounce, size, toNumber } from "lodash";
import { getCookies } from "../../helpers/cookies";
import { getDateFilterDetails } from "./utils";
import { InteractionCard } from "./components/InteractionCard";
import { filtersToQueryString } from "../../helpers/filtersToQueryString";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import { LoaderWithMessage } from "../../components/LoaderWithMessage";
import { CardSkeleton } from "../../components/Skeletons";
import { useGetDataQuery } from "../../hooks";
import { PulseType } from "../../constants/PulseOtelSemcov";
import dayjs from "dayjs";
import { useAnalytics } from "../../hooks/useAnalytics";

interface InteractionMetrics {
  interactionName: string;
  apdex: number;
  errorRate: number;
  p50: number;
  poorUserPercentage: number;
}

export function CriticalInteractionList() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { trackClick } = useAnalytics("InteractionList");
  const searchFields = Object.fromEntries(searchParams.entries());
  const [opened, { open, close }] = useDisclosure(false);
  const [checked, setChecked] = useState(
    searchFields?.userEmail === getCookies(COOKIES_KEY.USER_EMAIL),
  );
  const [searchStr, setSearchStr] = useState<string>(
    searchFields?.interactionName || "",
  );
  const [rows, setRows] = useState<GetInteractionsResponse>({
    interactions: [],
    totalInteractions: 0,
  });
  const [totalRecords, setTotalRecords] = useState<number>(0);

  const pgNo = toNumber(searchFields.pageNo) || 0;
  const [pagination, setPagination] = useState<PaginationType>({
    page: pgNo,
    size: (pgNo + 1) * defaultPageSize,
  });
  const [filters, setFilters] = useState<FiltersType>({
    users: searchFields?.userEmail || "",
    status: searchFields?.status || "",
  });
  const loaderRef = useRef<HTMLDivElement | null>(null);
  const scrollContainerRef = useRef<HTMLDivElement | null>(null);

  const { ref, entry } = useIntersection({
    root: loaderRef.current,
    threshold: 0.9, // Fires when the loader comes fully into view
  });

  useEffect(() => {
    setSearchParams(
      filtersToQueryString({
        ...Object.fromEntries(searchParams.entries()),
        userEmail: filters?.users,
        interactionName: searchStr,
        pageNo: String(pagination?.page),
        status: filters?.status || "",
      }),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters?.users, filters?.status, searchStr, pagination?.page]);

  const { data: filterValuesFromServer } = useGetInteractionListFilters();

  const {
    data: response,
    refetch,
    isFetching,
    isLoading,
  } = useGetInteractions({
    queryParams: {
      page: pagination.size > defaultPageSize ? 0 : pagination.page,
      size: pagination.size || defaultPageSize,
      userEmail: filters.users || undefined,
      interactionName: searchStr || undefined,
      status: filters.status || undefined,
    },
    pageIdentifier: "list",
  });

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const fetchInteractionsList = useCallback(debounce(refetch, 300), []);

  useEffect(() => {
    const pg = pagination.page;
    setTimeout(() => {
      if (scrollContainerRef.current) {
        scrollContainerRef.current.scrollTo({
          top: pg * 784, // approx. height of cards
          behavior: "smooth",
        });
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scrollContainerRef.current]);

  useEffect(() => {
    const { data } = response ?? {};

    if (!(isFetching || isLoading) && data) {
      setTotalRecords(data.totalInteractions);
      setRows((prev) => ({
        ...prev,
        interactions: [
          ...(prev.interactions || []),
          ...(data?.interactions || []),
        ],
      }));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isFetching]);

  useEffect(() => {
    if (!(isFetching || isLoading)) refetch();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pagination, refetch]);

  useEffect(() => {
    if (!(isFetching || isLoading)) fetchInteractionsList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refetch, searchStr, filters]);

  const handleSearch = useDebouncedCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      setPagination({
        page: 0,
        size: defaultPageSize,
      });
      setRows({ interactions: [], totalInteractions: 0 });
      setSearchStr(e.target.value);
    },
    300,
  );

  const handleFilterChange = (filter: FiltersType) => {
    // Check if filters have actually changed to prevent duplicate fetches
    const hasFilterChanged =
      filter.users !== filters.users || filter.status !== filters.status;

    if (!hasFilterChanged) {
      close();
      return;
    }

    setPagination({
      page: 0,
      size: defaultPageSize,
    });
    setRows({ interactions: [], totalInteractions: 0 });
    setFilters((prev) => ({ ...prev, ...filter }));
    const currentUserEmail = getCookies(COOKIES_KEY.USER_EMAIL);
    if (filter.users !== currentUserEmail) {
      setChecked(false);
    }
    close();
  };

  const isFilterActive = () => {
    if (filters && (filters.users || filters.status)) {
      return true;
    }

    return false;
  };

  const handleFilterToggle = (value: boolean) => {
    if (value === false) {
      close();
      return;
    }

    open();
  };

  const handleMyInteractionToggle = (event: ChangeEvent<HTMLInputElement>) => {
    event.preventDefault();
    const updatedChecked = event.currentTarget.checked;
    setChecked(event.currentTarget.checked);
    setPagination({
      page: 0,
      size: defaultPageSize,
    });
    if (updatedChecked) {
      const email = getCookies(COOKIES_KEY.USER_EMAIL);
      setFilters((prev) => ({
        ...prev,
        users: email || "",
      }));
    } else {
      setFilters((prev) => ({
        ...prev,
        users: "",
      }));
    }
    setRows({ totalInteractions: 0, interactions: [] });
  };

  const data = useMemo(() => rows?.interactions || [], [rows]);

  const hasMore = data?.length < totalRecords;

  const { startTime, endTime } = useMemo(() => getDateFilterDetails(), []);

  // Extract interaction names from the fetched interactions
  const interactionNames = useMemo(() => {
    return data
      .map((interaction) => interaction.name)
      .filter((name): name is string => Boolean(name));
  }, [data]);

  const { data: metricsData, isLoading: isLoadingMetrics } = useGetDataQuery({
    requestBody: {
      dataType: "TRACES",
      timeRange: {
        start: dayjs.utc(startTime).toISOString(),
        end: dayjs.utc(endTime).toISOString(),
      },
      select: [
        {
          function: "COL",
          param: { field: "SpanName" },
          alias: "interaction_name",
        },
        { function: "APDEX", alias: "apdex" },
        { function: "INTERACTION_SUCCESS_COUNT", alias: "success_count" },
        { function: "INTERACTION_ERROR_COUNT", alias: "error_count" },
        { function: "USER_CATEGORY_EXCELLENT", alias: "user_excellent" },
        { function: "USER_CATEGORY_GOOD", alias: "user_good" },
        { function: "USER_CATEGORY_AVERAGE", alias: "user_avg" },
        { function: "USER_CATEGORY_POOR", alias: "user_poor" },
        { function: "DURATION_P50", alias: "p50" },
      ],
      filters: [
        { field: "SpanName", operator: "IN", value: interactionNames },
        { field: "PulseType", operator: "EQ", value: [PulseType.INTERACTION] },
      ],
      groupBy: ["interaction_name"],
    },
    enabled: interactionNames.length > 0,
  });

  // Transform metrics data into a map for easy lookup
  const metricsMap = useMemo(() => {
    const map: Record<string, InteractionMetrics> = {};

    if (!metricsData?.data?.rows || metricsData.data.rows.length === 0) {
      return map;
    }

    const fields = metricsData.data.fields;
    const interactionNameIndex = fields.indexOf("interaction_name");
    const apdexIndex = fields.indexOf("apdex");
    const successCountIndex = fields.indexOf("success_count");
    const errorCountIndex = fields.indexOf("error_count");
    const userPoorIndex = fields.indexOf("user_poor");
    const userExcellentIndex = fields.indexOf("user_excellent");
    const userGoodIndex = fields.indexOf("user_good");
    const userAvgIndex = fields.indexOf("user_avg");
    const p50Index = fields.indexOf("p50");

    metricsData.data.rows.forEach((row) => {
      const interactionName = row[interactionNameIndex];
      const successCount = parseFloat(row[successCountIndex]) || 0;
      const errorCount = parseFloat(row[errorCountIndex]) || 0;
      const totalCount = successCount + errorCount;
      const errorRate = totalCount > 0 ? (errorCount / totalCount) * 100 : 0;

      const userPoor = parseFloat(row[userPoorIndex]) || 0;
      const userExcellent = parseFloat(row[userExcellentIndex]) || 0;
      const userGood = parseFloat(row[userGoodIndex]) || 0;
      const userAvg = parseFloat(row[userAvgIndex]) || 0;
      const totalUsers = userPoor + userExcellent + userGood + userAvg;
      const poorUserPercentage =
        totalUsers > 0 ? (userPoor / totalUsers) * 100 : 0;

      map[interactionName] = {
        interactionName,
        apdex: parseFloat(row[apdexIndex]) || 0,
        errorRate,
        p50: Math.round(parseFloat(row[p50Index]) || 0),
        poorUserPercentage,
      };
    });

    return map;
  }, [metricsData]);

  const loadMoreItems = () => {
    if (
      !hasMore ||
      isFetching ||
      pagination.page >= Math.ceil(size(data) / defaultPageSize)
    )
      return;
    setPagination((prev) => ({
      ...prev,
      page: prev.page + 1,
      size: defaultPageSize,
    }));
  };

  useEffect(() => {
    if (entry?.isIntersecting) {
      loadMoreItems();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entry]);

  const onInteractionClick = (interaction: {
    id: number;
    name: string | undefined;
  }) => {
    trackClick(`Interaction: ${interaction.name || 'unknown'}`);
    navigate(
      `${ROUTES["CRITICAL_INTERACTION_DETAILS"].basePath}/${interaction.name || ""}`,
    );
  };

  const renderContent = () => {
    // Show loading state while fetching interactions or metrics
    if (isLoading || isLoadingMetrics) {
      return (
        <ScrollArea className={classes.scrollArea}>
          <Box className={classes.criticalInteractionsTableContainer}>
            {Array.from({ length: 8 }).map((_, index) => (
              <CardSkeleton 
                key={index} 
                height={180} 
                showHeader 
                contentRows={3} 
              />
            ))}
          </Box>
        </ScrollArea>
      );
    }

    if (data.length === 0) {
      return (
        <ErrorAndEmptyState
          classes={[classes.error]}
          message={
            CRITICAL_INTERACTION_LISTING_PAGE_CONSTANTS.NO_INTERACTIONS_MESSAGE
          }
        />
      );
    }

    return (
      <ScrollArea
        viewportRef={scrollContainerRef}
        className={classes.scrollArea}
      >
        <Box className={classes.criticalInteractionsTableContainer}>
          {data.map((item) => {
            const interactionName = item?.name || "";
            const metrics = metricsMap[interactionName];

            return (
              <InteractionCard
                key={item?.id}
                interactionName={interactionName}
                description={item?.description}
                onClick={() =>
                  onInteractionClick({
                    id: item?.id,
                    name: item?.name
                  })
                }
                apdexScore={metrics?.apdex}
                errorRateValue={metrics?.errorRate}
                p50Latency={metrics?.p50}
                poorUserPercentage={metrics?.poorUserPercentage}
              />
            );
          })}
        </Box>
        {isFetching && hasMore && (
          <Box ref={ref} className={classes.loadMoreLoader}>
            <LoaderWithMessage loadingMessage="Loading more interactions..." />
          </Box>
        )}
      </ScrollArea>
    );
  };

  return (
    <Box className={classes.pageContainer}>
      {/* Compact Header */}
      <Box className={classes.pageHeader}>
        <Box className={classes.titleSection}>
          <h1 className={classes.pageTitle}>Critical Interactions</h1>
          <span className={classes.interactionCount}>
            {totalRecords} {totalRecords === 1 ? "Interaction" : "Interactions"}
          </span>
        </Box>
      </Box>

      {/* Search and Filters Section */}
      <Box className={classes.controlsSection}>
        <Group className={classes.searchBarContainer}>
          <TextInput
            className={classes.searchInput}
            placeholder={
              CRITICAL_INTERACTION_LISTING_PAGE_CONSTANTS.USER_EXPERIENCE_LIST_SEARCH_BAR_PLACEHOLDER_TEXT
            }
            onChange={handleSearch}
            size="sm"
            defaultValue={searchStr}
          />

          <Switch
            size="sm"
            label={CRITICAL_INTERACTION_LISTING_PAGE_CONSTANTS.SWITCH_TEXT}
            checked={checked}
            onChange={handleMyInteractionToggle}
            className={classes.swtichMyInteraction}
          />
          <Popover
            opened={opened}
            withArrow
            shadow="md"
            onChange={handleFilterToggle}
            closeOnEscape
          >
            <Popover.Target>
              <Button
                onClick={open}
                variant={isFilterActive() ? "filled" : "light"}
                size="sm"
                className={classes.filterButton}
              >
                <IconFilterEdit size={18} strokeWidth={1.5} />
              </Button>
            </Popover.Target>
            <Popover.Dropdown>
              <Filters
                defaultFilters={filters}
                handleFiltersChange={handleFilterChange}
                defaultFilterValuesFromServer={filterValuesFromServer || { createdBy: [], statuses: [] }}
              />
            </Popover.Dropdown>
          </Popover>

          <Link to={ROUTES["CRITICAL_INTERACTION_FORM"].basePath}>
            <Button size="sm" variant="light" className={classes.createButton}>
              {
                CRITICAL_INTERACTION_LISTING_PAGE_CONSTANTS.CREATE_USER_EXPERIENCE_BUTTON_TEXT
              }
            </Button>
          </Link>
        </Group>
      </Box>

      {/* Content Section */}
      {renderContent()}
    </Box>
  );
}
