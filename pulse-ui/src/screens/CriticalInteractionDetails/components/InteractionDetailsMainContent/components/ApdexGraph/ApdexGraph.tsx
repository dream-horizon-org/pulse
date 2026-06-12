import classes from "../InteractionDetailsGraphs/InteractionDetailsGraphs.module.css";
import apdexGraphClasses from "./ApdexGraph.module.css";
import { useEffect, useState } from "react";
import {
  ApdexGraphData,
  DateTimeMap,
  GraphDataProps,
} from "./ApdexGraph.interface";
import { DataMapType } from "../../../../../../hooks/useGetApdexScore/useGetApdexScore.interface";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { GraphLoadingAndErrorHandler } from "../GraphLoadingAndErrorHandler";
import { AbsoluteNumbersForGraphs } from "../AbsoluteNumbersForGraphs/AbsoluteNumbersForGraphs";
import { Progress, useMantineTheme } from "@mantine/core";
import { useGetGraphDataFromJobId } from "../../../../../../hooks/useGetGraphDataFromJobId";
import { API_ROUTES, DATE_FORMAT } from "../../../../../../constants";
import { useCachedGetApdexScore } from "../../../../../../hooks/useGetCachedApdexScore";
import { ApdexDataMapType } from "../../../../../../hooks/useGetCachedApdexScore/useCachedGetApdexScore.interface";
import { useCachedErrorRate } from "../../../../../../hooks/useCachedErrorRate";
import { ErrorRateDataMapType } from "../../../../../../hooks/useCachedErrorRate/useCachedErrorRate.interface";
import { LineChart } from "../../../../../../components/Charts";
import {
  createMarkLineConfig,
  createSeriesFromConfig,
} from "../../../../../../components/Charts/utils";

dayjs.extend(utc);

export enum Metric {
  APDEX = "APDEX",
  ERROR_RATE = "ERROR_RATE",
}


