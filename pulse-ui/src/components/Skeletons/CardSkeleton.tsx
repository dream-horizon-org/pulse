import { Box } from "@mantine/core";
import { SkeletonLoader } from "./SkeletonLoader";
import classes from "./Skeletons.module.css";

export interface CardSkeletonProps {
  /** Height of the card */
  height?: number | string;
  /** Width of the card */
  width?: number | string;
  /** Show header skeleton */
  showHeader?: boolean;
  /** Number of content rows */
  contentRows?: number;
  /** Additional CSS class */
  className?: string;
}

/**
 * Generic card skeleton for various card layouts.
 * Use for interaction cards, screen cards, etc.
 */
export function CardSkeleton({
  height = 180,
  width = "100%",
  showHeader = true,
  contentRows = 3,
  className,
}: CardSkeletonProps) {
  return (
    <Box 
      className={`${classes.cardSkeleton} ${className || ""}`}
      style={{ height, width }}
    >
      {showHeader && (
        <div className={classes.cardHeader}>
          <SkeletonLoader height={16} width="60%" radius="sm" />
          <SkeletonLoader height={20} width={50} radius="xl" />
        </div>
      )}
      
      <div className={classes.cardContent}>
        {Array.from({ length: contentRows }).map((_, index) => (
          <div key={index} className={classes.cardRow}>
            <SkeletonLoader height={10} width="40%" radius="sm" />
            <SkeletonLoader height={12} width="25%" radius="sm" />
          </div>
        ))}
      </div>
    </Box>
  );
}

