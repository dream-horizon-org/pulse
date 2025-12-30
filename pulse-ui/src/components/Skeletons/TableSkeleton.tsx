import { Box } from "@mantine/core";
import { SkeletonLoader } from "./SkeletonLoader";
import classes from "./Skeletons.module.css";

export interface TableSkeletonProps {
  /** Number of columns */
  columns?: number;
  /** Number of rows */
  rows?: number;
  /** Show table header skeleton */
  showHeader?: boolean;
  /** Additional CSS class */
  className?: string;
}

/**
 * Skeleton for table loading states.
 * Matches common table layouts with header and data rows.
 */
export function TableSkeleton({
  columns = 5,
  rows = 5,
  showHeader = true,
  className,
}: TableSkeletonProps) {
  return (
    <Box className={`${classes.tableWrapper} ${className || ""}`}>
      {showHeader && (
        <div className={classes.tableHeader}>
          {Array.from({ length: columns }).map((_, index) => (
            <div key={index} className={classes.tableHeaderCell}>
              <SkeletonLoader height={12} width="70%" radius="sm" />
            </div>
          ))}
        </div>
      )}
      
      <div className={classes.tableBody}>
        {Array.from({ length: rows }).map((_, rowIndex) => (
          <div key={rowIndex} className={classes.tableRow}>
            {Array.from({ length: columns }).map((_, colIndex) => (
              <div key={colIndex} className={classes.tableCell}>
                <SkeletonLoader 
                  height={14} 
                  width={colIndex === 0 ? "80%" : "50%"} 
                  radius="sm" 
                />
              </div>
            ))}
          </div>
        ))}
      </div>
    </Box>
  );
}

