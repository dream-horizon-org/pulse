import type { ReactNode } from "react";
import { ActionIcon, Text } from "@mantine/core";
import { IconChevronLeft, IconChevronRight } from "@tabler/icons-react";
import classes from "./HeatmapPanel.module.css";

export type HeatmapDensityGradientVariant = "brand" | "thermal";

export interface HeatmapScreenshotStageProps {
  count: number;
  onPrev: () => void;
  onNext: () => void;
  /** Phone frame + overlays. */
  frame: ReactNode;
  /** Density strip matches tap (thermal) vs other signals (brand). */
  densityGradientVariant?: HeatmapDensityGradientVariant;
}

function IntensityLegendStrip({
  densityGradientVariant,
}: {
  densityGradientVariant: HeatmapDensityGradientVariant;
}) {
  return (
    <div
      className={classes.heatShotLegendBelow}
      role="img"
      aria-label="Intensity scale from lower to higher interaction density"
    >
      <div className={classes.gradientLegend}>
        <Text size="xs" c="dimmed" fw={600} component="span" className={classes.gradientCaption}>
          Less intensity
        </Text>
        <div
          className={
            densityGradientVariant === "thermal"
              ? classes.gradientStripThermal
              : classes.gradientStrip
          }
        />
        <Text size="xs" c="dimmed" fw={600} component="span" className={classes.gradientCaption}>
          More intensity
        </Text>
      </div>
    </div>
  );
}

/**
 * Prev / frame / next rail, then intensity legend centered below the screenshot.
 */
export function HeatmapScreenshotStage({
  count,
  onPrev,
  onNext,
  frame,
  densityGradientVariant = "brand",
}: HeatmapScreenshotStageProps) {
  const showNav = count > 1;

  return (
    <div className={classes.heatScreenshotStage}>
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

      <IntensityLegendStrip densityGradientVariant={densityGradientVariant} />
    </div>
  );
}
