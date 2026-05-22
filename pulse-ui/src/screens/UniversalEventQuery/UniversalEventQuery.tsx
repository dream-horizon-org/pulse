import {
  Box,
  Button,
  Divider,
  Flex,
  Grid,
  Title,
  useMantineTheme,
} from "@mantine/core";
import Editor, { EditorProps } from "@monaco-editor/react";
import { IconCheck, IconPlayerPlay, IconPlayerStop } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { ApiResponse } from "../../helpers/makeRequest";

import { useRef } from "react";
import {
  handleNotification,
  NotificationTypes,
} from "../../helpers/handleNotification/handleNotification";
import {
  GetQueryIdErrorResponse,
  GetQueryIdSuccessResponseBody,
  useRunUniversalQuery,
} from "../../hooks/useRunUniversalQuery";
import {
  QueryValidationErrorResponse,
  QueryValidationSuccessResponseBody,
  useValidateUniversalQuery,
} from "../../hooks/useValidateUniversalQuery";
import { hasResponseError } from "../../utils/network";
import { TablesSidebar } from "./components/TablesSidebar/TablesSidebar";
import { UNIVERSAL_QUERY_TEXTS } from "./UniversalEventQuery.constants";
import { getCookies } from "../../helpers/cookies";
import { COOKIES_KEY } from "../../constants";
import { useGetQueryResultFromQueryId } from "../../hooks/useQueryResultFromQueryId";
import {
  RunUniversalQueryErrorResponse,
  RunUniversalQuerySuccessResponseBody,
} from "../../hooks/useQueryResultFromQueryId/useQueryResultFromQueryId.interface";
import { QueryResults } from "./components/QueryResults";
import classes from "./UniversalEventQuery.module.css";
import { useCancelQuery } from "../../hooks/useCancelQuery";
import { CancelQueryResponseType } from "../../hooks/useCancelQuery/useCancelQuery.interface";
import { logEvent } from "../../helpers/googleAnalytics";
import { QueryHistory } from "./components/QueryHistory";
import { SuggestedQueries } from "./components/SuggestedQueries";

