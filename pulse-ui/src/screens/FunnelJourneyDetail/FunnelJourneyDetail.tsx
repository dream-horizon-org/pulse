import {
  ActionIcon,
  Badge,
  Box,
  Group,
  Loader,
  Paper,
  SimpleGrid,
  Text,
  Title,
} from "@mantine/core";
import { IconArrowLeft, IconChartFunnel, IconRoute } from "@tabler/icons-react";
import { generatePath, useNavigate, useParams } from "react-router-dom";
import dayjs from "dayjs";
import ReactECharts from "echarts-for-react";
import { useMemo } from "react";
import { ROUTES } from "../../constants";
import { useGetFunnelJourneyDetail } from "../../hooks/useGetFunnelJourneyDetail";
import { ErrorAndEmptyState } from "../../components/ErrorAndEmptyState";
import {
  useGetFunnelData,
  useGetFunnelTrend,
  useGetJourneyData,
} from "../../hooks/useGetFunnelData";
import type { FunnelStep } from "../../hooks/useGetFunnelData";
import { getDateRangeFromPreset } from "../FunnelAnalysis/mockData";
import { FunnelVisualization } from "../FunnelAnalysis/components/FunnelVisualization";
import { FunnelDataTable } from "../FunnelAnalysis/components/FunnelDataTable";
import { buildJourneySankeyOption } from "../FunnelAnalysis/utils/buildJourneySankeyOption";
import funnelClasses from "../FunnelAnalysis/FunnelAnalysis.module.css";
import {
  BACK_TO_LIST,
  NOT_FOUND_DESCRIPTION,
  NOT_FOUND_TITLE,
} from "./FunnelJourneyDetail.constants";
import classes from "./FunnelJourneyDetail.module.css";

const MOCK_FUNNEL_STEP_EVENT_NAMES = [
  "Screen_View: Home",
  "Screen_View: Product Detail",
  "Tap: Add to Cart",
  "Tap: Checkout",
  "Tap: Place Order",
] as const;

const MOCK_JOURNEY_ANCHOR_EVENT = "Screen_View: Home";

function FunnelDetailView() {
  const timeRange = useMemo(() => getDateRangeFromPreset("7d"), []);

  const apiSteps: FunnelStep[] = useMemo(
    () =>
      MOCK_FUNNEL_STEP_EVENT_NAMES.map((eventName) => ({
        eventName,
        dataType: "LOGS" as const,
      })),
    [],
  );

  const requestBody = useMemo(
    () => ({
      steps: apiSteps,
      timeRange,
      mode: "UNIQUE_USERS" as const,
      windowSeconds: 86400,
    }),
    [apiSteps, timeRange],
  );

  const { data: funnelRes, isLoading: funnelLoading } = useGetFunnelData({
    requestBody,
    enabled: true,
  });

  const { data: trendRes, isLoading: trendLoading } = useGetFunnelTrend({
    requestBody,
    enabled: true,
  });

  const funnelResult = funnelRes?.data;
  const trendResult = trendRes?.data;
  const isLoading = funnelLoading || trendLoading;

  return (
    <Box className={funnelClasses.mainCanvas} style={{ padding: 0 }}>
      {isLoading && (
        <Box className={funnelClasses.emptyState}>
          <Loader color="teal" size="lg" />
          <Text size="sm" c="dimmed" mt="md">
            Loading funnel visualization…
          </Text>
        </Box>
      )}

      {!isLoading && funnelResult?.steps?.length ? (
        <>
          <FunnelVisualization
            steps={funnelResult.steps}
            totalConversionRate={
              trendResult?.totalConversionRate ??
              funnelResult.overallConversionRate
            }
            conversionTrend={trendResult?.conversionTrend ?? 0}
            medianTimes={trendResult?.medianTimes ?? []}
          />
          <FunnelDataTable
            steps={funnelResult.steps}
            timeRange={timeRange}
            apiSteps={apiSteps}
          />
        </>
      ) : null}

      {!isLoading && !funnelResult?.steps?.length ? (
        <Box className={funnelClasses.emptyState}>
          <Text size="sm" c="dimmed">
            Funnel data could not be loaded.
          </Text>
        </Box>
      ) : null}
    </Box>
  );
}

function JourneyDetailView() {
  const timeRange = useMemo(() => getDateRangeFromPreset("7d"), []);

  const requestBody = useMemo(
    () => ({
      direction: "forward" as const,
      anchorEvent: MOCK_JOURNEY_ANCHOR_EVENT,
      depth: 5,
      timeRange,
    }),
    [timeRange],
  );

  const { data, isLoading } = useGetJourneyData({
    requestBody,
    enabled: true,
  });

  const journeyData = data?.data;

  return (
    <Box className={funnelClasses.journeyLayout} style={{ minHeight: 560 }}>
      <Box className={funnelClasses.journeyCanvas} style={{ padding: 0 }}>
        <Box className={funnelClasses.sankeyContainer}>
          <Text size="sm" fw={600} c="dark.7" mb="md">
            Forward journey from{" "}
            <Text span c="teal" fw={700}>
              {MOCK_JOURNEY_ANCHOR_EVENT}
            </Text>{" "}
            (preview · depth 5)
          </Text>

          {isLoading ? (
            <Box
              style={{ display: "flex", justifyContent: "center", padding: 80 }}
            >
              <Loader color="teal" size="lg" />
            </Box>
          ) : journeyData ? (
            <ReactECharts
              option={buildJourneySankeyOption(journeyData)}
              style={{ height: "520px", width: "100%" }}
              notMerge
            />
          ) : (
            <Box className={funnelClasses.emptyState}>
              <Text size="sm" c="dimmed">
                Journey data could not be loaded.
              </Text>
            </Box>
          )}
        </Box>
      </Box>
    </Box>
  );
}

