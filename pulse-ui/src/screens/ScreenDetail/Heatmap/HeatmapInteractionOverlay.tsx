import { Stack, Text, Tooltip } from "@mantine/core";
import type { HeatmapInteractionElementRegion } from "./heatmap.types";
import { normalizeInteractionRegions, regionAverageScore } from "./heatmapInteractionUtils";
import classes from "./HeatmapPanel.module.css";

export interface HeatmapInteractionOverlayProps {
  regions: HeatmapInteractionElementRegion[];
}

function TooltipBody({ region }: { region: HeatmapInteractionElementRegion }) {
  const avg = regionAverageScore(region);
  const rows = region.interaction_scores;

  return (
    <Stack gap={6} className={classes.interactionTooltipStack}>
      <Text size="xs" fw={700} c="white">
        Avg score: {avg.toFixed(2)}
        {region.element_id ? ` · ${region.element_id}` : ""}
      </Text>
      {rows.length === 0 ? (
        <Text size="xs" c="dimmed">
          No interaction rows
        </Text>
      ) : (
        rows.map((row, i) => (
          <div key={row.interaction_id ?? `${i}`} className={classes.interactionTooltipRow}>
            <Text size="xs" c="white" lineClamp={2}>
              {row.name ?? row.interaction_id ?? `Interaction ${i + 1}`}
            </Text>
            <Text size="xs" fw={600} c="teal.2" style={{ whiteSpace: "nowrap" }}>
              {row.score.toFixed(2)}
            </Text>
          </div>
        ))
      )}
    </Stack>
  );
}

export function HeatmapInteractionOverlay({
  regions,
}: HeatmapInteractionOverlayProps) {
  const norm = normalizeInteractionRegions(regions);

  if (!norm.length) {
    return (
      <div className={classes.heatInteractionOverlay}>
        <div className={classes.interactionEmptyState}>
          <Text size="xs" c="dimmed" ta="center">
            No interaction regions in this response. Key actions need{" "}
            <code className={classes.interactionEmptyCode}>layers.interaction_map</code> from
            the API.
          </Text>
        </div>
      </div>
    );
  }

  return (
    <div className={classes.heatInteractionOverlay}>
      {norm.map((r, i) => {
        const left = Math.min(r.minX, r.maxX) * 100;
        const top = Math.min(r.minY, r.maxY) * 100;
        const w = Math.abs(r.maxX - r.minX) * 100;
        const h = Math.abs(r.maxY - r.minY) * 100;
        const key = r.element_id ?? `el-${i}-${left}-${top}`;

        return (
          <Tooltip
            key={key}
            label={<TooltipBody region={r} />}
            position="top"
            withArrow
            openDelay={150}
            maw={280}
          >
            <button
              type="button"
              className={classes.interactionRegion}
              style={{
                left: `${left}%`,
                top: `${top}%`,
                width: `${w}%`,
                height: `${h}%`,
              }}
              aria-label={
                r.element_id
                  ? `Pulse interactions for ${r.element_id}`
                  : `Pulse interactions region ${i + 1}`
              }
            />
          </Tooltip>
        );
      })}
    </div>
  );
}
