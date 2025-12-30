import { Box } from "@mantine/core";
import classes from "./Skeletons.module.css";

export interface SkeletonLoaderProps {
  height?: string | number;
  width?: string | number;
  radius?: "xs" | "sm" | "md" | "lg" | "xl";
  className?: string;
  animate?: boolean;
}

/**
 * Base skeleton loader component with shimmer animation.
 * Use this as a building block for more complex skeleton layouts.
 */
export function SkeletonLoader({
  height = "1rem",
  width = "100%",
  radius = "md",
  className,
  animate = true,
}: SkeletonLoaderProps) {
  return (
    <Box
      className={`${classes.skeleton} ${animate ? classes.animated : ""} ${className || ""}`}
      style={{
        height,
        width,
        borderRadius: `var(--mantine-radius-${radius})`,
      }}
    />
  );
}