export function ApdexGraph({
  useCaseName,
  startTime = "",
  endTime = "",
  filters,
  className,
  metricToShow = [Metric.APDEX, Metric.ERROR_RATE],
  thresholds = [],
  height,
}: GraphDataProps) {
  const theme = useMantineTheme();
  const [graphData, setGraphData] = useState<ApdexGraphData>([]);
  const [dateTimeMap, setDateTimeMap] = useState<DateTimeMap>({});
  const [errorDateTimeMap, setErrorDateTimeMap] = useState<DateTimeMap>({});
  const [jobId, setJobId] = useState<string | undefined>();
  const [errorJobId, setErrorJobId] = useState<string | undefined>();
  const [enableApdexScoreQuery, setEnableApdexScoreQuery] =
    useState<boolean>(true);
  const [enableErrorRateQuery, setEnableErrorRateQuery] =
    useState<boolean>(true);
  const { data: errorData, isFetching: fetchingErrorRate } = useCachedErrorRate(
    {
      requestBody: {
        useCaseId: useCaseName,
        startTime: startTime,
        endTime: endTime,
        appVersion: filters?.APP_VERSION,
        networkProvider: filters?.NETWORK_PROVIDER,
        osVersion: filters?.OS_VERSION,
        platform: filters?.PLATFORM,
        state: filters?.STATE,
      },
      enabled:
        enableErrorRateQuery && metricToShow.includes(Metric.ERROR_RATE),
    },
  );

  // TODO: get the data from here
  const {
    data,
    isLoading,
    isFetching: fetchingApdexScore,
  } = useCachedGetApdexScore({
    requestBody: {
      useCaseId: useCaseName,
      startTime: startTime,
      endTime: endTime,
      appVersion: filters?.APP_VERSION,
      networkProvider: filters?.NETWORK_PROVIDER,
      osVersion: filters?.OS_VERSION,
      platform: filters?.PLATFORM,
      state: filters?.STATE,
    },
    enabled: enableApdexScoreQuery && metricToShow.includes(Metric.APDEX),
  });

  const { data: jobData } = useGetGraphDataFromJobId({
    refetchInterval: 1000,
    jobId: jobId,
    graphDataEndpoint: API_ROUTES.GET_APDEX_SCORE.apiPath,
    requestBody: {
      useCaseId: useCaseName,
      startTime: startTime,
      endTime: endTime,
      appVersion: filters?.APP_VERSION,
      networkProvider: filters?.NETWORK_PROVIDER,
      osVersion: filters?.OS_VERSION,
      platform: filters?.PLATFORM,
      state: filters?.STATE,
    },
  });

  const { data: errorJobData } = useGetGraphDataFromJobId({
    refetchInterval: 1000,
    jobId: errorJobId,
    graphDataEndpoint: API_ROUTES.GET_ERROR_RATE.apiPath,
    requestBody: {
      useCaseId: useCaseName,
      startTime: startTime,
      endTime: endTime,
      appVersion: filters?.APP_VERSION,
      networkProvider: filters?.NETWORK_PROVIDER,
      osVersion: filters?.OS_VERSION,
      platform: filters?.PLATFORM,
      state: filters?.STATE,
    },
  });

  const [error, setError] = useState<string>();
  const [errorRateDataError, setErrorRateDateError] = useState<string>();

  useEffect(() => {
    setEnableApdexScoreQuery(true);
    setJobId(undefined);
    setGraphData([]);
    setDateTimeMap({});
    setErrorDateTimeMap({});
    setErrorJobId(undefined);
    setEnableErrorRateQuery(true);
  }, [filters, startTime, endTime, useCaseName]);

  useEffect(() => {
    if (data?.error) {
      setError(data?.error.message);
    }

    if (!data?.data) return;
    if (error) setError(undefined);
    // setGraphData(formatGraphData(data.data.apdexResults));

    // check for jobId and set it
    if (!data?.data.jobComplete) {
      setJobId(data?.data.jobReference?.jobId);
      setEnableApdexScoreQuery(false);
      return;
    }

    setDateTimeMap(
      formatCachedApdexResponseDataToDateTimeMap(data?.data?.readings || []),
    );

    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  useEffect(() => {
    if (jobData?.error) {
      setError(jobData?.error.message);
      setJobId(undefined);
    }

    if (!jobData?.data) return;
    if (error) {
      setError(undefined);
      setJobId(undefined);
    }
    // setGraphData(formatGraphData(data.data.apdexResults));

    // check for jobId and set it
    if (!jobData?.data.jobComplete) {
      return;
    }

    setJobId(undefined);
    setDateTimeMap(
      formatResponseDataToDateTimeMap(jobData.data.apdexResults, "Apdex"),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobData]);

  useEffect(() => {
    const combinedDateTimes = {
      ...dateTimeMap,
      ...errorDateTimeMap,
    };

    const allDates = Object.keys(combinedDateTimes);

    const mergedGraphData = allDates.map((date) => ({
      date,
      Apdex:
        dateTimeMap[date]?.Apdex ??
        graphData.find((item) => item.date === date)?.Apdex ??
        0,
      ErrorRate:
        errorDateTimeMap[date]?.ErrorRate ??
        graphData.find((item) => item.date === date)?.ErrorRate ??
        0,
    }));

    setGraphData(mergedGraphData);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dateTimeMap, errorDateTimeMap]);

  useEffect(() => {
    if (errorData?.error) {
      setErrorRateDateError(errorData?.error.message);
    }

    if (!errorData?.data) return;
    if (errorRateDataError) setError(undefined);
    // setGraphData(formatErrorRateGraphData(errorData.data.errorInteractionResults));

    if (!errorData?.data.jobComplete) {
      setErrorJobId(errorData?.data.jobReference?.jobId);
      setEnableErrorRateQuery(false);
      return;
    }

    setErrorDateTimeMap(
      formatCachedErrorResponseDataToDateTimeMap(
        errorData?.data?.readings || [],
      ),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [errorData]);

  useEffect(() => {
    if (errorJobData?.error) {
      setError(errorJobData?.error.message);
      setJobId(undefined);
    }

    if (!errorJobData?.data) return;
    if (error) {
      setError(undefined);
      setJobId(undefined);
    }
    // setGraphData(formatGraphData(data.data.apdexResults));

    // check for jobId and set it
    if (!errorJobData?.data.jobComplete) {
      return;
    }

    setErrorJobId(undefined);
    setErrorDateTimeMap(
      formatResponseDataToDateTimeMap(
        errorJobData.data?.errorInteractionResults,
        "ErrorRate",
      ),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [errorJobData]);

  const formatResponseDataToDateTimeMap = (
    data: DataMapType[],
    type: string,
  ) => {
    return (
      data?.reduce((acc, value) => {
        const dateTime = value?.f[0]?.v
          ? new Date(value?.f[0]?.v * 1000)
          : null;

        if (dateTime) {
          const timeString = dayjs(dateTime).format(DATE_FORMAT);
          const existingValue = dateTimeMap?.[timeString] || {};
          acc[timeString] = {
            ...existingValue,
            [type]: value?.f[1]?.v,
          };
        }

        return acc;
      }, {} as DateTimeMap) || dateTimeMap
    );
  };

  const formatCachedErrorResponseDataToDateTimeMap = (
    data: ErrorRateDataMapType[],
  ) => {
    return (
      data?.reduce((acc, value) => {
        const dateTime = value?.timestamp
          ? new Date(value?.timestamp * 1000)
          : null;

        if (dateTime) {
          const timeString = dayjs(dateTime).format(DATE_FORMAT);
          const existingValue = errorDateTimeMap?.[timeString] || {};
          acc[timeString] = {
            ...existingValue,
            ErrorRate: Number(value.errorRate),
          };
        }

        return acc;
      }, {} as DateTimeMap) || errorDateTimeMap
    );
  };

  const formatCachedApdexResponseDataToDateTimeMap = (
    data: ApdexDataMapType[],
  ) => {
    return (
      data?.reduce((acc, value) => {
        const dateTime = value?.timestamp
          ? new Date(value?.timestamp * 1000)
          : null;

        if (dateTime) {
          const timeString = dayjs(dateTime).format(DATE_FORMAT);
          const existingValue = dateTimeMap?.[timeString] || {};
          acc[timeString] = {
            ...existingValue,
            Apdex: Number(value.apdexScore),
          };
        }

        return acc;
      }, {} as DateTimeMap) || {}
    );
  };

  const getAvg = (data: ApdexGraphData, key: string) => {
    if (!data?.length) return 0;

    if (key === Metric.APDEX) {
      return Number(
        data?.reduce((acc, item) => {
          return acc + Number(item.Apdex);
        }, 0) / data.length,
      );
    }

    if (key === Metric.ERROR_RATE)
      return Number(
        data?.reduce((acc, item) => {
          return acc + Number(item.ErrorRate);
        }, 0) / data.length,
      );

    return 0;
  };

  const getGraphTitle = () => {
    if (
      metricToShow.includes(Metric.APDEX) &&
      metricToShow.includes(Metric.ERROR_RATE)
    ) {
      return "Interaction Apdex Score and Failure Rate";
    }
    if (metricToShow.includes(Metric.APDEX)) {
      return "Interaction Apdex Score";
    }
    if (metricToShow.includes(Metric.ERROR_RATE)) {
      return "Failure Rate";
    }
  };

  const getThreshold = (metric: Metric, threshold: number) => {
    let avg =
      graphData?.length > 0
        ? Number((getAvg(graphData, metric) || 0).toFixed(2))
        : 0;
    if (metric === Metric.APDEX) {
      return avg - threshold;
    } else if (metric === Metric.ERROR_RATE) {
      return avg + threshold;
    }

    return threshold;
  };

  const getEChartsSeries = () => {
    const seriesConfigs = [
      {
        metricType: Metric.APDEX,
        name: "Interaction Apdex Score",
        color: theme.colors.blue[6],
        dataKey: Metric.APDEX,
      },
      {
        metricType: Metric.ERROR_RATE,
        name: "Failure Rate",
        color: theme.colors.red[6],
        dataKey: Metric.ERROR_RATE,
      },
    ];

    const getThresholdColorAndLabel = (metric: string) => ({
      label:
        metric === Metric.APDEX
          ? "Interaction Apdex Score threshold"
          : "Failure Rate threshold",
    });

    return createSeriesFromConfig({
      seriesConfigs,
      metricToShow,
      graphData,
      thresholds,
      theme,
      getThresholdColorAndLabel,
      createMarkLineConfig,
      getThreshold,
    });
  };
  return (
    <div className={classes.graphContainer + " " + className}>
      {!isLoading && !jobId && (fetchingApdexScore || fetchingErrorRate) && (
        <Progress
          className={classes.progressBarContainer}
          animated
          striped
          value={100}
          color="blue"
          size="xs"
        />
      )}
      <GraphLoadingAndErrorHandler
        isLoading={isLoading || !!jobId}
        error={error}
        hasNoData={graphData.length === 0}
      />
      <div className={apdexGraphClasses.graphTitleContainer}>
        <div className={classes.graphTitle}>{getGraphTitle()}</div>
      </div>
      <div className={classes.absoluteCardContainer}>
        {metricToShow.includes(Metric.APDEX) && (
          <AbsoluteNumbersForGraphs
            data={`${graphData?.length > 0 ? (getAvg(graphData, Metric.APDEX) || 0).toFixed(2) : 0}`}
            title="Apdex"
            color="blue-6"
          />
        )}
        {metricToShow.includes(Metric.ERROR_RATE) && (
          <AbsoluteNumbersForGraphs
            data={`${graphData?.length > 0 ? ((getAvg(graphData, Metric.ERROR_RATE) || 0) * 100).toFixed(2) : 0}%`}
            title="Failure rate"
            color="red-6"
          />
        )}
      </div>

      <LineChart
        height={220}
        tooltipValueFormatter={(value) => Number(value).toFixed(2)}
        option={{
          xAxis: {
            type: "category",
            data: graphData.map((d) => d.date),
          },
          yAxis: {
            type: "value",
            min: 0,
            max: 1,
            axisLabel: {
              formatter: (value: number) => value.toFixed(2),
            },
            interval: 0.25,
          },
          series: getEChartsSeries(),
        }}
      />
    </div>
  );
}
