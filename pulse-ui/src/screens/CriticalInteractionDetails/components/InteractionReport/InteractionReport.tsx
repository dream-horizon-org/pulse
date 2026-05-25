import { Alert, Box, Button, Stack, Text, Title } from "@mantine/core";
import { LoaderWithMessage } from "../../../../components/LoaderWithMessage";
import { IconRefresh } from "@tabler/icons-react";
import { useCallback, useState } from "react";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import {
  GET_RCA_JOB_ROUTE,
  POST_INTERACTION_REPORT_ROUTE,
} from "../../../../constants/API";
import { makeRequest } from "../../../../helpers/makeRequest";
import {
  getJobIdFromRcaPostResponse,
  unwrapRcaJobApiBody,
  unwrapRcaReportPostApiBody,
} from "../../../../helpers/rcaResponseUnwrap";
import { isValidRcaDateParam } from "../../../../helpers/rcaRequestUtils";
import { normalizeRcaJobStatus } from "../../../../hooks/useGetRcaReport/useGetRcaReport";
import type {
  RcaJobResponse,
  RcaReportResponse,
} from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";
import { getApiBaseUrl } from "../../../../utils";
import classes from "../RootCause/RootCause.module.css";

const RCA_HTTP_OK = 200;
const RCA_HTTP_ACCEPTED = 202;

export type InteractionReportV1Wire = {
  version?: number;
  identity?: {
    name?: string;
    business_moment?: string;
    reporting_period?: { start?: string; end?: string };
  };
  verdict?: {
    rating?: string;
    summary?: string;
    primary_kpi?: { metric?: string; display?: string; value?: number };
    secondary_kpi?: { metric?: string; display?: string; value?: number };
  };
};

type InteractionReportProps = {
  entityKey: string | null;
  date: string | null | undefined;
  projectId?: string;
};

function extractReportPayload(data: unknown): InteractionReportV1Wire | null {
  if (data == null || typeof data !== "object") return null;
  const root = data as Record<string, unknown>;
  const report = root.report;
  if (report != null && typeof report === "object") {
    return report as InteractionReportV1Wire;
  }
  if ("identity" in root && "verdict" in root) {
    return root as InteractionReportV1Wire;
  }
  return null;
}

export function InteractionReport({
  entityKey,
  date,
  projectId,
}: InteractionReportProps) {
  const [report, setReport] = useState<InteractionReportV1Wire | null>(null);
  const [cachedAt, setCachedAt] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pollJob = useCallback(
    async (jobId: string) => {
      const headers: Record<string, string> = {};
      if (projectId?.trim()) headers["X-Project-ID"] = projectId.trim();
      for (let attempt = 0; attempt < 120; attempt++) {
        const jobRes = await makeRequest<RcaJobResponse>({
          url: `${getApiBaseUrl()}${GET_RCA_JOB_ROUTE.apiPath(jobId)}`,
          init: { method: GET_RCA_JOB_ROUTE.method, headers },
          unwrapped: true,
        });
        const job = unwrapRcaJobApiBody(jobRes).data;
        const status = normalizeRcaJobStatus(job?.status);
        if (status === "COMPLETED") {
          const parsed = extractReportPayload(job?.report ?? job);
          if (parsed) {
            setReport(parsed);
            setCachedAt(
              typeof (job as { cachedAt?: string })?.cachedAt === "string"
                ? ((job as { cachedAt?: string }).cachedAt ?? null)
                : null,
            );
          }
          return;
        }
        if (status === "FAILED") {
          throw new Error(job?.errorMessage ?? "Report generation failed");
        }
        await new Promise((r) => setTimeout(r, 2000));
      }
      throw new Error("Report generation timed out");
    },
    [projectId],
  );

  const generate = useCallback(
    async (regenerate = false) => {
      if (!entityKey?.trim()) return;
      setLoading(true);
      setError(null);
      try {
        const body: Record<string, string | boolean> = {
          entityKey: entityKey.trim(),
        };
        if (isValidRcaDateParam(date)) body.date = date!;
        if (regenerate) body.regenerate = true;
        const headers: Record<string, string> = {};
        if (projectId?.trim()) headers["X-Project-ID"] = projectId.trim();
        const postRes = await makeRequest<RcaReportResponse | RcaJobResponse>({
          url: `${getApiBaseUrl()}${POST_INTERACTION_REPORT_ROUTE.apiPath}`,
          init: {
            method: POST_INTERACTION_REPORT_ROUTE.method,
            headers: { "Content-Type": "application/json", ...headers },
            body: JSON.stringify(body),
          },
          unwrapped: true,
        });
        const unwrapped = unwrapRcaReportPostApiBody(postRes);
        const status = postRes.status;
        if (status === RCA_HTTP_OK) {
          const parsed = extractReportPayload(unwrapped.data);
          if (parsed) {
            setReport(parsed);
            const cached =
              unwrapped.data != null &&
              typeof unwrapped.data === "object" &&
              (unwrapped.data as { cachedAt?: string }).cachedAt;
            setCachedAt(typeof cached === "string" ? cached : null);
            return;
          }
        }
        if (status === RCA_HTTP_ACCEPTED) {
          const jobId = getJobIdFromRcaPostResponse(postRes);
          if (!jobId) throw new Error("Missing job id from server");
          await pollJob(jobId);
          return;
        }
        throw new Error("Unexpected response from interaction report API");
      } catch (e) {
        setError(e instanceof Error ? e.message : "Failed to load report");
        setReport(null);
      } finally {
        setLoading(false);
      }
    },
    [date, entityKey, pollJob, projectId],
  );

  if (!entityKey) {
    return <ErrorAndEmptyState message="Interaction name is required" />;
  }

  return (
    <Stack gap="md" className={classes.rootCauseContainer}>
      <Box className={classes.toolbar}>
        <Button
          size="xs"
          variant="light"
          leftSection={<IconRefresh size={14} />}
          onClick={() => generate(true)}
          disabled={loading}
        >
          {report ? "Regenerate" : "Generate report"}
        </Button>
        {!report && !loading && (
          <Button size="xs" onClick={() => generate(false)} disabled={loading}>
            Generate
          </Button>
        )}
      </Box>
      {cachedAt && (
        <Text size="xs" c="dimmed">
          Cached report · {cachedAt}
        </Text>
      )}
      {loading && (
        <LoaderWithMessage message="Generating interaction health report…" />
      )}
      {error && <Alert color="red">{error}</Alert>}
      {report && !loading && (
        <Stack gap="sm">
          <Title order={5}>{report.identity?.name ?? entityKey}</Title>
          {report.identity?.business_moment && (
            <Text size="sm">{report.identity.business_moment}</Text>
          )}
          {report.verdict && (
            <Alert
              color={
                report.verdict.rating === "green"
                  ? "green"
                  : report.verdict.rating === "red"
                    ? "red"
                    : "yellow"
              }
            >
              <Text fw={600}>
                {report.verdict.rating?.toUpperCase()} ·{" "}
                {report.verdict.primary_kpi?.metric}{" "}
                {report.verdict.primary_kpi?.display ??
                  report.verdict.primary_kpi?.value}
              </Text>
              <Text size="sm" mt={4}>
                {report.verdict.summary}
              </Text>
            </Alert>
          )}
          <Text size="xs" c="dimmed">
            L1 stub view — full eight-block renderer in issue 06.
          </Text>
        </Stack>
      )}
    </Stack>
  );
}
