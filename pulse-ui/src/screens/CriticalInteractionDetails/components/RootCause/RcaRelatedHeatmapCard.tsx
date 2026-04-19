import { Button, Card, Group, Text } from "@mantine/core";
import dayjs from "dayjs";
import relativeTime from "dayjs/plugin/relativeTime";
import utc from "dayjs/plugin/utc";
import { Link } from "react-router-dom";
import type { RcaHeatmapFiltersWireV1 } from "../../../../hooks/useGetRcaReport/useGetRcaReport.interface";
import {
  buildRcaHeatmapEvidenceHref,
  resolveHeatmapEvidenceUtcRange,
} from "./buildRcaHeatmapEvidenceHref";
import classes from "./RcaRelatedHeatmapCard.module.css";

dayjs.extend(utc);
dayjs.extend(relativeTime);

const HEATMAP_EVIDENCE_DESCRIPTION =
  "Tap and gesture density on this screen for the dashboard time range.";

function relativeLabelFromResolvedEnd(
  filters: RcaHeatmapFiltersWireV1 | null | undefined,
): string | null {
  const { end } = resolveHeatmapEvidenceUtcRange(filters);
  const parsed = dayjs.utc(end, "YYYY-MM-DD HH:mm:ss");
  if (!parsed.isValid()) return null;
  return parsed.fromNow();
}

export interface RcaRelatedHeatmapCardProps {
  projectId: string;
  screenName: string;
  segmentTitle: string;
  heatmapFilters: RcaHeatmapFiltersWireV1 | null | undefined;
}

export function RcaRelatedHeatmapCard({
  projectId,
  screenName,
  segmentTitle,
  heatmapFilters,
}: RcaRelatedHeatmapCardProps) {
  const href = buildRcaHeatmapEvidenceHref(
    projectId,
    screenName,
    heatmapFilters,
  );
  const timeLabel = relativeLabelFromResolvedEnd(heatmapFilters);
  const subtitle = segmentTitle.trim() || screenName;

  return (
    <Card
      className={classes.heatmapEvidenceCard}
      withBorder
      padding="md"
      radius="md"
    >
      <Group
        justify="space-between"
        align="flex-start"
        wrap="nowrap"
        gap="xs"
        mb="xs"
      >
        <Text className={classes.typeLabel} tt="uppercase" size="xs" fw={700}>
          Heatmap
        </Text>
        {timeLabel ? (
          <Text size="xs" c="dimmed" ta="right" className={classes.timeLabel}>
            {timeLabel}
          </Text>
        ) : null}
      </Group>

      <Text
        className={classes.cardTitle}
        fw={700}
        size="sm"
        mb={6}
        lineClamp={2}
      >
        {screenName}
      </Text>

      <Text size="xs" c="dimmed" mb="sm" lineClamp={2}>
        {subtitle}
      </Text>

      <Text
        size="xs"
        c="gray.6"
        lh={1.55}
        mb="md"
        className={classes.description}
      >
        {HEATMAP_EVIDENCE_DESCRIPTION}
      </Text>

      <Button
        component={Link}
        to={href}
        target="_blank"
        rel="noopener noreferrer"
        fullWidth
        size="sm"
        className={classes.viewDetailButton}
        variant="filled"
      >
        View Detail
      </Button>
    </Card>
  );
}
