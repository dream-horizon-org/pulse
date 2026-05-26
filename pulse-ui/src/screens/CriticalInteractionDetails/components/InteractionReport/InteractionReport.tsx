import { Alert, Badge, Box, Button, Stack, Text } from "@mantine/core";
import { IconRefresh } from "@tabler/icons-react";
import { LoaderWithMessage } from "../../../../components/LoaderWithMessage";
import { ErrorAndEmptyState } from "../../../../components/ErrorAndEmptyState";
import { useInteractionReport } from "../../../../hooks/useInteractionReport";
import { InteractionReportView } from "./InteractionReportView";
import classes from "./InteractionReport.module.css";

type InteractionReportProps = {
  entityKey: string | null;
  date: string | null | undefined;
  projectId?: string;
};

function formatCachedAt(iso: string | null): string {
  if (!iso) return "";
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

export function InteractionReport({
  entityKey,
  date,
  projectId,
}: InteractionReportProps) {
  const { report, cached, cachedAt, loading, error, generate } =
    useInteractionReport({ entityKey, date, projectId });

  if (!entityKey) {
    return <ErrorAndEmptyState message="Interaction name is required" />;
  }

  return (
    <Stack gap="md" className={classes.container}>
      <Box className={classes.toolbar}>
        <Button
          size="xs"
          variant="light"
          leftSection={<IconRefresh size={14} />}
          onClick={() => generate(Boolean(report))}
          disabled={loading}
          loading={loading}
        >
          {report ? "Regenerate" : "Generate report"}
        </Button>
      </Box>

      {cached && (
        <Badge
          size="sm"
          variant="light"
          color="gray"
          className={classes.cachedBadge}
        >
          Cached report
          {cachedAt ? ` · ${formatCachedAt(cachedAt)}` : ""}
        </Badge>
      )}

      {loading && !report && (
        <LoaderWithMessage loadingMessage="Generating interaction health report…" />
      )}
      {error && <Alert color="red">{error}</Alert>}
      {report && !loading && <InteractionReportView report={report} />}
      {!report && !loading && !error && (
        <Text size="sm" c="dimmed">
          Generate a health report for this interaction and reporting period.
        </Text>
      )}
    </Stack>
  );
}
