import { Box, Stack, Text, Tooltip } from "@mantine/core";
import { useCallback, useMemo, useRef, useState } from "react";
import type { HeatmapGlowPoint } from "./heatmap.types";
import classes from "./HeatmapPanel.module.css";

const GRID = 14;
const PICK_R_NORM = 0.07;

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
}

/**
 * Hit-test nearest glow bin in normalized space (same layout as canvas inset) and
 * show a tooltip — one layer, grid-accelerated for large payloads.
 */
export function HeatmapGlowBinHoverLayer({ points }: HeatmapGlowBinHoverLayerProps) {
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

  return (
    <div
      ref={wrapRef}
      className={classes.heatGlowHitLayer}
      onMouseMove={onMove}
      onMouseLeave={() => setTip(null)}
      aria-hidden
    >
      {tip && (
        <Tooltip
          position="right"
          withArrow
          openDelay={80}
          withinPortal
          label={
            <Stack gap={4} className={classes.glowBinTooltipStack}>
              <Text size="xs" fw={700} c="white">
                Density bin
              </Text>
              <Text size="xs" c="gray.2">
                Weight (aggregated taps / events in bin)
              </Text>
              <Text size="sm" fw={600} c="teal.2">
                {tip.point.weight.toLocaleString(undefined, { maximumFractionDigits: 2 })}
              </Text>
              <Text size="xs" c="dimmed">
                Norm. center ({tip.point.x.toFixed(3)}, {tip.point.y.toFixed(3)})
              </Text>
            </Stack>
          }
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
