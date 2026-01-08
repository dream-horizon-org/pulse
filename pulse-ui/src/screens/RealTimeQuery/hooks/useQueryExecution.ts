import { useState, useCallback, useRef, useEffect } from "react";
import { useSubmitQuery, useGetQueryJobStatus } from "../../../hooks";
import { QueryResult, QueryExecutionState } from "../RealTimeQuery.interface";
import { QUERY_POLLING_CONFIG, REALTIME_QUERY_TEXTS } from "../RealTimeQuery.constants";
import { API_BASE_URL } from "../../../constants";
import { makeRequest } from "../../../helpers/makeRequest";
import { GetJobStatusResponse } from "../../../hooks/useSubmitQuery";

interface UseQueryExecutionOptions {
  onSuccess?: (result: QueryResult) => void;
  onError?: (error: string) => void;
}

interface UseQueryExecutionReturn {
  executeQuery: (query: string) => void;
  cancelQuery: () => void;
  loadMore: () => void;
  executionState: QueryExecutionState;
  result: QueryResult | null;
  isLoading: boolean;
  isLoadingMore: boolean;
}

/**
 * Hook to handle query execution with job polling and pagination
 * 
 * Flow:
 * 1. Submit query to backend
 * 2. If response has status COMPLETED with resultData → done
 * 3. If response has status RUNNING/QUEUED → start polling
 * 4. Poll every 5 seconds until COMPLETED, FAILED, or CANCELLED
 * 5. If response has nextToken, user can load more results
 */
