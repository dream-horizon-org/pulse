import { useState, useCallback, useRef, useEffect } from "react";
import { useAiQuery, useGetQueryJobStatus, useCancelQuery } from "../../../hooks";
import { QueryResult, QueryExecutionState } from "../RealTimeQuery.interface";
import {
  QUERY_POLLING_CONFIG,
  REALTIME_QUERY_TEXTS,
} from "../RealTimeQuery.constants";
import { API_BASE_URL } from "../../../constants";
import { makeRequest } from "../../../helpers/makeRequest";
import { GetJobStatusResponse } from "../../../hooks/useSubmitQuery";
import { AiInsights } from "../../../hooks/useAiQuery";

interface UseAiQueryExecutionOptions {
  onSuccess?: (result: QueryResult, insights?: AiInsights | null, sourcesAnalyzed?: string[] | null, timeRange?: { start: string; end: string } | null) => void;
  onError?: (error: string) => void;
}

interface UseAiQueryExecutionReturn {
  executeAiQuery: (naturalLanguageQuery: string, context?: string) => void;
  cancelQuery: () => void;
  loadMore: () => void;
  executionState: QueryExecutionState;
  result: QueryResult | null;
  generatedSql: string | null;
  insights: AiInsights | null;
  sourcesAnalyzed: string[] | null;
  timeRange: { start: string; end: string } | null;
  isLoading: boolean;
  isLoadingMore: boolean;
}

/**
 * Hook to handle AI natural language query execution with job polling and pagination.
 *
 * Flow:
 * 1. Submit natural language query to AI endpoint
 * 2. AI converts it to SQL, executes, and returns results
 * 3. If response has status COMPLETED with resultData → done
 * 4. If response has status RUNNING/QUEUED → start polling
 * 5. Poll every 5 seconds until COMPLETED, FAILED, or CANCELLED
 * 6. If response has nextToken, user can load more results
 */
