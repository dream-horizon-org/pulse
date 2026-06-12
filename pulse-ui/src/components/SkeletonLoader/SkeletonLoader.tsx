import { Box } from "@mantine/core";
import classes from "./SkeletonLoader.module.css";

interface SkeletonLoaderProps {
  height?: string | number;
  width?: string | number;
  radius?: string;
  className?: string;
}

export function SkeletonLoader({
  height = "1rem",
  width = "100%",
  radius = "md",
  className,
}: SkeletonLoaderProps) {
  return (
    <Box
      className={`${classes.skeleton} ${className || ""}`}
      style={{
        height,
        width,
        borderRadius: `var(--mantine-radius-${radius})`,
      }}
    />
  );
}
