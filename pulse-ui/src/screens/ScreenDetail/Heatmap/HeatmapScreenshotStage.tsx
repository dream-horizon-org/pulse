import type { ReactNode } from "react";
import { ActionIcon, Text } from "@mantine/core";
import { IconChevronLeft, IconChevronRight } from "@tabler/icons-react";
import classes from "./HeatmapPanel.module.css";

export type HeatmapStageLegendMode = "heatmap" | "interaction";

export interface HeatmapScreenshotStageProps {
  count: number;
  index: number;
  onPrev: () => void;
  onNext: () => void;
  /** Phone frame + overlays. */
  frame: ReactNode;
  /** Meta row on the right: density scale vs interaction-map hint. */
  legendMode?: HeatmapStageLegendMode;
}

/**
 * Screenshot meta row (count ∥ legend), then prev/frame/next rail.
 */
export function HeatmapScreenshotStage({
  count,
  index,
  onPrev,
  onNext,
  frame,
  legendMode = "heatmap",
}: HeatmapScreenshotStageProps) {
  const showNav = count > 1;
  const safeIndex = count > 0 ? Math.min(index, count - 1) : 0;

  return (
    <div className={classes.heatScreenshotStage}>
      <div className={classes.heatShotMetaRow}>
        <div className={classes.heatShotMetaCount}>
          {showNav && (
            <Text size="xs" c="dimmed" fw={500} component="span">
              Screenshot {safeIndex + 1} of {count}
            </Text>
          )}
        </div>
        <div className={classes.heatShotMetaLegend}>
          {legendMode === "heatmap" ? (
            <>
              <div className={classes.gradientLegend}>
                <span className={classes.gradientCaption}>Cooler</span>
                <div className={classes.gradientStrip} />
                <span className={classes.gradientCaption}>Hotter</span>
              </div>
              <span className={classes.heatMetaSep} aria-hidden>
                ·
              </span>
              <span className={classes.heatIntensityInlineNote}>
                Normalized coordinates; screenshot is alignment only.
              </span>
            </>
          ) : (
            <span className={classes.heatIntensityInlineNote}>
              Bounding boxes: Pulse interactions on this screen · hover a region for scores
            </span>
          )}
        </div>
      </div>

      <div className={classes.heatFrameRail}>
        {showNav ? (
          <ActionIcon
            variant="default"
            size="lg"
            radius="md"
            onClick={onPrev}
            aria-label="Previous screenshot"
            className={classes.heatShotNavBtn}
          >
            <IconChevronLeft size={20} stroke={1.5} />
          </ActionIcon>
        ) : (
          <span className={classes.heatShotNavSpacer} aria-hidden />
        )}

        <div className={classes.heatFrameCenter}>{frame}</div>

        {showNav ? (
          <ActionIcon
            variant="default"
            size="lg"
            radius="md"
            onClick={onNext}
            aria-label="Next screenshot"
            className={classes.heatShotNavBtn}
          >
            <IconChevronRight size={20} stroke={1.5} />
          </ActionIcon>
        ) : (
          <span className={classes.heatShotNavSpacer} aria-hidden />
        )}
      </div>
    </div>
  );
}