export function useQueryExecution(
  options?: UseQueryExecutionOptions
): UseQueryExecutionReturn {
  const [executionState, setExecutionState] = useState<QueryExecutionState>({
    status: "idle",
    jobId: null,
    errorMessage: null,
    pollCount: 0,
  });
  const [result, setResult] = useState<QueryResult | null>(null);
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

  // Update refs when options change
  useEffect(() => {
    onSuccessRef.current = options?.onSuccess;
    onErrorRef.current = options?.onError;
  }, [options?.onSuccess, options?.onError]);

  // Query submission mutation
  const submitMutation = useSubmitQuery({
    onSuccess: (response) => {
      if (!isMountedRef.current) return;

      // Check for error in response
      if (response.error) {
        const errorMessage = response.error?.message || REALTIME_QUERY_TEXTS.SUBMIT_QUERY_ERROR;
        setExecutionState((prev) => ({
          ...prev,
          status: "failed",
          errorMessage,
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
        }));
        onErrorRef.current?.(errorMessage);
        return;
      }

      console.log("[QueryExecution] Submit response:", { 
        status: data.status, 
        jobId: data.jobId,
        hasResultData: !!data.resultData 
      });

      // Check if query completed immediately (within 3 seconds on backend)
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
        onSuccessRef.current?.(processedResult);
      } else if (data.status === "FAILED" || data.status === "CANCELLED") {
        // Query failed or was cancelled
        const errorMessage = data.message || "Query execution failed";
        setExecutionState((prev) => ({
          ...prev,
          status: "failed",
          jobId: data.jobId,
          errorMessage,
        }));
        onErrorRef.current?.(errorMessage);
      } else {
        // Status is RUNNING or QUEUED - need to poll for results
        console.log("[QueryExecution] Starting polling for jobId:", data.jobId);
        pollCountRef.current = 0;
        lastProcessedAtRef.current = 0;
        setExecutionState({
          status: "polling",
          jobId: data.jobId,
          errorMessage: null,
          pollCount: 0,
        });
        setShouldPoll(true);
      }
    },
    onError: (error) => {
      if (!isMountedRef.current) return;
      const errorMessage = error?.error?.message || REALTIME_QUERY_TEXTS.SUBMIT_QUERY_ERROR;
      console.error("[QueryExecution] Submit error:", errorMessage);
      setExecutionState((prev) => ({
        ...prev,
        status: "failed",
        errorMessage,
      }));
      onErrorRef.current?.(errorMessage);
    },
  });

  // Job status polling query
  const { 
    data: jobStatusData, 
    error: jobStatusError,
    dataUpdatedAt,
  } = useGetQueryJobStatus({
    jobId: executionState.jobId || "",
    enabled: shouldPoll && !!executionState.jobId,
    refetchInterval: shouldPoll ? QUERY_POLLING_CONFIG.POLL_INTERVAL_MS : false,
  });

  // Handle job status updates
  useEffect(() => {
    // Early exit conditions
    if (!shouldPoll || !executionState.jobId) return;
    if (!dataUpdatedAt || dataUpdatedAt === lastProcessedAtRef.current) return;
    
    // Mark this response as processed
    lastProcessedAtRef.current = dataUpdatedAt;
    
    // Handle polling error
    if (jobStatusError) {
      console.error("[QueryExecution] Poll error:", jobStatusError);
      // Don't stop polling on transient errors, just log
      return;
    }

    if (!jobStatusData?.data) return;

    const data = jobStatusData.data;
    
    // Increment poll count using ref
    pollCountRef.current += 1;
    const currentPollCount = pollCountRef.current;
    
    console.log("[QueryExecution] Poll response:", { 
      status: data.status, 
      pollCount: currentPollCount,
      hasResultData: !!data.resultData,
      errorMessage: data.errorMessage,
    });

    // Update poll count in state for display purposes
    setExecutionState((prev) => ({
      ...prev,
      pollCount: currentPollCount,
    }));

    // Check for max poll attempts (timeout)
    if (currentPollCount >= QUERY_POLLING_CONFIG.MAX_POLL_ATTEMPTS) {
      console.log("[QueryExecution] Max poll attempts reached, timing out");
      setShouldPoll(false);
      const errorMessage = REALTIME_QUERY_TEXTS.QUERY_TIMEOUT;
      setExecutionState((prev) => ({
        ...prev,
        status: "failed",
        errorMessage,
      }));
      onErrorRef.current?.(errorMessage);
      return;
    }

    // Handle different statuses
    if (data.status === "COMPLETED") {
      console.log("[QueryExecution] Query completed");
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
        onSuccessRef.current?.(processedResult);
      } else {
        // Completed but no results - might happen for empty result sets
        const emptyResult: QueryResult = {
          columns: [],
          rows: [],
          totalRows: 0,
          executionTimeMs: startTimeRef.current ? Date.now() - startTimeRef.current : undefined,
          hasMore: false,
          nextToken: null,
        };
        setResult(emptyResult);
        setExecutionState((prev) => ({
          ...prev,
          status: "completed",
        }));
        onSuccessRef.current?.(emptyResult);
      }
    } else if (data.status === "FAILED") {
      console.log("[QueryExecution] Query failed:", data.errorMessage);
      setShouldPoll(false);
      const errorMessage = data.errorMessage || "Query execution failed";
      setExecutionState((prev) => ({
        ...prev,
        status: "failed",
        errorMessage,
      }));
      onErrorRef.current?.(errorMessage);
    } else if (data.status === "CANCELLED") {
      console.log("[QueryExecution] Query was cancelled");
      setShouldPoll(false);
      setExecutionState((prev) => ({
        ...prev,
        status: "cancelled",
        errorMessage: "Query was cancelled",
      }));
    } else {
      // RUNNING or QUEUED - continue polling
      console.log("[QueryExecution] Query still running, poll count:", currentPollCount);
    }
  }, [dataUpdatedAt, shouldPoll, executionState.jobId, jobStatusData, jobStatusError]);

  // Cleanup on unmount
  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  const executeQuery = useCallback(
    (query: string) => {
      if (!query.trim()) return;

      console.log("[QueryExecution] Executing query:", query.substring(0, 100) + "...");

      // Reset state
      setResult(null);
      setShouldPoll(false);
      pollCountRef.current = 0;
      lastProcessedAtRef.current = 0;
      startTimeRef.current = Date.now();

      setExecutionState({
        status: "submitting",
        jobId: null,
        errorMessage: null,
        pollCount: 0,
      });

      // Submit the query
      submitMutation.mutate({
        queryString: query,
      });
    },
    [submitMutation]
  );

  const cancelQuery = useCallback(() => {
    console.log("[QueryExecution] Query cancelled by user");
    setShouldPoll(false);
    setExecutionState((prev) => ({
      ...prev,
      status: "cancelled",
      errorMessage: "Query cancelled by user",
    }));
  }, []);

  /**
   * Load more results using the nextToken
   */
  const loadMore = useCallback(async () => {
    if (!result?.nextToken || !executionState.jobId || isLoadingMore) {
      console.log("[QueryExecution] Cannot load more:", { 
        hasNextToken: !!result?.nextToken, 
        hasJobId: !!executionState.jobId,
        isLoadingMore 
      });
      return;
    }

    console.log("[QueryExecution] Loading more results with nextToken");
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
        console.error("[QueryExecution] Load more error:", response.error);
        onErrorRef.current?.(response.error.message || "Failed to load more results");
        return;
      }

      const data = response.data;
      if (!data || !data.resultData) {
        console.log("[QueryExecution] No more data to load");
        return;
      }

      console.log("[QueryExecution] Loaded more results:", {
        newRowsCount: data.resultData.length,
        hasMoreNextToken: !!data.nextToken,
      });

      // Append new rows to existing result
      setResult((prev) => {
        if (!prev) return prev;
        
        const newRows = data.resultData as Record<string, string | number | boolean | null>[];
        
        return {
          ...prev,
          rows: [...prev.rows, ...newRows],
          totalRows: prev.totalRows + newRows.length,
          hasMore: !!data.nextToken,
          nextToken: data.nextToken,
          dataScannedInBytes: data.dataScannedInBytes ?? prev.dataScannedInBytes,
        };
      });
    } catch (error) {
      console.error("[QueryExecution] Load more exception:", error);
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
    executeQuery,
    cancelQuery,
    loadMore,
    executionState,
    result,
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

  // Extract columns from first row
  const firstRow = resultData[0];
  const columns = Object.keys(firstRow).map((key) => ({
    name: key,
    type: inferType(firstRow[key]),
  }));

  // Calculate execution time
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

/**
 * Infer data type from value for display purposes
 */
function inferType(value: unknown): string {
  if (value === null || value === undefined) return "string";
  if (typeof value === "number") return "number";
  if (typeof value === "boolean") return "boolean";
  if (value instanceof Date) return "date";
  return "string";
}
