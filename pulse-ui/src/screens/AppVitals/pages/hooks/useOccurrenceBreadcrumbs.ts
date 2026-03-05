import { useState, useCallback, useEffect, useRef } from "react";
import { useMutation } from "@tanstack/react-query";
import { API_BASE_URL } from "../../../../constants";
import { makeRequest, ApiResponse } from "../../../../helpers/makeRequest";
import { useGetQueryJobStatus } from "../../../../hooks/useSubmitQuery";
import type { SubmitQueryResponse } from "../../../../hooks/useSubmitQuery";

const POLL_INTERVAL_MS = 5000;
const MAX_POLL_ATTEMPTS = 60;

export interface BreadcrumbItem {
  id: string;
  eventName: string;
  screenName: string;
  timestamp: Date;
  relativeMs: number;
  props: Record<string, unknown>;
}

interface BreadcrumbRequest {
  sessionId: string;
  errorTimestamp: string;
}

interface UseOccurrenceBreadcrumbsParams {
  sessionId: string;
  errorTimestamp: Date | null;
  enabled?: boolean;
}

export function useOccurrenceBreadcrumbs({
  sessionId,
  errorTimestamp,
  enabled = true,
}: UseOccurrenceBreadcrumbsParams) {
  const [jobId, setJobId] = useState<string | null>(null);
  const [shouldPoll, setShouldPoll] = useState(false);
  const [breadcrumbs, setBreadcrumbs] = useState<BreadcrumbItem[]>([]);
  const [queryState, setQueryState] = useState({
    isLoading: false,
    isError: false,
    errorMessage: undefined as string | undefined,
  });

  const hasSubmittedRef = useRef(false);
  const lastSessionIdRef = useRef("");
  const pollCountRef = useRef(0);
  const lastProcessedAtRef = useRef(0);
  const isMountedRef = useRef(true);

  useEffect(() => {
    isMountedRef.current = true;
    return () => { isMountedRef.current = false; };
  }, []);

  const submitMutation = useMutation<
    ApiResponse<SubmitQueryResponse>,
    Error,
    BreadcrumbRequest
  >({
    mutationKey: ["SUBMIT_BREADCRUMBS"],
    mutationFn: async (request: BreadcrumbRequest) =>
      makeRequest<SubmitQueryResponse>({
        url: `${API_BASE_URL}/v1/breadcrumbs`,
        init: {
          method: "POST",
          body: JSON.stringify(request),
          headers: { "Content-Type": "application/json" },
        },
      }),
  });

  const { data: jobStatusData, dataUpdatedAt } = useGetQueryJobStatus({
    jobId: jobId || "",
    enabled: shouldPoll && !!jobId,
    refetchInterval: shouldPoll ? POLL_INTERVAL_MS : false,
  });

  const parseResults = useCallback(
    (resultData: Record<string, unknown>[]): BreadcrumbItem[] => {
      if (!resultData || resultData.length === 0 || !errorTimestamp) return [];
      const errorMs = errorTimestamp.getTime();

      return resultData.map((row, idx) => {
        const eventName = String(row.event_name ?? row.eventName ?? "");
        const timestampStr = String(row.timestamp ?? "");
        const screenName = String(row.screen_name ?? row.screenName ?? "");
        const propsRaw = row.props ?? "";

        let parsedProps: Record<string, unknown> = {};
        if (typeof propsRaw === "string" && propsRaw) {
          try { parsedProps = JSON.parse(propsRaw); } catch { /* not valid JSON */ }
        } else if (typeof propsRaw === "object" && propsRaw !== null) {
          parsedProps = propsRaw as Record<string, unknown>;
        }

        const ts = new Date(timestampStr.includes("T") ? timestampStr : timestampStr.replace(" ", "T") + "Z");
        const relativeMs = ts.getTime() - errorMs;

        return {
          id: `bc-${idx}`,
          eventName,
          screenName,
          timestamp: ts,
          relativeMs,
          props: parsedProps,
        };
      });
    },
    [errorTimestamp],
  );

  const handleSubmitResponse = useCallback(
    (response: ApiResponse<SubmitQueryResponse>) => {
      if (!isMountedRef.current) return;

      if (response.error || !response.data) {
        setQueryState({
          isLoading: false,
          isError: true,
          errorMessage: response.error?.message || "Failed to fetch breadcrumbs",
        });
        return;
      }

      const data = response.data;

      if (data.status === "COMPLETED" && data.resultData) {
        setBreadcrumbs(parseResults(data.resultData));
        setQueryState({ isLoading: false, isError: false, errorMessage: undefined });
      } else if (data.status === "COMPLETED" && !data.resultData) {
        setBreadcrumbs([]);
        setQueryState({ isLoading: false, isError: false, errorMessage: undefined });
      } else if (data.status === "FAILED" || data.status === "CANCELLED") {
        setQueryState({
          isLoading: false,
          isError: true,
          errorMessage: data.message || "Breadcrumb query failed",
        });
      } else {
        setJobId(data.jobId);
        pollCountRef.current = 0;
        lastProcessedAtRef.current = 0;
        setShouldPoll(true);
      }
    },
    [parseResults],
  );

  useEffect(() => {
    if (!shouldPoll || !jobId) return;
    if (!dataUpdatedAt || dataUpdatedAt === lastProcessedAtRef.current) return;
    lastProcessedAtRef.current = dataUpdatedAt;

    if (!jobStatusData?.data) return;
    const data = jobStatusData.data;

    pollCountRef.current += 1;
    if (pollCountRef.current >= MAX_POLL_ATTEMPTS) {
      setShouldPoll(false);
      setQueryState({
        isLoading: false,
        isError: true,
        errorMessage: "Breadcrumb query timed out",
      });
      return;
    }

    if (data.status === "COMPLETED") {
      setShouldPoll(false);
      if (data.resultData) {
        setBreadcrumbs(parseResults(data.resultData));
      }
      setQueryState({ isLoading: false, isError: false, errorMessage: undefined });
    } else if (data.status === "FAILED") {
      setShouldPoll(false);
      setQueryState({
        isLoading: false,
        isError: true,
        errorMessage: data.errorMessage || "Breadcrumb query failed",
      });
    } else if (data.status === "CANCELLED") {
      setShouldPoll(false);
      setQueryState({
        isLoading: false,
        isError: false,
        errorMessage: "Breadcrumb query was cancelled",
      });
    }
  }, [dataUpdatedAt, shouldPoll, jobId, jobStatusData, parseResults]);

  const isReady = enabled && !!sessionId && !!errorTimestamp;

  useEffect(() => {
    if (!isReady || !errorTimestamp) return;
    if (hasSubmittedRef.current && lastSessionIdRef.current === sessionId) return;

    hasSubmittedRef.current = true;
    lastSessionIdRef.current = sessionId;

    setBreadcrumbs([]);
    setJobId(null);
    setShouldPoll(false);
    pollCountRef.current = 0;
    lastProcessedAtRef.current = 0;

    setQueryState({ isLoading: true, isError: false, errorMessage: undefined });

    submitMutation.mutate(
      { sessionId, errorTimestamp: errorTimestamp.toISOString() },
      {
        onSuccess: handleSubmitResponse,
        onError: (error: Error) => {
          if (!isMountedRef.current) return;
          setQueryState({
            isLoading: false,
            isError: true,
            errorMessage: error.message || "Failed to fetch breadcrumbs",
          });
        },
      },
    );
  }, [isReady, sessionId, errorTimestamp, submitMutation, handleSubmitResponse]);

  const resetForNewOccurrence = useCallback(() => {
    hasSubmittedRef.current = false;
    setShouldPoll(false);
    setJobId(null);
    setBreadcrumbs([]);
    setQueryState({ isLoading: false, isError: false, errorMessage: undefined });
  }, []);

  return {
    breadcrumbs,
    queryState,
    resetForNewOccurrence,
  };
}