export function FunnelJourneyDetail() {
  const navigate = useNavigate();
  const { projectId, id } = useParams<{ projectId: string; id: string }>();
  const { data: apiResponse, isLoading, error } = useGetFunnelJourneyDetail(id);
  const detail = apiResponse?.data ?? null;
  const isNotFound = apiResponse?.status === 404;
  const failMessage =
    apiResponse?.error?.message ||
    (error instanceof Error ? error.message : NOT_FOUND_TITLE);

  const goBack = () => {
    if (projectId) {
      navigate(generatePath(ROUTES.FUNNEL_ANALYSIS.path, { projectId }));
      return;
    }
    navigate(-1);
  };

  if (isLoading) {
    return (
      <Box className={classes.shell}>
        <Group justify="center" py={80}>
          <Loader color="teal" />
        </Group>
      </Box>
    );
  }

  if (!detail) {
    return (
      <Box className={classes.shell}>
        <Group mb="md">
          <ActionIcon variant="subtle" color="gray" onClick={goBack} size="lg">
            <IconArrowLeft size={20} />
          </ActionIcon>
          <Text size="sm" c="dimmed">
            {BACK_TO_LIST}
          </Text>
        </Group>
        <ErrorAndEmptyState
          message={failMessage}
          description={isNotFound ? NOT_FOUND_DESCRIPTION : undefined}
        />
      </Box>
    );
  }

  const KindIcon = detail.kind === "FUNNEL" ? IconChartFunnel : IconRoute;

  return (
    <Box className={classes.shell}>
      <Box className={classes.header}>
        <ActionIcon variant="subtle" color="gray" onClick={goBack} size="lg">
          <IconArrowLeft size={20} />
        </ActionIcon>
        <Box className={classes.titleBlock}>
          <Group gap="sm" wrap="wrap" align="center">
            <Title order={2} size="h3" c="dark.7">
              {detail.name}
            </Title>
            <Badge
              variant="light"
              color="gray"
              leftSection={<KindIcon size={12} />}
            >
              {detail.kind === "FUNNEL" ? "Funnel" : "Journey"}
            </Badge>
            <Badge
              color={
                detail.status === "ACTIVE"
                  ? "teal"
                  : detail.status === "CREATING"
                    ? "blue"
                    : "gray"
              }
              variant="light"
            >
              {detail.status === "ACTIVE"
                ? "Active"
                : detail.status === "CREATING"
                  ? "Creating"
                  : "Stopped"}
            </Badge>
          </Group>
          <Text size="sm" c="dimmed" mt={8}>
            {detail.description}
          </Text>
        </Box>
      </Box>

      <Paper p="lg" className={classes.paper}>
        <SimpleGrid cols={{ base: 1, sm: 2, md: 3 }} spacing="lg">
          <Box>
            <Text className={classes.metaLabel}>Created by</Text>
            <Text size="sm">{detail.createdBy}</Text>
          </Box>
          <Box>
            <Text className={classes.metaLabel}>Created</Text>
            <Text size="sm" c="dimmed">
              {dayjs(detail.createdAt).format("MMM D, YYYY HH:mm")}
            </Text>
          </Box>
          <Box>
            <Text className={classes.metaLabel}>Last updated</Text>
            <Text size="sm" c="dimmed">
              {dayjs(detail.lastUpdatedAt).format("MMM D, YYYY HH:mm")}
            </Text>
          </Box>
          {detail.kind === "FUNNEL" && detail.funnelType && (
            <Box>
              <Text className={classes.metaLabel}>Funnel type</Text>
              <Text size="sm">
                {detail.funnelType === "ORDERED" ? "Ordered" : "Unordered"}
              </Text>
            </Box>
          )}
          <Box style={{ gridColumn: "1 / -1" }}>
            <Text className={classes.metaLabel}>Tags</Text>
            <Group gap="xs" mt={4}>
              {detail.tags.length ? (
                detail.tags.map((t) => (
                  <Badge key={t} size="sm" variant="outline" color="teal">
                    {t}
                  </Badge>
                ))
              ) : (
                <Text size="sm" c="dimmed">
                  None
                </Text>
              )}
            </Group>
          </Box>
        </SimpleGrid>

        <Box mt="xl">
          {detail.status === "CREATING" ? (
            <Box className={funnelClasses.emptyState} py={60}>
              <Loader color="blue" size="lg" />
              <Text size="lg" fw={700} c="dark.6" mt="md">
                Computing {detail.kind === "FUNNEL" ? "Funnel" : "Journey"} Data
              </Text>
              <Text size="sm" c="dimmed" mt={4} maw={400} ta="center">
                Your {detail.kind === "FUNNEL" ? "funnel" : "journey"} is currently being computed on the server. This might take a few moments. Please check back later.
              </Text>
            </Box>
          ) : detail.kind === "FUNNEL" ? (
            <FunnelDetailView />
          ) : (
            <JourneyDetailView />
          )}
        </Box>
      </Paper>
    </Box>
  );
}