export function useAiQueryExecution(
  options?: UseAiQueryExecutionOptions
): UseAiQueryExecutionReturn {
  const [executionState, setExecutionState] = useState<QueryExecutionState>({
    status: "idle",
    jobId: null,
    errorMessage: null,
    errorCause: null,
    pollCount: 0,
  });
  const [result, setResult] = useState<QueryResult | null>(null);
  const [generatedSql, setGeneratedSql] = useState<string | null>(null);
  const [insights, setInsights] = useState<AiInsights | null>(null);
  const [sourcesAnalyzed, setSourcesAnalyzed] = useState<string[] | null>(null);
  const [timeRange, setTimeRange] = useState<{ start: string; end: string } | null>(null);
  const [shouldPoll, setShouldPoll] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);

  // Store callbacks in refs to avoid stale closures
  const onSuccessRef = useRef(options?.onSuccess);
  const onErrorRef = useRef(options?.onError);

  // Use ref for poll count to avoid dependency issues
  const pollCountRef = useRef(0);

  // Track if component is mounted
  const isMountedRef = useRef(true);
  const startTimeRef = useRef<number | null>(null);

  // Track last processed dataUpdatedAt to avoid processing same response twice
  const lastProcessedAtRef = useRef<number>(0);

  // Refs for insights to pass to onSuccess callback
  const insightsRef = useRef<AiInsights | null>(null);
  const sourcesRef = useRef<string[] | null>(null);
  const timeRangeRef = useRef<{ start: string; end: string } | null>(null);

  // Update refs when options change
  useEffect(() => {
    onSuccessRef.current = options?.onSuccess;
    onErrorRef.current = options?.onError;
  }, [options?.onSuccess, options?.onError]);

  // AI query submission mutation
  const aiMutation = useAiQuery({
    onSuccess: (response) => {
      if (!isMountedRef.current) return;

      // Check for error in response
      if (response.error) {
        const errorMessage =
          response.error?.message || REALTIME_QUERY_TEXTS.AI_QUERY_ERROR;
        const errorCause = response.error?.cause || null;
        setExecutionState((prev) => ({
          ...prev,
          status: "failed",
          errorMessage,
          errorCause,
        }));
        onErrorRef.current?.(errorMessage);
        return;
      }

      const data = response.data;
      if (!data) {
        const errorMessage = "No response data received";
        setExecutionState((prev) => ({
          ...prev,
          status: "failed",
          errorMessage,
          errorCause: null,
        }));
        onErrorRef.current?.(errorMessage);
        return;
      }

      // Store generated SQL if available
      if (data.generatedSql) {
        setGeneratedSql(data.generatedSql);
      }

      // Store insights if available
      if (data.insights) {
        setInsights(data.insights);
        insightsRef.current = data.insights;
      }

      // Store sources analyzed if available
      if (data.sourcesAnalyzed) {
        setSourcesAnalyzed(data.sourcesAnalyzed);
        sourcesRef.current = data.sourcesAnalyzed;
      }

      // Store time range if available
      if (data.timeRange) {
        setTimeRange(data.timeRange);
        timeRangeRef.current = data.timeRange;
      }

      // Check if query completed immediately
      if (data.status === "COMPLETED" && data.resultData) {
        const processedResult = processResultData(
          data.resultData,
          startTimeRef.current,
          data.dataScannedInBytes,
          data.nextToken
        );
        setResult(processedResult);
        setExecutionState((prev) => ({
          ...prev,
          status: "completed",
          jobId: data.jobId,
        }));
        onSuccessRef.current?.(processedResult, insightsRef.current, sourcesRef.current, timeRangeRef.current);
      } else if (data.status === "FAILED" || data.status === "CANCELLED") {
        const errorMessage = data.message || "AI query execution failed";
        setExecutionState((prev) => ({
          ...prev,
          status: "failed",
          jobId: data.jobId,
          errorMessage,
          errorCause: null,
        }));
        onErrorRef.current?.(errorMessage);
      } else {
        // Status is RUNNING or QUEUED - need to poll for results
        pollCountRef.current = 0;
        lastProcessedAtRef.current = 0;
        setExecutionState({
          status: "polling",
          jobId: data.jobId,
          errorMessage: null,
          errorCause: null,
          pollCount: 0,
        });
        setShouldPoll(true);
      }
    },
    onError: (error) => {
      if (!isMountedRef.current) return;
      const errorMessage =
        error?.error?.message || REALTIME_QUERY_TEXTS.AI_QUERY_ERROR;
      const errorCause = error?.error?.cause || null;
      console.error("[AiQueryExecution] Submit error:", errorMessage);
      setExecutionState((prev) => ({
        ...prev,
        status: "failed",
        errorMessage,
        errorCause,
      }));
      onErrorRef.current?.(errorMessage);
    },
  });

  // Job status polling query (reuses the same hook as regular query execution)
  const {
    data: jobStatusData,
    error: jobStatusError,
    dataUpdatedAt,
  } = useGetQueryJobStatus({
    jobId: executionState.jobId || "",
    enabled: shouldPoll && !!executionState.jobId,
    refetchInterval: shouldPoll
      ? QUERY_POLLING_CONFIG.POLL_INTERVAL_MS
      : false,
  });

  // Handle job status updates
  useEffect(() => {
    if (!shouldPoll || !executionState.jobId) return;
    if (!dataUpdatedAt || dataUpdatedAt === lastProcessedAtRef.current) return;

    lastProcessedAtRef.current = dataUpdatedAt;

    if (jobStatusError) {
      console.error("[AiQueryExecution] Poll error:", jobStatusError);
      return;
    }

    if (!jobStatusData?.data) return;

    const data = jobStatusData.data;

    pollCountRef.current += 1;
    const currentPollCount = pollCountRef.current;

    setExecutionState((prev) => ({
      ...prev,
      pollCount: currentPollCount,
    }));

    // Check for max poll attempts (timeout)
    if (currentPollCount >= QUERY_POLLING_CONFIG.MAX_POLL_ATTEMPTS) {
      setShouldPoll(false);
      const errorMessage = REALTIME_QUERY_TEXTS.QUERY_TIMEOUT;
      setExecutionState((prev) => ({
        ...prev,
        status: "failed",
        errorMessage,
        errorCause: null,
      }));
      onErrorRef.current?.(errorMessage);
      return;
    }

    if (data.status === "COMPLETED") {
      setShouldPoll(false);

      if (data.resultData) {
        const processedResult = processResultData(
          data.resultData,
          startTimeRef.current,
          data.dataScannedInBytes,
          data.nextToken
        );
        setResult(processedResult);
        setExecutionState((prev) => ({
          ...prev,
          status: "completed",
        }));
        onSuccessRef.current?.(processedResult, insightsRef.current, sourcesRef.current, timeRangeRef.current);
      } else {
        const emptyResult: QueryResult = {
          columns: [],
          rows: [],
          totalRows: 0,
          executionTimeMs: startTimeRef.current
            ? Date.now() - startTimeRef.current
            : undefined,
          hasMore: false,
          nextToken: null,
        };
        setResult(emptyResult);
        setExecutionState((prev) => ({
          ...prev,
          status: "completed",
        }));
        onSuccessRef.current?.(emptyResult, insightsRef.current, sourcesRef.current, timeRangeRef.current);
      }
    } else if (data.status === "FAILED") {
      setShouldPoll(false);
      const errorMessage = data.errorMessage || "Query execution failed";
      setExecutionState((prev) => ({
        ...prev,
        status: "failed",
        errorMessage,
        errorCause: null,
      }));
      onErrorRef.current?.(errorMessage);
    } else if (data.status === "CANCELLED") {
      setShouldPoll(false);
      setExecutionState((prev) => ({
        ...prev,
        status: "cancelled",
        errorMessage: "Query was cancelled",
        errorCause: null,
      }));
    }
  }, [
    dataUpdatedAt,
    shouldPoll,
    executionState.jobId,
    jobStatusData,
    jobStatusError,
  ]);

  // Cleanup on unmount
  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  const executeAiQuery = useCallback(
    (naturalLanguageQuery: string, context?: string) => {
      if (!naturalLanguageQuery.trim()) return;

      // Reset state
      setResult(null);
      setGeneratedSql(null);
      setInsights(null);
      setSourcesAnalyzed(null);
      setTimeRange(null);
      insightsRef.current = null;
      sourcesRef.current = null;
      timeRangeRef.current = null;
      setShouldPoll(false);
      pollCountRef.current = 0;
      lastProcessedAtRef.current = 0;
      startTimeRef.current = Date.now();

      setExecutionState({
        status: "submitting",
        jobId: null,
        errorMessage: null,
        errorCause: null,
        pollCount: 0,
      });

      // Submit the AI query
      aiMutation.mutate({
        query: naturalLanguageQuery.trim(),
        context: context || undefined,
      });
    },
    [aiMutation]
  );

  // Cancel query mutation
  const cancelMutation = useCancelQuery({
    onSuccess: () => {},
    onError: (error) => {
      console.error("[AiQueryExecution] Cancel API error:", error);
    },
  });

  const cancelQuery = useCallback(() => {
    setShouldPoll(false);

    if (executionState.jobId) {
      cancelMutation.mutate({ jobId: executionState.jobId });
    }

    setExecutionState((prev) => ({
      ...prev,
      status: "cancelled",
      errorMessage: "Query cancelled by user",
      errorCause: null,
    }));
  }, [executionState.jobId, cancelMutation]);

  /**
   * Load more results using the nextToken
   */
  const loadMore = useCallback(async () => {
    if (!result?.nextToken || !executionState.jobId || isLoadingMore) {
      return;
    }

    setIsLoadingMore(true);

    try {
      const params = new URLSearchParams();
      params.append("maxResults", "1000");
      params.append("nextToken", result.nextToken);

      const response = await makeRequest<GetJobStatusResponse>({
        url: `${API_BASE_URL}/query/job/${executionState.jobId}?${params.toString()}`,
        init: {
          method: "GET",
        },
      });

      if (!isMountedRef.current) return;

      if (response.error) {
        console.error("[AiQueryExecution] Load more error:", response.error);
        onErrorRef.current?.(
          response.error.message || "Failed to load more results"
        );
        return;
      }

      const data = response.data;
      if (!data || !data.resultData) {
        return;
      }

      // Append new rows to existing result
      setResult((prev) => {
        if (!prev) return prev;

        const newRows = data.resultData as Record<
          string,
          string | number | boolean | null
        >[];

        return {
          ...prev,
          rows: [...prev.rows, ...newRows],
          totalRows: prev.totalRows + newRows.length,
          hasMore: !!data.nextToken,
          nextToken: data.nextToken,
          dataScannedInBytes:
            data.dataScannedInBytes ?? prev.dataScannedInBytes,
        };
      });
    } catch (error) {
      console.error("[AiQueryExecution] Load more exception:", error);
      onErrorRef.current?.("Failed to load more results");
    } finally {
      if (isMountedRef.current) {
        setIsLoadingMore(false);
      }
    }
  }, [result?.nextToken, executionState.jobId, isLoadingMore]);

  const isLoading =
    executionState.status === "submitting" ||
    executionState.status === "polling";

  return {
    executeAiQuery,
    cancelQuery,
    loadMore,
    executionState,
    result,
    generatedSql,
    insights,
    sourcesAnalyzed,
    timeRange,
    isLoading,
    isLoadingMore,
  };
}

/**
 * Process raw result data from API into display format
 */
function processResultData(
  resultData: Record<string, unknown>[],
  startTime: number | null,
  dataScannedInBytes?: number,
  nextToken?: string | null
): QueryResult {
  if (!resultData || resultData.length === 0) {
    return {
      columns: [],
      rows: [],
      totalRows: 0,
      executionTimeMs: startTime ? Date.now() - startTime : undefined,
      hasMore: false,
      nextToken: null,
    };
  }

  const firstRow = resultData[0];
  const columns = Object.keys(firstRow).map((key) => ({
    name: key,
    type: inferType(firstRow[key]),
  }));

  const executionTimeMs = startTime ? Date.now() - startTime : undefined;

  return {
    columns,
    rows: resultData as Record<string, string | number | boolean | null>[],
    totalRows: resultData.length,
    executionTimeMs,
    dataScannedInBytes,
    hasMore: !!nextToken,
    nextToken,
  };
}

function inferType(value: unknown): string {
  if (value === null || value === undefined) return "string";
  if (typeof value === "number") return "number";
  if (typeof value === "boolean") return "boolean";
  if (value instanceof Date) return "date";
  return "string";
}
