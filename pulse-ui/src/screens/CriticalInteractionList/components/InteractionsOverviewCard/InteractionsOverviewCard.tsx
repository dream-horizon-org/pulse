import { useEffect } from "react";
import {
  Anchor,
  Button,
  Card,
  Group,
  Skeleton,
  Stack,
  Text,
} from "@mantine/core";
import { IconRefresh, IconSparkles } from "@tabler/icons-react";
import dayjs from "dayjs";
import { useProjectContext } from "../../../../contexts";
import { useGetInteractionsOverview } from "../../../../hooks/useGetInteractionsOverview/useGetInteractionsOverview";
import classes from "./InteractionsOverviewCard.module.css";

// Labels and colors match InteractionCard.tsx getHealthColor/getHealthStatus exactly
const SEVERITY_COLORS: Record<string, string> = {
  POOR: "#ef4444",
  FAIR: "#f97316",
  GOOD: "#f59e0b",
  EXCELLENT: "#10b981",
};

function renderSummaryWithLinks(
  summary: string,
  projectId: string,
  interactionNames: string[]
): React.ReactNode[] {
  const severityPattern = `\\b(?:${Object.keys(SEVERITY_COLORS).join("|")})\\b`;

  // Sort longest first to avoid partial matches on interaction names
  const sorted = [...interactionNames].sort((a, b) => b.length - a.length);
  const escaped = sorted.map((n) => n.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));

  // Always match severity labels; add interaction names only when available
  const pattern =
    escaped.length > 0
      ? `(${severityPattern}|${escaped.join("|")})`
      : `(${severityPattern})`;
  const combined = new RegExp(pattern, "g");

  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = combined.exec(summary)) !== null) {
    if (match.index > lastIndex) {
      parts.push(summary.slice(lastIndex, match.index));
    }

    const token = match[0];

    if (token in SEVERITY_COLORS) {
      parts.push(
        <span
          key={`sev-${match.index}`}
          className={classes.severityBadge}
          style={{
            backgroundColor: `${SEVERITY_COLORS[token]}20`,
            color: SEVERITY_COLORS[token],
          }}
        >
          {token}
        </span>
      );
    } else {
      const href = `/projects/${projectId}/interaction-details/${encodeURIComponent(token)}`;
      parts.push(
        <Anchor
          key={`link-${match.index}`}
          href={href}
          fw={700}
          className={classes.interactionLink}
        >
          {token}
        </Anchor>
      );
    }

    lastIndex = match.index + token.length;
  }

  if (lastIndex < summary.length) {
    parts.push(summary.slice(lastIndex));
  }

  return parts;
}

interface Props {
  interactionNames?: string[];
}

export function InteractionsOverviewCard({ interactionNames = [] }: Props) {
  const { projectId } = useProjectContext();
  const { mutate, isPending, isSuccess, isError, data } =
    useGetInteractionsOverview();

  useEffect(() => {
    mutate({});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (isPending) {
    return (
      <Card padding="lg" radius="md" withBorder className={classes.card}>
        <Stack gap="sm">
          <Skeleton height={14} width="40%" radius="sm" />
          <Skeleton height={12} width="100%" radius="sm" />
          <Skeleton height={12} width="90%" radius="sm" />
          <Skeleton height={12} width="80%" radius="sm" />
        </Stack>
      </Card>
    );
  }

  if (isError) {
    return (
      <Card padding="lg" radius="md" withBorder className={classes.card}>
        <Text size="sm" c="dimmed">
          Unable to load overview. Try refreshing the page.
        </Text>
      </Card>
    );
  }

  if (isSuccess && data?.data) {
    const { summary, cachedAt } = data.data;
    const cachedAtFormatted =
      cachedAt ? dayjs(cachedAt).format("MMM D, HH:mm") : null;

    return (
      <Card padding="lg" radius="md" withBorder className={classes.card}>
        <Group justify="space-between" align="center" wrap="nowrap" mb="xs">
          <div className={classes.summaryTitleRow}>
            <IconSparkles size={18} color="var(--mantine-color-violet-6)" />
            <Text fw={700} size="sm" c="violet.7">
              AI Insights
            </Text>
          </div>
          <Group gap="sm" wrap="nowrap" align="center">
            {cachedAtFormatted ? (
              <Text size="xs" c="dimmed">
                Report as of {cachedAtFormatted}
              </Text>
            ) : null}
            <Button
              variant="light"
              size="xs"
              leftSection={<IconRefresh size={14} />}
              onClick={() => mutate({ regenerate: true })}
              disabled={isPending}
              aria-label="Regenerate insights"
            >
              Regenerate insights
            </Button>
          </Group>
        </Group>

        <Text size="sm" lh={1.65}>
          {renderSummaryWithLinks(summary, projectId ?? "", interactionNames)}
        </Text>
      </Card>
    );
  }

  return null;
}
