import classes from "./CriticalInteractionDetails.module.css";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { Badge, Box, Grid, Tabs, Title, Tooltip, useMantineTheme } from "@mantine/core";
import {
  CRITICAL_INTERACTION_DETAILS_PAGE_CONSTANTS,
  ROUTES,
} from "../../constants";
import { AllInteractionDetails } from "./AllInteractionDetails";
import { useEffect, useState } from "react";
import { IconArrowNarrowLeft } from "@tabler/icons-react";
import { Manage } from "../CriticalInteractionList/components/Manage";
import { InteractionDetailsFilters } from "./components/InteractionDetailsFilters";
import { InteractionDetailsMainContent } from "./components/InteractionDetailsMainContent";
import { useGetInteractionDetails } from "../../hooks/useGetInteractionDetails";
import { useFilterStore } from "../../stores/useFilterStore";
import Analysis from "./components/InteractionDetailsMainContent/components/Analysis";
import DateTimeRangePicker from "./components/DateTimeRangePicker/DateTimeRangePicker";
import ProblematicInteractions from "./components/InteractionDetailsMainContent/components/ProblematicInteractions/ProblematicInteractions";
import { GraphCardSkeleton, SkeletonLoader } from "../../components/Skeletons";

export function CiritcalInteractionDetails() {
  const [searchParams, setSearchParams] = useSearchParams();
  const {
    initializeFromUrlParams,
    filterValues,
    startTime,
    endTime,
    quickTimeRangeString,
    quickTimeRangeFilterIndex,
    handleTimeFilterChange,
    selectedTimeFilter,
  } = useFilterStore();

  const navigate = useNavigate();
  const routeParams = useParams()["*"];
  const theme = useMantineTheme();

  const routeParamsArray = routeParams?.split("/") ?? [];
  const [interactionName] = routeParamsArray;
  const {
    data: interactionDetails,
    isLoading: fetchingInteractionDetails,
    error: interactionDetailsError,
    refetch: refetchJobDetails,
  } = useGetInteractionDetails({
    queryParams: {
      name: interactionName,
    },
  });

  const VALID_TABS = ["overview", "analysis", "sessions"];
  const initialTab = VALID_TABS.includes(searchParams.get("tab") || "")
    ? searchParams.get("tab")
    : "overview";
  const [activeTab, setActiveTab] = useState<string | null>(initialTab);

  useEffect(() => {
    initializeFromUrlParams(searchParams);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  // Show skeleton loading state while fetching interaction details
  if (fetchingInteractionDetails) {
    return (
      <div className={classes.criticalInteractionDetailsContainer}>
        {/* Header skeleton */}
        <div className={classes.criticalInteractionDetailsHeader}>
          <div className={classes.criticalInteractionDetailsHeaderContent}>
            <SkeletonLoader height={18} width={18} radius="sm" />
            <div className={classes.titleSection}>
              <SkeletonLoader height={20} width={200} radius="sm" />
              <SkeletonLoader height={14} width={300} radius="sm" />
            </div>
          </div>
          <div className={classes.headerRightSection}>
            <SkeletonLoader height={32} width={100} radius="md" />
            <SkeletonLoader height={32} width={100} radius="md" />
            <SkeletonLoader height={32} width={150} radius="md" />
          </div>
        </div>
        
        {/* Tab skeleton */}
        <div className={classes.tabsSkeleton}>
          <SkeletonLoader height={36} width={100} radius="sm" />
          <SkeletonLoader height={36} width={80} radius="sm" />
          <SkeletonLoader height={36} width={100} radius="sm" />
        </div>
        
        {/* Content skeleton - graphs layout */}
        <Grid mt="md">
          <Grid.Col span={8.5}>
            <Box style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 'var(--mantine-spacing-md)' }}>
              <GraphCardSkeleton chartHeight={200} metricsCount={3} />
              <GraphCardSkeleton chartHeight={200} metricsCount={3} />
              <Box style={{ gridColumn: '1 / span 2' }}>
                <GraphCardSkeleton chartHeight={200} metricsCount={3} />
              </Box>
            </Box>
          </Grid.Col>
          <Grid.Col span={3.5}>
            <Box style={{ display: 'flex', flexDirection: 'column', gap: 'var(--mantine-spacing-sm)' }}>
              <SkeletonLoader height={60} width="100%" radius="md" />
              <SkeletonLoader height={60} width="100%" radius="md" />
              <SkeletonLoader height={60} width="100%" radius="md" />
            </Box>
          </Grid.Col>
        </Grid>
      </div>
    );
  }

  if (
    !routeParams ||
    (routeParams && routeParams === "*") ||
    routeParamsArray.length < 1
  ) {
    return <AllInteractionDetails />;
  }

  const handBackToListingPage = () => {
    navigate(ROUTES.CRITICAL_INTERACTIONS.path);
  };

  const handleTabChange = (value: string | null) => {
    if (value) {
      setActiveTab(value);
      setSearchParams({ ...Object.fromEntries(searchParams.entries()), tab: value }, { replace: true });
    }
  };
  return (
    <Tabs
      defaultValue="Overview"
      variant="unstyled"
      classNames={classes}
      value={activeTab}
      onChange={handleTabChange}
    >
      <div className={classes.criticalInteractionDetailsContainer}>
        <div className={classes.criticalInteractionDetailsHeader}>
          {/* Left Section - Back Button, Title, Status, Description */}
          <div className={classes.criticalInteractionDetailsHeaderContent}>
            <Tooltip label="Back to interactions">
              <span
                onClick={handBackToListingPage}
                className={classes.backButtonContainer}
              >
                <IconArrowNarrowLeft className={classes.backButton} size={18} />
              </span>
            </Tooltip>
            <div className={classes.titleSection}>
              <div className={classes.titleRow}>
                <Title order={5} className={classes.title}>
                  {interactionName ??
                    CRITICAL_INTERACTION_DETAILS_PAGE_CONSTANTS.CRITICAL_INTERACTION_DETAILS_HEADER}
                </Title>
                {interactionDetailsError && (
                  <Badge color={theme.colors.red[6]} size="xs" variant="light">
                    Error
                  </Badge>
                )}
              </div>
              {interactionDetails?.data?.description && (
                <span className={classes.descriptionText}>
                  {interactionDetails?.data?.description}
                </span>
              )}
            </div>
          </div>

          {/* Right Section - Actions, Filters, Time Picker */}
          <div className={classes.headerRightSection}>
            {!interactionDetailsError && interactionDetails?.data && (
              <>
                <Manage
                  {...interactionDetails?.data}
                  refetchJobDetails={refetchJobDetails}
                />
                <div className={classes.verticalDivider} />
              </>
            )}
            <InteractionDetailsFilters />
            <div className={classes.verticalDivider} />
            <DateTimeRangePicker
              handleTimefilterChange={handleTimeFilterChange}
              selectedQuickTimeFilterIndex={quickTimeRangeFilterIndex ?? 0}
              defaultQuickTimeFilterString={quickTimeRangeString || ""}
              defaultEndTime={selectedTimeFilter?.endDate || endTime}
              defaultStartTime={selectedTimeFilter?.startDate || startTime}
            />
          </div>
        </div>

        <Tabs.List>
          <Tabs.Tab value="overview">Overview</Tabs.Tab>
          <Tabs.Tab value="analysis">Analysis</Tabs.Tab>
          <Tabs.Tab value="sessions">Interactions</Tabs.Tab>
        </Tabs.List>

        <Tabs.Panel value="overview">
          {!startTime || !endTime ? (
            <Grid mt="md">
              <Grid.Col span={8.5}>
                <Box style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 'var(--mantine-spacing-md)' }}>
                  <GraphCardSkeleton chartHeight={200} metricsCount={3} />
                  <GraphCardSkeleton chartHeight={200} metricsCount={3} />
                </Box>
              </Grid.Col>
            </Grid>
          ) : filterValues && interactionDetails?.data ? (
            <div>
              <InteractionDetailsMainContent
                jobDetails={interactionDetails?.data}
                dashboardFilters={filterValues}
                startTime={startTime}
                endTime={endTime}
              />
            </div>
          ) : null}
        </Tabs.Panel>
        <Tabs.Panel value="analysis">
          {!startTime || !endTime ? (
            <Grid mt="md">
              <Grid.Col span={12}>
                <GraphCardSkeleton chartHeight={200} metricsCount={3} />
              </Grid.Col>
            </Grid>
          ) : filterValues ? (
            <Analysis
              interactionName={interactionName}
              dashboardFilters={filterValues}
              startTime={startTime}
              endTime={endTime}
            />
          ) : null}
        </Tabs.Panel>
        <Tabs.Panel value="sessions">
          {!startTime || !endTime ? (
            <Grid mt="md">
              <Grid.Col span={12}>
                <GraphCardSkeleton chartHeight={200} metricsCount={3} />
              </Grid.Col>
            </Grid>
          ) : (
            <ProblematicInteractions
              dashboardFilters={filterValues}
              startTime={startTime}
              endTime={endTime}
              interactionName={interactionName}
            />
          )}
        </Tabs.Panel>
      </div>
    </Tabs>
  );
}
