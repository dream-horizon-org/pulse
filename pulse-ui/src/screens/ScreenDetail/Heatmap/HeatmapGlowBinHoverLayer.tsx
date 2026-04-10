import { Box, Group, Stack, Text, Tooltip } from "@mantine/core";
import { useCallback, useMemo, useRef, useState } from "react";
import type { HeatmapDataResponse, HeatmapGlowPoint } from "./heatmap.types";
import { buildGlowBinTooltipModel } from "./heatmapGlowBinTooltip";
import type { HeatmapSignal } from "./heatmapPanelUtils";
import classes from "./HeatmapPanel.module.css";

const GRID = 14;
/** Normalized pick radius — keep in line with heatmap.js kernel in HeatmapJsCanvas. */
const PICK_R_NORM = 0.08;

type Bucket = HeatmapGlowPoint[];

function buildBuckets(points: HeatmapGlowPoint[]): Bucket[][] {
  const emptyRow = () => Array.from({ length: GRID }, () => [] as HeatmapGlowPoint[]);
  const buckets: Bucket[][] = Array.from({ length: GRID }, emptyRow);
  for (const p of points) {
    const gx = Math.min(GRID - 1, Math.max(0, Math.floor(p.x * GRID)));
    const gy = Math.min(GRID - 1, Math.max(0, Math.floor(p.y * GRID)));
    buckets[gy][gx].push(p);
  }
  return buckets;
}

export interface HeatmapGlowBinHoverLayerProps {
  points: HeatmapGlowPoint[];
  /** When set: total clicks at spot, active layer count, activity zone vs layer max. */
  binTooltip?: {
    payload: HeatmapDataResponse;
    signal: HeatmapSignal;
  };
}

/** Bin weights are event-style counts in the API; show whole numbers (mocks used to emit one decimal). */
function formatBinTooltipCount(n: number): string {
  if (!Number.isFinite(n)) return "—";
  return Math.round(n).toLocaleString();
}

/**
 * Hit-test nearest glow bin in normalized space (same layout as canvas inset) and
 * show a tooltip — one layer, grid-accelerated for large payloads.
 */
export function HeatmapGlowBinHoverLayer({
  points,
  binTooltip,
}: HeatmapGlowBinHoverLayerProps) {
  const wrapRef = useRef<HTMLDivElement | null>(null);
  const buckets = useMemo(() => buildBuckets(points), [points]);
  const [tip, setTip] = useState<{
    px: number;
    py: number;
    point: HeatmapGlowPoint;
  } | null>(null);

  const pick = useCallback(
    (nx: number, ny: number): HeatmapGlowPoint | null => {
      const gx = Math.min(GRID - 1, Math.max(0, Math.floor(nx * GRID)));
      const gy = Math.min(GRID - 1, Math.max(0, Math.floor(ny * GRID)));
      let best: HeatmapGlowPoint | null = null;
      let bestD = PICK_R_NORM * PICK_R_NORM;
      for (let dy = -1; dy <= 1; dy++) {
        for (let dx = -1; dx <= 1; dx++) {
          const cx = gx + dx;
          const cy = gy + dy;
          if (cx < 0 || cy < 0 || cx >= GRID || cy >= GRID) continue;
          for (const p of buckets[cy][cx]) {
            const d = (p.x - nx) ** 2 + (p.y - ny) ** 2;
            if (d < bestD) {
              bestD = d;
              best = p;
            }
          }
        }
      }
      return best;
    },
    [buckets],
  );

  const onMove = useCallback(
    (e: React.MouseEvent<HTMLDivElement>) => {
      const el = wrapRef.current;
      if (!el || !points.length) {
        setTip(null);
        return;
      }
      const r = el.getBoundingClientRect();
      const nx = (e.clientX - r.left) / Math.max(1, r.width);
      const ny = (e.clientY - r.top) / Math.max(1, r.height);
      const hit = pick(nx, ny);
      if (!hit) {
        setTip(null);
        return;
      }
      setTip({
        px: e.clientX - r.left,
        py: e.clientY - r.top,
        point: hit,
      });
    },
    [pick, points.length],
  );

  if (!points.length) {
    return null;
  }

  const tooltipModel =
    tip && binTooltip
      ? buildGlowBinTooltipModel(binTooltip.payload, binTooltip.signal, tip.point)
      : null;

  const tooltipLabel = tooltipModel ? (
    <Stack gap="sm" className={classes.glowBinTooltipStack}>
      <Group justify="space-between" gap="xl" wrap="nowrap" align="flex-start">
        <Text size="xs" c="gray.3" style={{ flexShrink: 0 }}>
          Total clicks
        </Text>
        <Text size="sm" fw={600} c="white" ta="right" style={{ lineHeight: 1.3 }}>
          {formatBinTooltipCount(tooltipModel.totalClicks)}
        </Text>
      </Group>
      <Group justify="space-between" gap="xl" wrap="nowrap" align="flex-start">
        <Text size="xs" c="gray.3" style={{ flexShrink: 0 }}>
          {tooltipModel.layerLabel}
        </Text>
        <Text size="sm" fw={600} c="white" ta="right" style={{ lineHeight: 1.3 }}>
          {formatBinTooltipCount(tooltipModel.layerValue)}
        </Text>
      </Group>
      <Group justify="space-between" gap="xl" wrap="nowrap" align="flex-start">
        <Text size="xs" c="gray.3" style={{ flexShrink: 0 }}>
          Zone
        </Text>
        <Text size="sm" fw={600} c="teal.2" ta="right" style={{ lineHeight: 1.3 }}>
          {tooltipModel.zoneLabel}
        </Text>
      </Group>
    </Stack>
  ) : tip ? (
    <Stack gap="sm" className={classes.glowBinTooltipStack}>
      <Group justify="space-between" gap="xl" wrap="nowrap" align="flex-start">
        <Text size="xs" c="gray.3">
          This bin
        </Text>
        <Text size="sm" fw={600} c="white" ta="right">
          {formatBinTooltipCount(tip.point.weight)}
        </Text>
      </Group>
    </Stack>
  ) : null;

  return (
    <div
      ref={wrapRef}
      className={classes.heatGlowHitLayer}
      onMouseMove={onMove}
      onMouseLeave={() => setTip(null)}
      aria-hidden
    >
      {tip && tooltipLabel && (
        <Tooltip
          position="right"
          withArrow
          withinPortal
          opened
          events={{ hover: false, focus: false, touch: false }}
          positionDependencies={[
            tip.px,
            tip.py,
            tip.point.weight,
            tip.point.x,
            tip.point.y,
            binTooltip?.signal,
            tooltipModel?.totalClicks,
            tooltipModel?.layerValue,
            tooltipModel?.zoneLabel,
          ]}
          label={tooltipLabel}
        >
          <Box
            component="span"
            className={classes.glowBinTooltipAnchor}
            style={{ left: tip.px, top: tip.py }}
          />
        </Tooltip>
      )}
    </div>
  );
}