export const UniversalEventQuery = () => {
  const editorRef = useRef<any>(null);
  const [queryRequestId, setQueryRequestId] = useState<string | null>(null);
  const [isValidQuery, setIsValidQuery] = useState<boolean>(false);

  const insertTextAtCursor = (text: string) => {
    const editor = editorRef.current;
    if (!editor) return;

    const selection = editor.getSelection();
    const model = editor.getModel();

    if (!selection || !model) return;

    const cursorIndex = model.getOffsetAt(selection.getStartPosition());
    const newQuery =
      query.slice(0, cursorIndex) + " " + text + " " + query.slice(cursorIndex);

    setQuery(newQuery);

    editor.setValue(newQuery);

    editor.setPosition({
      lineNumber: selection.startLineNumber,
      column: selection.startColumn + text.length + 1,
    });

    editor.focus();
  };

  const theme = useMantineTheme();
  const [query, setQuery] = useState<string>(
    `
    -- NOTE: Following columns have been updated in processed_events_partitioned_hourly table
    -- 1. appVersion -> app_version
    -- 2. networkProvider -> network_provider
    -- 3. osVersion -> os_version
    -- 4. eventProps -> props
    
    SELECT eventName FROM d11_stream_analytics_multi_region.processed_events_partitioned_hourly where eventTimestamp BETWEEN TIMESTAMP_SUB(CURRENT_TIMESTAMP(), interval 5 minute) AND CURRENT_TIMESTAMP() LIMIT 100;`,
  );
  const [pageToken, setPageToken] = useState<string | null>(null);
  const [data, setData] = useState<RunUniversalQuerySuccessResponseBody>();
  const intervalRef = useRef<any>();
  const [isRunningQuery, setIsRunningQuery] = useState(false);
  const handleQueryValidationSucess = (
    response: ApiResponse<QueryValidationSuccessResponseBody>,
  ) => {
    if (response?.data?.errorMessage) {
      return handleNotification({
        type: NotificationTypes.ERROR,
        message:
          response?.data?.errorMessage ??
          UNIVERSAL_QUERY_TEXTS.QUERY_VALIDATION_ERROR,
        description:
          response.error?.cause ??
          "An error occurred while validating the query",
        theme,
      });
    }

    if (!response.data?.success) {
      return handleNotification({
        type: NotificationTypes.ERROR,
        message: UNIVERSAL_QUERY_TEXTS.QUERY_INVALID,
        description: "The SQL query is invalid!",
        theme,
      });
    }

    handleNotification({
      type: NotificationTypes.SUCCESS,
      message: UNIVERSAL_QUERY_TEXTS.QUERY_VALIDATION_SUCCESS,
      description: "The SQL query is valid!",
      theme,
    });

    setIsValidQuery(true);
  };

  const handleQueryValidationError = (error: QueryValidationErrorResponse) => {
    handleNotification({
      type: NotificationTypes.ERROR,
      message: error.error?.message ?? UNIVERSAL_QUERY_TEXTS.VALIDATION_FAILED,
      description:
        error.error?.cause ??
        UNIVERSAL_QUERY_TEXTS.VALIDATION_FAILED_DESCRIPTION,
      theme,
    });
  };

  const handleRunQuerySuccess = (
    response: ApiResponse<GetQueryIdSuccessResponseBody>,
  ) => {
    if (hasResponseError(response) || response.error?.message) {
      setIsRunningQuery(false);
      return handleNotification({
        type: NotificationTypes.ERROR,
        message:
          response.error?.message ??
          UNIVERSAL_QUERY_TEXTS.QUERY_EXECUTION_ERROR,
        description:
          response.error?.cause ??
          UNIVERSAL_QUERY_TEXTS.VALIDATION_FAILED_DESCRIPTION,
        theme,
      });
    }

    setQueryRequestId(response.data?.requestId ?? null);
  };

  useEffect(() => {
    if (queryRequestId) {
      intervalRef.current = setInterval(() => {
        getResults({ requestId: queryRequestId, pageToken: pageToken || "" });
      }, 6000);

      return () => clearInterval(intervalRef.current);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queryRequestId]);

  const handleRunQueryError = (error: GetQueryIdErrorResponse) => {
    handleNotification({
      type: NotificationTypes.ERROR,
      message:
        error.error?.message ?? UNIVERSAL_QUERY_TEXTS.QUERY_EXECUTION_FAILED,
      description: error.error?.cause ?? "Failed to execute query.",
      theme,
    });
  };

  const handleGetQueryResultError = (error: RunUniversalQueryErrorResponse) => {
    if (error.jobComplete) {
      if (error?.totalRows === 0) {
        handleNotification({
          type: NotificationTypes.ERROR,
          message: "No data found",
          description: "No data found for the query",
          theme,
        });
      }
    } else {
      handleNotification({
        type: NotificationTypes.ERROR,
        message:
          error.error?.message ?? UNIVERSAL_QUERY_TEXTS.QUERY_EXECUTION_FAILED,
        description: error.error?.cause ?? "Failed to execute query.",
        theme,
      });
    }
    clearInterval(intervalRef.current);
    setPageToken(null);
    setIsRunningQuery(false);
    setIsValidQuery(false);
  };

  const handleCancelQuerySuccess = (
    response: ApiResponse<CancelQueryResponseType>,
  ) => {
    if (response.data?.success) {
      handleNotification({
        type: NotificationTypes.SUCCESS,
        message: "Query cancelled successfully",
        description: "",
        theme,
      });

      clearInterval(intervalRef.current);
      setIsRunningQuery(false);
      setPageToken(null);
      setQueryRequestId(null);
      setData(undefined);
    }
  };

  const handleCancelQueryError = (error: { error: { message: string; cause?: string } }) => {
    if (error.error) {
      handleNotification({
        type: NotificationTypes.ERROR,
        message:
          error.error?.message ?? UNIVERSAL_QUERY_TEXTS.QUERY_EXECUTION_FAILED,
        description: error.error?.cause ?? "Failed to cancel query.",
        theme,
      });
    }
  };

  const { mutate: validateQuery, isPending: isValidatingQuery } =
    useValidateUniversalQuery({
      onSuccess: handleQueryValidationSucess,
      onError: handleQueryValidationError,
    });

  const { mutate: cancelQuery, isPending: isCancelQueryPending } =
    useCancelQuery({
      onSuccess: handleCancelQuerySuccess,
      onError: handleCancelQueryError,
    });

  const { mutate: runQuery } = useRunUniversalQuery({
    query,
    emailId: `${getCookies(COOKIES_KEY.USER_EMAIL)}`,
    onSuccess: handleRunQuerySuccess,
    onError: handleRunQueryError,
  });

  const handleGetQueryResultSuccess = (
    response: ApiResponse<RunUniversalQuerySuccessResponseBody>,
  ) => {
    if (!response) return;

    if (!response.data?.jobComplete) {
      return;
    }

    if (response.data?.totalRows === 0) {
      handleNotification({
        type: NotificationTypes.ERROR,
        message: "No data found",
        description: "No data found for the query",
        theme,
      });
    }

    clearInterval(intervalRef.current);
    setData({
      rows: [
        ...(data?.pageToken ? data?.rows || [] : []),
        ...(response.data?.rows || []),
      ],
      schema: response.data?.schema,
      totalRows: response.data?.totalRows,
      pageToken: response.data?.pageToken,
      jobComplete: response.data?.jobComplete,
    });
    setPageToken(response?.data?.pageToken || null);
    setIsRunningQuery(false);
    setIsValidQuery(false);
  };

  const { mutate: getResults } = useGetQueryResultFromQueryId({
    requestId: queryRequestId ?? "",
    pageToken: pageToken ?? "",
    onSuccess: handleGetQueryResultSuccess,
    onError: handleGetQueryResultError,
  });

  const queryChangeHandler: EditorProps["onChange"] = (value) => {
    setQuery(value ?? "");
    setIsValidQuery(false);
  };

  const disableAction = !query.length || isRunningQuery || isValidatingQuery;

  const handleCancelQuery = () => {
    if (queryRequestId) cancelQuery({ jobId: queryRequestId });
  };

  const handleExecuteQuery = () => {
    setPageToken(null);
    setQueryRequestId(null);
    setData(undefined);
    setIsRunningQuery(true);
    runQuery({ query, emailId: `${getCookies(COOKIES_KEY.USER_EMAIL)}` });
    logEvent(UNIVERSAL_QUERY_TEXTS.EXECUTE_QUERY, UNIVERSAL_QUERY_TEXTS.TITLE);
  };

  const fetchMoreData = () => {
    setIsRunningQuery(true);
    getResults({ requestId: queryRequestId || "", pageToken: pageToken || "" });
  };

  const handleValidateQuery = () => {
    validateQuery({ query });
    logEvent(UNIVERSAL_QUERY_TEXTS.VALIDATE_QUERY, UNIVERSAL_QUERY_TEXTS.TITLE);
  };

  return (
    <Box>
      <Grid>
        <Grid.Col span={{ base: 12, md: 4 }} className={classes.titleContainer}>
          <Title order={4}>{UNIVERSAL_QUERY_TEXTS.TITLE}</Title>
        </Grid.Col>
        <Grid.Col span={{ base: 12, md: 8 }}>
          <Flex gap={"sm"} justify={{ md: "flex-end" }}>
            <QueryHistory />
            <SuggestedQueries />
            <Button
              leftSection={<IconCheck size={20} />}
              onClick={handleValidateQuery}
              loading={isValidatingQuery}
              disabled={disableAction}
              variant="outline"
            >
              {UNIVERSAL_QUERY_TEXTS.VALIDATE_QUERY}
            </Button>
            {isRunningQuery && queryRequestId ? (
              <Button
                leftSection={<IconPlayerStop size={20} />}
                onClick={handleCancelQuery}
                disabled={!isRunningQuery}
                loading={isCancelQueryPending}
              >
                {UNIVERSAL_QUERY_TEXTS.CANCEL_QUERY}
              </Button>
            ) : (
              <Button
                leftSection={<IconPlayerPlay size={20} />}
                onClick={handleExecuteQuery}
                loading={isRunningQuery}
                disabled={!isValidQuery}
              >
                {UNIVERSAL_QUERY_TEXTS.EXECUTE_QUERY}
              </Button>
            )}
          </Flex>
        </Grid.Col>
      </Grid>
      <Divider my="md" />

      <div className={classes.editorContainer}>
        <TablesSidebar onInsert={insertTextAtCursor}></TablesSidebar>
        <Editor
          height="300px"
          defaultLanguage="sql"
          value={query}
          onChange={queryChangeHandler}
          onMount={(editor) => (editorRef.current = editor)}
          options={{
            minimap: { enabled: false },
            fontSize: 14,
            wordWrap: "on",
            automaticLayout: true,
          }}
          className={classes.editor}
        />
      </div>
      <Divider my="md" />

      <QueryResults
        data={data}
        isLoadingData={isRunningQuery}
        fetchMoreData={fetchMoreData}
      ></QueryResults>
    </Box>
  );
};
